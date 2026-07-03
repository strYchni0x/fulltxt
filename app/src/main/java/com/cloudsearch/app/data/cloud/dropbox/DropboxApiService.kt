package me.fulltxt.app.data.cloud.dropbox

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit-Schnittstelle für die Dropbox-API v2.
 * Basis-URL: https://api.dropboxapi.com/2/
 *
 * Alle Aufrufe benötigen einen "Authorization: Bearer <token>"-Header.
 * Datei-Downloads nutzen OkHttp direkt (anderer Host: content.dropboxapi.com).
 */
interface DropboxApiService {

    /** Listet alle Dateien/Ordner. path="" für den Root, recursive=true für den gesamten Baum. */
    @POST("files/list_folder")
    suspend fun listFolder(
        @Header("Authorization") auth: String,
        @Body request: ListFolderRequest
    ): ListFolderResult

    /** Setzt eine paginierte Auflistung fort oder holt Änderungen nach einem gespeicherten Cursor. */
    @POST("files/list_folder/continue")
    suspend fun listFolderContinue(
        @Header("Authorization") auth: String,
        @Body request: ContinueRequest
    ): ListFolderResult

    /**
     * Holt einen Cursor, der den aktuellen Zustand eines Ordnerbaums repräsentiert.
     * Wird verwendet, um den Delta-Sync zu initialisieren, ohne alle Dateiinhalte abzurufen.
     */
    @POST("files/list_folder/get_latest_cursor")
    suspend fun getLatestCursor(
        @Header("Authorization") auth: String,
        @Body request: ListFolderRequest
    ): CursorResult

    /**
     * Holt einen temporären (4-Stunden-)HTTPS-Link, um eine Datei direkt herunterzuladen.
     * Der path kann eine Dropbox-Datei-ID (z. B. "id:abc123") oder ein Pfad-String sein.
     */
    @POST("files/get_temporary_link")
    suspend fun getTemporaryLink(
        @Header("Authorization") auth: String,
        @Body request: TemporaryLinkRequest
    ): TemporaryLinkResult
}
