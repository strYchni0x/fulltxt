package me.fulltxt.app.data.cloud.googledrive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline activity that receives the Google Picker redirect
 * (fulltxt://drive-picker?folders=…&files=…  or  ?cancel=1) and forwards the selected
 * IDs to [GoogleDrivePickerManager]. Mirrors DropboxCallbackActivity.
 */
class DrivePickerCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
    }

    private fun handleIntent() {
        val data = intent?.data
        val cancelled = data?.getQueryParameter("cancel") != null
        Log.d("DrivePicker", "Callback: uri=$data cancelled=$cancelled")

        val result = if (cancelled || data == null) {
            null
        } else {
            DrivePickerResult(
                folders = data.getQueryParameter("folders").toIdSet(),
                files = data.getQueryParameter("files").toIdSet()
            )
        }
        GoogleDrivePickerManager.deliverResult(result)
        finish()
    }

    private fun String?.toIdSet(): Set<String> =
        this?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
