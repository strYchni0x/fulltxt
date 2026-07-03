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
    /** Absoluter URL-Pfad, z. B. /remote.php/dav/files/user/docs/report.pdf */
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

    // Merkt sich pro Endpunkt (Server + Root-Pfad), ob Depth: infinity nicht unterstützt wird, damit
    // wir nicht bei jedem Sync eine langsame, aussichtslose infinity-Anfrage verschwenden. Eine
    // Server-Eigenschaft, nicht pro Benutzer.
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
     * Listet alle unterstützten Dateien unter dem Benutzer-Root mit einem einzigen PROPFIND
     * (Depth: infinity). Fällt auf eine rekursive Depth:1-Traversierung zurück, wenn der Server
     * infinity ablehnt (HTTP 403/405).
     *
     * @param rootPath WebDAV-Root-Pfad des Anbieters. Standard ist das Nextcloud/ownCloud/
     *   MagentaCloud-Layout. Für Anbieter mit anderer Struktur einen eigenen Wert übergeben
     *   (z. B. Strato HiDrive nutzt "/users/<username>/").
     */
    suspend fun listFiles(
        serverUrl: String,
        username: String,
        authHeader: String,
        rootPath: String = "/remote.php/dav/files/$username/"
    ): List<WebDavFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebDavFile>()
        val infinityKey = "no_infinity:${serverUrl.trimEnd('/')}$rootPath"

        // Zuerst Depth: infinity versuchen (eine Anfrage für den ganzen Baum) — außer ein früherer
        // Sync hat bereits festgestellt, dass dieser Server es nicht unterstützt. Viele Server lehnen
        // infinity ab oder verschlucken sich daran: manche liefern 403/405, andere (z. B. Nextcloud
        // bei einem PHP-Timeout während der Auflistung) liefern 500 oder laufen in einen Timeout. Bei
        // JEDEM Fehler fallen wir auf eine rekursive Depth:1-Traversierung zurück (wie es offizielle
        // WebDAV-Clients tun) und merken uns das, damit spätere Syncs den langsamen Versuch überspringen.
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

        // Rekursive Depth:1-Traversierung (Server unterstützt infinity nicht / hat es abgelehnt).
        collectFilesRecursive(serverUrl, authHeader, rootPath, results)
        results
    }

    /** Lädt eine Datei anhand ihres WebDAV-href-Pfads herunter. */
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
     * Führt ein Test-PROPFIND (Depth:0) gegen den Benutzer-Root aus.
     * Wirft eine Exception, wenn die Zugangsdaten falsch sind oder der Server nicht erreichbar ist.
     *
     * @param rootPath siehe [listFiles]
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

    // --- Private Hilfsfunktionen ---

    /**
     * Setzt ein PROPFIND ab und parst die Antwort.
     * Gibt null zurück, wenn der Server mit 403/405 antwortet (infinity nicht erlaubt).
     * Wirft bei anderen Nicht-2xx-Antworten.
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
            403, 405 -> null   // infinity nicht erlaubt – Aufrufer greift auf den Fallback zurück
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
        // Das PROPFIND für dieses Verzeichnis darf werfen (der initiale Root-Aufruf bringt echte
        // Auth-/Verbindungsfehler an die Oberfläche). Fehler beim Absteigen in einzelne Unterordner
        // werden unten aufgefangen, damit ein nicht lesbarer/fehlerhafter Ordner nicht die ganze
        // Traversierung abbricht.
        val entries = propfind(serverUrl, authHeader, path, depth = "1") ?: return
        // Den ersten Eintrag (das Verzeichnis selbst) überspringen, indem auf isDirectory + passenden Pfad gefiltert wird
        entries
            .filter { it.href.trimEnd('/') != path.trimEnd('/') }
            .forEach { entry ->
                if (entry.isDirectory) {
                    // Einen Unterordner, der scheitert (HTTP 500, Timeout, vorübergehend), überspringen und weitermachen.
                    try {
                        collectFilesRecursive(serverUrl, authHeader, entry.href, results)
                    } catch (_: Exception) {
                        // diesen Teilbaum ignorieren
                    }
                } else if (entry.mimeType in SUPPORTED_MIME_TYPES) {
                    results.add(entry)
                }
            }
    }

    /**
     * Parst einen WebDAV-Multistatus-XML-Body in eine Liste von WebDavFile-Einträgen.
     *
     * Nutzt boolesche Zustandsflags statt eines numerischen Verschachtelungszählers: Das Lesen einer
     * Text-Property über [readText] (XmlPullParser.nextText) konsumiert das END_TAG dieses Elements,
     * was einen manuellen Tiefenzähler verfälschen würde. Flags sind dagegen immun. Die
     * Verzeichniserkennung stützt sich auf das verschachtelte `<d:resourcetype><d:collection/>`,
     * daher wird `<resourcetype>` explizit verfolgt.
     */
    private fun parseMultiStatus(xml: String): List<WebDavFile> {
        val results = mutableListOf<WebDavFile>()

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser: XmlPullParser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        // Zustandsautomat pro <d:response>
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
                        // Zustand pro Response zurücksetzen
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
                        // Ein etwaiges "; charset=…"-Suffix entfernen, damit es zu SUPPORTED_MIME_TYPES passt.
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
                                // href-Segmente sind prozentcodiert; für einen menschenlesbaren
                                // Anzeigenamen dekodieren (Uri.decode lässt '+' literal, anders als URLDecoder).
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
     * Liest den Textinhalt des aktuellen START_TAG-Elements.
     * Bewegt den Parser über das END_TAG des Elements hinaus.
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
