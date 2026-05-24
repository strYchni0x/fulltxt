package me.fulltxt.app.data.cloud.magenta

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class MagentaCloudCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String
)

@Singleton
class MagentaCloudAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "magenta_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(accountId: String, credentials: MagentaCloudCredentials) {
        prefs.edit()
            .putString("${accountId}_url",  credentials.serverUrl.trimEnd('/'))
            .putString("${accountId}_user", credentials.username)
            .putString("${accountId}_pass", credentials.appPassword)
            .apply()
    }

    fun getCredentials(accountId: String): MagentaCloudCredentials? {
        val url  = prefs.getString("${accountId}_url",  null) ?: return null
        val user = prefs.getString("${accountId}_user", null) ?: return null
        val pass = prefs.getString("${accountId}_pass", null) ?: return null
        return MagentaCloudCredentials(url, user, pass)
    }

    fun removeCredentials(accountId: String) {
        prefs.edit()
            .remove("${accountId}_url")
            .remove("${accountId}_user")
            .remove("${accountId}_pass")
            .apply()
    }

    fun isAuthenticated(accountId: String): Boolean =
        getCredentials(accountId) != null

    fun getBasicAuthHeader(accountId: String): String? {
        val creds = getCredentials(accountId) ?: return null
        val raw = "${creds.username}:${creds.appPassword}"
        return "Basic ${Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))}"
    }
}
