package me.fulltxt.app.data.cloud.dropbox

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Transparente Trampolin-Activity, die den Dropbox-OAuth-Redirect
 * (fulltxt://dropbox-auth?code=…) empfängt und den Auth-Code an [DropboxAuthManager] weiterleitet.
 *
 * Im Manifest mit launchMode="singleTop" deklariert, sodass erneute Starts dieselbe Instanz
 * wiederverwenden, statt eine neue zu stapeln.
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
