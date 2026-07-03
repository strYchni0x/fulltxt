package me.fulltxt.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält die Passphrase, die den lokalen SQLCipher-Index verschlüsselt.
 *
 * Die Passphrase ist ein zufälliger 256-Bit-Wert, der beim ersten Start einmal erzeugt und in
 * [EncryptedSharedPreferences] gespeichert wird — d. h. von einem hardwaregestützten
 * Android-Keystore-Masterkey umschlossen. Sie verlässt das Gerät nie. (Index-*Backups* verwenden
 * eine separate, vom Benutzer gewählte Passphrase, damit sie geräteübergreifend portabel bleiben;
 * siehe [me.fulltxt.app.data.backup.BackupCrypto].)
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "fulltxt_db_key",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Die SQLCipher-Passphrase als ASCII-String (Base64 von 32 zufälligen Bytes). Beim ersten Aufruf
     * erzeugt und persistiert, danach unverändert zurückgegeben. Reines ASCII, sodass sie sicher in
     * eine SQLCipher-`KEY '...'`-Klausel eingebettet und austauschbar als UTF-8-Bytes verwendet werden kann.
     */
    @Synchronized
    fun passphrase(): String {
        prefs.getString(KEY, null)?.let { return it }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.getEncoder().encodeToString(raw)
        prefs.edit().putString(KEY, encoded).apply()
        return encoded
    }

    private companion object {
        const val KEY = "index_passphrase"
    }
}
