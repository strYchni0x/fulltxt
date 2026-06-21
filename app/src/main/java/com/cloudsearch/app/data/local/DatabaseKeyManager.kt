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
 * Holds the passphrase that encrypts the on-device SQLCipher index.
 *
 * The passphrase is a random 256-bit value, generated once on first launch and stored in
 * [EncryptedSharedPreferences] — i.e. wrapped by a hardware-backed Android Keystore master key.
 * It never leaves the device. (Index *backups* use a separate, user-chosen passphrase so they
 * stay portable across devices; see [me.fulltxt.app.data.backup.BackupCrypto].)
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
     * The SQLCipher passphrase, as an ASCII string (Base64 of 32 random bytes). Generated and
     * persisted on first call, returned unchanged afterwards. Pure ASCII, so it can be embedded
     * safely in a SQLCipher `KEY '...'` clause and used as UTF-8 bytes interchangeably.
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
