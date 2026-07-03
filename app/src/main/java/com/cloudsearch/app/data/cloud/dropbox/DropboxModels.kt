package me.fulltxt.app.data.cloud.dropbox

import com.google.gson.annotations.SerializedName

// ── Request-Bodys ─────────────────────────────────────────────────────────────

data class ListFolderRequest(
    @SerializedName("path")             val path: String = "",
    @SerializedName("recursive")        val recursive: Boolean = true,
    @SerializedName("include_deleted")  val includeDeleted: Boolean = false,
    @SerializedName("include_media_info") val includeMediaInfo: Boolean = false
)

data class ContinueRequest(
    @SerializedName("cursor") val cursor: String
)

// ── Response-Bodys ────────────────────────────────────────────────────────────

data class ListFolderResult(
    @SerializedName("entries")  val entries: List<DropboxEntry>,
    @SerializedName("cursor")   val cursor: String,
    @SerializedName("has_more") val hasMore: Boolean
)

/**
 * Ein einzelner von /files/list_folder zurückgegebener Eintrag.
 * [tag] ist einer von "file", "folder", "deleted".
 */
data class DropboxEntry(
    @SerializedName(".tag")          val tag: String,
    @SerializedName("name")          val name: String? = null,
    @SerializedName("path_display")  val pathDisplay: String? = null,
    @SerializedName("id")            val id: String? = null,
    @SerializedName("size")          val size: Long? = null,
    @SerializedName("server_modified") val serverModified: String? = null,
    @SerializedName("client_modified") val clientModified: String? = null,
    @SerializedName("rev")           val rev: String? = null,
    @SerializedName("is_downloadable") val isDownloadable: Boolean? = true,
    @SerializedName("content_hash")  val contentHash: String? = null
)

data class CursorResult(
    @SerializedName("cursor") val cursor: String
)

data class DropboxAccount(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("name")       val name: DropboxName?,
    @SerializedName("email")      val email: String?
)

data class DropboxName(
    @SerializedName("display_name") val displayName: String
)

/** Token-Antwort von /oauth2/token */
data class DropboxTokenResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("token_type")    val tokenType: String,
    @SerializedName("expires_in")    val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("account_id")    val accountId: String?,
    @SerializedName("uid")           val uid: String?
)

/** Pro Konto in EncryptedSharedPreferences gespeichert */
data class DropboxAccountInfo(
    val accountId:   String,
    val displayName: String,
    val email:       String
)

data class TemporaryLinkRequest(
    @SerializedName("path") val path: String
)

data class TemporaryLinkResult(
    @SerializedName("link")     val link: String,
    @SerializedName("metadata") val metadata: DropboxEntry? = null
)
