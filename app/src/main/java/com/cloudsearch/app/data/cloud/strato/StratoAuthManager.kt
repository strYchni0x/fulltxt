package me.fulltxt.app.data.cloud.strato

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class StratoCredentials(
    val username: String,
    val password: String
)

@Singleton
class StratoAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Strato-HiDrive-WebDAV-Server – für alle Konten fest. */
        const val SERVER_URL = "https://webdav.hidrive.strato.com"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "strato_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(accountId: String, credentials: StratoCredentials) {
        prefs.edit()
            .putString("${accountId}_user", credentials.username)
            .putString("${accountId}_pass", credentials.password)
            .apply()
    }

    fun getCredentials(accountId: String): StratoCredentials? {
        val user = prefs.getString("${accountId}_user", null) ?: return null
        val pass = prefs.getString("${accountId}_pass", null) ?: return null
        return StratoCredentials(user, pass)
    }

    fun removeCredentials(accountId: String) {
        prefs.edit()
            .remove("${accountId}_user")
            .remove("${accountId}_pass")
            .apply()
    }

    fun isAuthenticated(accountId: String): Boolean =
        getCredentials(accountId) != null

    fun getBasicAuthHeader(accountId: String): String? {
        val creds = getCredentials(accountId) ?: return null
        val raw = "${creds.username}:${creds.password}"
        return "Basic ${Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))}"
    }
}
