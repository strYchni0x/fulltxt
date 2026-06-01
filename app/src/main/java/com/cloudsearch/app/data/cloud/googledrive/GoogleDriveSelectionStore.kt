package me.fulltxt.app.data.cloud.googledrive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists which Google Drive folders and files the user granted the app access to via the
 * Google Picker. Only relevant for the `playstore` flavor (drive.file scope): with drive.file
 * the app can only see files the user explicitly picked, so [GoogleDriveConnector] enumerates
 * from this selection instead of listing the whole Drive.
 *
 * Mirrors the persistence approach of LocalFolderAuthManager.
 */
@Singleton
class GoogleDriveSelectionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fulltxt_drive_selection", Context.MODE_PRIVATE)

    fun getFolders(accountId: String): Set<String> =
        prefs.getStringSet(keyFolders(accountId), emptySet()) ?: emptySet()

    fun getFiles(accountId: String): Set<String> =
        prefs.getStringSet(keyFiles(accountId), emptySet()) ?: emptySet()

    fun hasSelection(accountId: String): Boolean =
        getFolders(accountId).isNotEmpty() || getFiles(accountId).isNotEmpty()

    fun setSelection(accountId: String, folders: Set<String>, files: Set<String>) {
        prefs.edit()
            .putStringSet(keyFolders(accountId), folders)
            .putStringSet(keyFiles(accountId), files)
            .apply()
    }

    fun clear(accountId: String) {
        prefs.edit()
            .remove(keyFolders(accountId))
            .remove(keyFiles(accountId))
            .apply()
    }

    private fun keyFolders(accountId: String) = "folders_$accountId"
    private fun keyFiles(accountId: String) = "files_$accountId"
}
