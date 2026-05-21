package me.fulltxt.app.data.cloud.onedrive

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Url

interface GraphApiService {

    @GET
    suspend fun getPage(
        @Url url: String,
        @Header("Authorization") bearer: String
    ): DriveItemPage

    @GET("me/drive/items/{id}/content")
    suspend fun downloadFile(
        @Header("Authorization") bearer: String,
        @Path("id") fileId: String
    ): ResponseBody
}
