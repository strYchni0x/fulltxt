package me.fulltxt.app.data.cloud.dropbox

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline activity that receives the Dropbox OAuth redirect
 * (fulltxt://dropbox-auth?code=…) and forwards the auth code to [DropboxAuthManager].
 *
 * Declared with launchMode="singleTop" in the manifest so re-launches reuse the
 * same instance instead of stacking a new one.
 */
class DropboxCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
    }

    private fun handleIntent() {
        val data  = intent?.data
        val code  = data?.getQueryParameter("code")
        val error = data?.getQueryParameter("error")
        Log.d("DropboxAuth", "Callback: uri=$data code=${code?.take(8)}… error=$error")

        when {
            code  != null -> DropboxAuthManager.deliverCode(code)
            error != null -> {
                Log.e("DropboxAuth", "OAuth error from Dropbox: $error")
                DropboxAuthManager.deliverCode(null)
            }
            else          -> {
                Log.e("DropboxAuth", "Callback without code or error, uri=$data")
                DropboxAuthManager.deliverCode(null)
            }
        }
        finish()
    }
}
