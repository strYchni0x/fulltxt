package me.fulltxt.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.data.local.DatabaseKeyManager
import me.fulltxt.app.data.local.FulltxtDatabase
import me.fulltxt.app.data.local.SqlCipherUtils
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FulltxtDatabase,
    private val keyManager: DatabaseKeyManager
) {
    /**
     * Exports the index as a portable, user-passphrase-encrypted backup.
     *
     * The live index is SQLCipher-encrypted with a device-bound key, so its raw bytes could never
     * be restored on another device. We therefore export a *plaintext* snapshot (via
     * `sqlcipher_export` on the existing Room connection) and re-encrypt it with the user's
     * [passphrase] through [BackupCrypto]. The plaintext snapshot lives only in app-private
     * storage and is deleted immediately afterwards.
     */
    suspend fun exportTo(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("fulltxt.db")
        val tmpPlain = File(dbFile.parentFile, "fulltxt_export.tmp")
        try {
            tmpPlain.delete()
            val sdb = db.openHelper.writableDatabase
            // Merge any WAL pages into the main DB so the export is a consistent snapshot.
            sdb.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()
            sdb.execSQL("ATTACH DATABASE ? AS plaintext KEY ''", arrayOf<Any?>(tmpPlain.absolutePath))
            try {
                // The cursor MUST be stepped — sqlcipher_export() only runs when the row is
                // fetched. Closing without moveToFirst() leaves the plaintext copy empty.
                sdb.query(SimpleSQLiteQuery("SELECT sqlcipher_export('plaintext')")).use { it.moveToFirst() }
            } finally {
                sdb.execSQL("DETACH DATABASE plaintext")
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tmpPlain.inputStream().use { input -> BackupCrypto.encrypt(input, out, passphrase) }
            } ?: throw IOException("Cannot open output stream")
        } finally {
            tmpPlain.delete()
        }
    }

    /**
     * Restores a backup created by [exportTo]. The backup is decrypted with the user [passphrase]
     * to a plaintext temp file, validated, then re-encrypted with this device's index key before
     * it replaces the live database. A wrong passphrase or corrupt file fails during decryption —
     * before the existing index is touched — so the current index is never lost.
     */
    suspend fun importFrom(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("fulltxt.db")
        val tmpPlain = File(dbFile.parentFile, "fulltxt_import.tmp")
        val tmpEnc = File(dbFile.parentFile, "fulltxt_import_enc.tmp")

        try {
            // 1. Decrypt the backup to a plaintext temp file (throws on wrong passphrase/corruption).
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpPlain.outputStream().use { out -> BackupCrypto.decrypt(input, out, passphrase) }
            } ?: throw IOException("Cannot read backup file")

            // 2. Validate the decrypted payload is actually a SQLite database.
            if (!SqlCipherUtils.isPlaintext(tmpPlain)) {
                throw IllegalArgumentException("Keine gültige FullTXT-Backup-Datei.")
            }

            // 3. Re-encrypt with this device's index key.
            SqlCipherUtils.encryptFromPlaintext(tmpPlain, keyManager.passphrase(), tmpEnc)

            // 4. Swap the encrypted copy into place.
            db.close()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            tmpEnc.copyTo(dbFile, overwrite = true)
        } finally {
            tmpPlain.delete()
            tmpEnc.delete()
        }
    }

    fun restartApp() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                      android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
