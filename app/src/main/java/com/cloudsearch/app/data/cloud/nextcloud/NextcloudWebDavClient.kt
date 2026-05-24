package me.fulltxt.app.data.cloud.nextcloud

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
    private val okHttpClient: OkHttpClient
) {

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

        // Try Depth: infinity first (supported by Nextcloud 20+)
        val infinityResponse = propfind(serverUrl, authHeader, rootPath, depth = "infinity")
        if (infinityResponse != null) {
            results += infinityResponse.filter { !it.isDirectory && it.mimeType in SUPPORTED_MIME_TYPES }
        } else {
            // Fall back to recursive Depth:1 traversal
            collectFilesRecursive(serverUrl, authHeader, rootPath, results)
        }
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
        val entries = propfind(serverUrl, authHeader, path, depth = "1") ?: return
        // Skip the first entry (the directory itself) by filtering on isDirectory + matching path
        entries
            .filter { it.href.trimEnd('/') != path.trimEnd('/') }
            .forEach { entry ->
                if (entry.isDirectory) {
                    collectFilesRecursive(serverUrl, authHeader, entry.href, results)
                } else if (entry.mimeType in SUPPORTED_MIME_TYPES) {
                    results.add(entry)
                }
            }
    }

    /** Parses a WebDAV multistatus XML body into a list of WebDavFile entries. */
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
        var depth = 0               // nesting depth so we ignore nested elements
        var inResponse = false
        var inProp = false
        var propDepth = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val ns = parser.namespace ?: ""
            val name = parser.name ?: ""

            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when {
                        ns == DAV_NS && name == "response" -> {
                            // Reset per-response state
                            href = ""; mimeType = null; sizeBytes = 0L
                            etag = null; createdAt = 0L; modifiedAt = 0L
                            isDirectory = false; inResponse = true; inProp = false
                        }
                        inResponse && ns == DAV_NS && name == "href" && !inProp -> {
                            href = readText(parser)
                        }
                        inResponse && ns == DAV_NS && name == "prop" -> {
                            inProp = true
                            propDepth = depth
                        }
                        inProp && depth == propDepth + 1 -> when {
                            ns == DAV_NS && name == "getcontenttype" ->
                                mimeType = readText(parser).ifEmpty { null }
                            ns == DAV_NS && name == "getcontentlength" ->
                                sizeBytes = readText(parser).toLongOrNull() ?: 0L
                            ns == DAV_NS && name == "getetag" ->
                                etag = readText(parser).trim('"').ifEmpty { null }
                            ns == DAV_NS && name == "creationdate" ->
                                createdAt = parseDate(readText(parser))
                            ns == DAV_NS && name == "getlastmodified" ->
                                modifiedAt = parseDate(readText(parser))
                            ns == DAV_NS && name == "collection" ->
                                isDirectory = true
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (ns == DAV_NS && name == "response" && inResponse && href.isNotEmpty()) {
                        results += WebDavFile(
                            href = href,
                            name = href.trimEnd('/').substringAfterLast('/'),
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            etag = etag,
                            createdAt = createdAt,
                            modifiedAt = modifiedAt,
                            isDirectory = isDirectory
                        )
                        inResponse = false
                        inProp = false
                    }
                    if (ns == DAV_NS && name == "prop") inProp = false
                    depth--
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
