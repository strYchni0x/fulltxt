package me.fulltxt.app.domain.model

data class CloudFile(
    val fileId: String,
    val fileName: String,
    val cloudPath: String,
    val cloudProvider: CloudProvider,
    val accountId: String,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val mimeType: String,
    val changeToken: String?,
    val webUrl: String? = null
)

val CloudProvider.requiresPro: Boolean get() = this in setOf(
    CloudProvider.GOOGLE_DRIVE,
    CloudProvider.ONE_DRIVE,
    CloudProvider.DROPBOX
)

enum class CloudProvider {
    GOOGLE_DRIVE,
    ONE_DRIVE,
    NEXTCLOUD,
    OWNCLOUD,
    DROPBOX,
    MAGENTA_CLOUD,
    STRATO_HIDRIVE,
    YANDEX_DISK,
    LOCAL
}
