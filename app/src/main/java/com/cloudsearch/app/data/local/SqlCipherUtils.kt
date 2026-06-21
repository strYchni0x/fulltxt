package me.fulltxt.app.data.local

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.IOException

/**
 * Low-level helpers around the SQLCipher-encrypted index file.
 *
 * The live index ([provideDatabase][me.fulltxt.app.di.DatabaseModule]) is encrypted with the
 * device-bound [DatabaseKeyManager] passphrase. Backups, however, must be portable, so they are
 * stored as *plaintext* SQLite (then wrapped with a user passphrase by `BackupCrypto`). These
 * helpers bridge the two representations via SQLCipher's `sqlcipher_export()`.
 */
object SqlCipherUtils {

    /** True if [file] exists and is an unencrypted SQLite database (begins with the SQLite magic). */
    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = file.inputStream().use { val b = ByteArray(16); it.read(b); b }
        return header.toString(Charsets.UTF_8).startsWith("SQLite format 3")
    }

    /**
     * Reads a plaintext SQLite database from [plaintextFile] and writes an encrypted copy to
     * [encryptedOut] (overwriting it). Used when importing a decrypted backup into the live index.
     */
    fun encryptFromPlaintext(
        plaintextFile: File,
        passphrase: String,
        encryptedOut: File
    ) {
        System.loadLibrary("sqlcipher")
        encryptedOut.delete()
        // Empty password opens the source as plaintext; the encrypted copy is created via ATTACH.
        val db = SQLiteDatabase.openOrCreateDatabase(plaintextFile, "", null, null)
        try {
            db.rawExecSQL("ATTACH DATABASE '${escape(encryptedOut.path)}' AS encrypted KEY '${escape(passphrase)}';")
            db.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            db.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            db.close()
        }
        if (!encryptedOut.exists()) throw IOException("Encryption produced no output file")
    }

    /** Escapes a value for embedding inside a single-quoted SQL string literal. */
    private fun escape(value: String): String = value.replace("'", "''")
}
