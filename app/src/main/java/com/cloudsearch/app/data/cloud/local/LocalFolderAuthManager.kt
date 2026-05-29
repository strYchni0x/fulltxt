package me.fulltxt.app.data.cloud.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("fulltxt_local_folders", Context.MODE_PRIVATE)

    fun addFolder(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val uris = getFolderUriStrings().toMutableSet()
        uris.add(treeUri.toString())
        prefs.edit().putStringSet(KEY_FOLDERS, uris).apply()
    }

    fun removeFolder(accountId: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(accountId), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val uris = getFolderUriStrings().toMutableSet()
        uris.remove(accountId)
        prefs.edit().putStringSet(KEY_FOLDERS, uris).apply()
    }

    fun getFolderUri(accountId: String): Uri? =
        if (accountId in getFolderUriStrings()) Uri.parse(accountId) else null

    fun getFolderName(treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: treeUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: treeUri.toString()

    fun isPermissionGranted(accountId: String): Boolean {
        val uri = Uri.parse(accountId)
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun getFolderUriStrings(): Set<String> =
        prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()

    companion object {
        private const val KEY_FOLDERS = "local_folders"
    }
}
