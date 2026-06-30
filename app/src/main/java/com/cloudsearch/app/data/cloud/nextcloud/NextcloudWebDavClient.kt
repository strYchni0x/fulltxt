package me.fulltxt.app.data.cloud.nextcloud

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class WebDavFile(
    /** Absolute URL path, e.g. /remote.php/dav/files/user/docs/report.pdf */
    val href: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val etag: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean
)

@Singleton
class NextcloudWebDavClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext context: Context
) {

    // Remembers per endpoint (server + root path) whether Depth: infinity is unsupported, so we
    // don't waste a slow, doomed infinity request on every sync. A server property, not per-user.
    private val infinityPrefs = context.getSharedPreferences("fulltxt_webdav", Context.MODE_PRIVATE)

    companion object {
        private const val DAV_NS = "DAV:"
        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:prop>
                <d:resourcetype/>
                <d:getcontenttype/>
                <d:getcontentlength/>
                <d:getetag/>
                <d:creationdate/>
                <d:getlastmodified/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val SUPPORTED_MIME_TYPES = setOf(
            "text/plain", "text/csv", "text/markdown",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    /**
     * Lists all supported files under the user's root using a single PROPFIND with Depth: infinity.
     * Falls back to recursive Depth:1 traversal if the server rejects infinity (HTTP 403/405).
     *
     * @param rootPath WebDAV root path for the provider. Defaults to the Nextcloud/ownCloud/
     *   MagentaCloud layout. Pass a custom value for providers with different structures
     *   (e.g. Strato HiDrive uses "/users/<username>/").
     */
    suspend fun listFiles(
        serverUrl: String,
        username: String,
        authHeader: String,
        rootPath: String = "/remote.php/dav/files/$username/"
    ): List<WebDavFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebDavFile>()
        val infinityKey = "no_infinity:${serverUrl.trimEnd('/')}$rootPath"

        // Try Depth: infinity first (a single request for the whole tree) — unless a previous sync
        // already found this server doesn't support it. Many servers reject or choke on infinity:
        // some return 403/405, others (e.g. Nextcloud hitting a PHP timeout while enumerating)
        // return 500 or time out. On ANY failure we fall back to a recursive Depth:1 traversal
        // (what official WebDAV clients use) and remember it, so later syncs skip the slow attempt.
        if (!infinityPrefs.getBoolean(infinityKey, false)) {
            val infinityResponse = try {
                propfind(serverUrl, authHeader, rootPath, depth = "infinity")
            } catch (_: Exception) {
                null
            }
            if (infinityResponse != null) {
                results += infinityResponse.filter { !it.isDirectory && it.mimeType in SUPPORTED_MIME_TYPES }
                return@withContext results
            }
            infinityPrefs.edit().putBoolean(infinityKey, true).apply()
        }

        // Recursive Depth:1 traversal (server doesn't support / rejected infinity).
        collectFilesRecursive(serverUrl, authHeader, rootPath, results)
        results
    }

    /** Downloads a file identified by its WebDAV href path. */
    suspend fun downloadFile(
        serverUrl: String,
        authHeader: String,
        href: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = if (href.startsWith("http")) href else "${serverUrl.trimEnd('/')}$href"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", authHeader)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code} for $href")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    /**
     * Performs a test PROPFIND (Depth:0) against the user root.
     * Throws if credentials are wrong or the server is unreachable.
     *
     * @param rootPath see [listFiles]
     */
    suspend fun testConnection(
        serverUrl: String,
        username: String,
        authHeader: String,
        rootPath: String = "/remote.php/dav/files/$username/"
    ) = withContext(Dispatchers.IO) {
        propfind(serverUrl, authHeader, rootPath, depth = "0")
            ?: throw Exception("Verbindungstest fehlgeschlagen")
    }

    // --- Private helpers ---

    /**
     * Issues a PROPFIND and parses the response.
     * Returns null if the server responds with 403/405 (infinity not allowed).
     * Throws for other non-2xx responses.
     */
    private fun propfind(
        serverUrl: String,
        authHeader: String,
        path: String,
        depth: String
    ): List<WebDavFile>? {
        val url = "${serverUrl.trimEnd('/')}$path"
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Authorization", authHeader)
            .header("Depth", depth)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val code = response.code
        val xml = response.body?.string()
        response.close()

        return when (code) {
            207 -> xml?.let { parseMultiStatus(it) } ?: emptyList()
            403, 405 -> null   // infinity not allowed – caller will fall back
            401 -> throw Exception("Authentifizierung fehlgeschlagen (401)")
            else -> throw Exception("WebDAV PROPFIND fehlgeschlagen: HTTP $code")
        }
    }

    private fun collectFilesRecursive(
        serverUrl: String,
        authHeader: String,
        path: String,
        results: MutableList<WebDavFile>
    ) {
        // The PROPFIND for this directory is allowed to throw (the initial root call surfaces
        // genuine auth/connection errors). Errors while descending into individual subfolders are
        // contained below so one unreadable/erroring folder doesn't abort the whole traversal.
        val entries = propfind(serverUrl, authHeader, path, depth = "1") ?: return
        // Skip the first entry (the directory itself) by filtering on isDirectory + matching path
        entries
            .filter { it.href.trimEnd('/') != path.trimEnd('/') }
            .forEach { entry ->
                if (entry.isDirectory) {
                    // Skip a subfolder that fails (HTTP 500, timeout, transient) and keep going.
                    try {
                        collectFilesRecursive(serverUrl, authHeader, entry.href, results)
                    } catch (_: Exception) {
                        // ignore this subtree
                    }
                } else if (entry.mimeType in SUPPORTED_MIME_TYPES) {
                    results.add(entry)
                }
            }
    }

    /**
     * Parses a WebDAV multistatus XML body into a list of WebDavFile entries.
     *
     * Uses boolean state flags rather than a numeric nesting depth: reading a text property via
     * [readText] (XmlPullParser.nextText) consumes that element's END_TAG, which would corrupt a
     * manual depth counter. Flags are immune to that. Directory detection relies on the nested
     * `<d:resourcetype><d:collection/>` element, so `<resourcetype>` is tracked explicitly.
     */
    private fun parseMultiStatus(xml: String): List<WebDavFile> {
        val results = mutableListOf<WebDavFile>()

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser: XmlPullParser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        // State machine per <d:response>
        var href = ""
        var mimeType: String? = null
        var sizeBytes = 0L
        var etag: String? = null
        var createdAt = 0L
        var modifiedAt = 0L
        var isDirectory = false
        var inResponse = false
        var inProp = false
        var inResourceType = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val ns = parser.namespace ?: ""
            val name = parser.name ?: ""

            when (event) {
                XmlPullParser.START_TAG -> when {
                    ns == DAV_NS && name == "response" -> {
                        // Reset per-response state
                        href = ""; mimeType = null; sizeBytes = 0L
                        etag = null; createdAt = 0L; modifiedAt = 0L
                        isDirectory = false; inResponse = true
                        inProp = false; inResourceType = false
                    }
                    inResponse && !inProp && ns == DAV_NS && name == "href" ->
                        href = readText(parser)
                    inResponse && ns == DAV_NS && name == "prop" ->
                        inProp = true
                    inProp && ns == DAV_NS && name == "resourcetype" ->
                        inResourceType = true
                    inResourceType && ns == DAV_NS && name == "collection" ->
                        isDirectory = true
                    inProp && ns == DAV_NS && name == "getcontenttype" ->
                        // Strip any "; charset=…" suffix so it matches SUPPORTED_MIME_TYPES.
                        mimeType = readText(parser).substringBefore(';').trim().ifEmpty { null }
                    inProp && ns == DAV_NS && name == "getcontentlength" ->
                        sizeBytes = readText(parser).toLongOrNull() ?: 0L
                    inProp && ns == DAV_NS && name == "getetag" ->
                        etag = readText(parser).trim('"').ifEmpty { null }
                    inProp && ns == DAV_NS && name == "creationdate" ->
                        createdAt = parseDate(readText(parser))
                    inProp && ns == DAV_NS && name == "getlastmodified" ->
                        modifiedAt = parseDate(readText(parser))
                }
                XmlPullParser.END_TAG -> when {
                    ns == DAV_NS && name == "resourcetype" -> inResourceType = false
                    ns == DAV_NS && name == "prop" -> inProp = false
                    ns == DAV_NS && name == "response" -> {
                        if (inResponse && href.isNotEmpty()) {
                            results += WebDavFile(
                                href = href,
                                // href segments are percent-encoded; decode for a human-readable
                                // display name (Uri.decode keeps '+' literal, unlike URLDecoder).
                                name = Uri.decode(href.trimEnd('/').substringAfterLast('/')),
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                etag = etag,
                                createdAt = createdAt,
                                modifiedAt = modifiedAt,
                                isDirectory = isDirectory
                            )
                        }
                        inResponse = false; inProp = false; inResourceType = false
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    /**
     * Reads the text content of the current START_TAG element.
     * Advances the parser past the element's END_TAG.
     */
    private fun readText(parser: XmlPullParser): String {
        return try {
            parser.nextText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseDate(s: String): Long {
        if (s.isBlank()) return 0L
        return runCatching {
            ZonedDateTime.parse(s).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }
}
