package me.fulltxt.app.data.cloud.googledrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.R
import javax.inject.Inject
import javax.inject.Singleton

/** Folder and file IDs the user selected in the Google Picker. */
data class DrivePickerResult(val folders: Set<String>, val files: Set<String>)

/**
 * Drives the Google Picker flow for the `playstore` flavor (drive.file scope). The picker itself
 * is a JavaScript API with no native Android SDK, so it is hosted at [R.string.picker_base_url]
 * (fulltxt.me). We open it in the browser with the user's OAuth token and receive the selected
 * folder/file IDs back via the deep link `fulltxt://drive-picker` (handled by
 * [DrivePickerCallbackActivity]).
 *
 * Mirrors the static-channel + CompletableDeferred pattern used by DropboxAuthManager.
 */
@Singleton
class GoogleDrivePickerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager
) {
    companion object {
        @Volatile private var pendingCallback: CompletableDeferred<DrivePickerResult?>? = null

        /** Called by [DrivePickerCallbackActivity] when the picker deep link arrives. */
        @JvmStatic
        fun deliverResult(result: DrivePickerResult?) {
            val d = pendingCallback ?: run {
                Log.e("DrivePicker", "deliverResult: no pending callback – ignoring")
                return
            }
            pendingCallback = null
            d.complete(result)
        }
    }

    /**
     * Opens the hosted picker for [accountId] (the signed-in account's email) and suspends until
     * the user finishes. Returns the selection, or null if the user cancelled.
     */
    suspend fun pickSelection(accountId: String): DrivePickerResult? {
        val token = withContext(Dispatchers.IO) {
            // GoogleAccountCredential.getToken() returns an OAuth2 access token for the granted
            // (drive.file) scope; the picker JS uses it via setOAuthToken().
            authManager.getCredential(accountId).token
        }

        val url = Uri.parse(context.getString(R.string.picker_base_url)).buildUpon()
            .appendQueryParameter("token", token)
            .appendQueryParameter("appId", context.getString(R.string.picker_app_id))
            .appendQueryParameter("apiKey", context.getString(R.string.picker_api_key))
            .build()

        val deferred = CompletableDeferred<DrivePickerResult?>()
        pendingCallback = deferred

        context.startActivity(
            Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        return deferred.await()
    }
}
