package me.fulltxt.app.data.cloud.dropbox

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the Dropbox API v2.
 * Base URL: https://api.dropboxapi.com/2/
 *
 * All calls require an "Authorization: Bearer <token>" header.
 * File downloads use OkHttp directly (different host: content.dropboxapi.com).
 */
interface DropboxApiService {

    /** List all files/folders. Use path="" for root, recursive=true for full tree. */
    @POST("files/list_folder")
    suspend fun listFolder(
        @Header("Authorization") auth: String,
        @Body request: ListFolderRequest
    ): ListFolderResult

    /** Continue a paginated listing or fetch changes after a stored cursor. */
    @POST("files/list_folder/continue")
    suspend fun listFolderContinue(
        @Header("Authorization") auth: String,
        @Body request: ContinueRequest
    ): ListFolderResult

    /**
     * Obtain a cursor representing the current state of a folder tree.
     * Used to bootstrap delta sync without fetching all file content.
     */
    @POST("files/list_folder/get_latest_cursor")
    suspend fun getLatestCursor(
        @Header("Authorization") auth: String,
        @Body request: ListFolderRequest
    ): CursorResult

    /**
     * Get a temporary (4-hour) HTTPS link to download a file directly.
     * The path can be a Dropbox file ID (e.g. "id:abc123") or a path string.
     */
    @POST("files/get_temporary_link")
    suspend fun getTemporaryLink(
        @Header("Authorization") auth: String,
        @Body request: TemporaryLinkRequest
    ): TemporaryLinkResult
}
