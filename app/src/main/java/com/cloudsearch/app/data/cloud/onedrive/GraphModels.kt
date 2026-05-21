package me.fulltxt.app.data.cloud.onedrive

import com.google.gson.annotations.SerializedName

data class DriveItemPage(
    @SerializedName("value") val items: List<GraphDriveItem> = emptyList(),
    @SerializedName("@odata.nextLink") val nextLink: String? = null,
    @SerializedName("@odata.deltaLink") val deltaLink: String? = null
)

data class GraphDriveItem(
    val id: String,
    val name: String,
    val size: Long?,
    val createdDateTime: String?,
    val lastModifiedDateTime: String?,
    val file: GraphFileInfo?,
    val parentReference: GraphParentReference?,
    val deleted: GraphDeleted?,
    val eTag: String?,
    val webUrl: String?
)

data class GraphFileInfo(val mimeType: String?)

data class GraphParentReference(val path: String?)

data class GraphDeleted(val state: String?)
