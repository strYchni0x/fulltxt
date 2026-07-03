package me.fulltxt.app.data.local

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.IOException

/**
 * Low-Level-Hilfsfunktionen rund um die SQLCipher-verschlüsselte Index-Datei.
 *
 * Der laufende Index ([provideDatabase][me.fulltxt.app.di.DatabaseModule]) ist mit der
 * gerätegebundenen [DatabaseKeyManager]-Passphrase verschlüsselt. Backups müssen jedoch portabel
 * sein und werden daher als *Klartext*-SQLite gespeichert (und danach von `BackupCrypto` mit einer
 * Benutzer-Passphrase umschlossen). Diese Helfer überbrücken die beiden Repräsentationen über
 * SQLCiphers `sqlcipher_export()`.
 */
object SqlCipherUtils {

    /** True, wenn [file] existiert und eine unverschlüsselte SQLite-Datenbank ist (beginnt mit der SQLite-Signatur). */
    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = file.inputStream().use { val b = ByteArray(16); it.read(b); b }
        return header.toString(Charsets.UTF_8).startsWith("SQLite format 3")
    }

    /**
     * Liest eine Klartext-SQLite-Datenbank aus [plaintextFile] und schreibt eine verschlüsselte Kopie
     * nach [encryptedOut] (überschreibt sie). Wird beim Importieren eines entschlüsselten Backups in
     * den laufenden Index verwendet.
     */
    fun encryptFromPlaintext(
        plaintextFile: File,
        passphrase: String,
        encryptedOut: File
    ) {
        System.loadLibrary("sqlcipher")
        encryptedOut.delete()
        // Leeres Passwort öffnet die Quelle als Klartext; die verschlüsselte Kopie wird per ATTACH erstellt.
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

    /** Escapt einen Wert zum Einbetten in ein einfach-quotiertes SQL-String-Literal. */
    private fun escape(value: String): String = value.replace("'", "''")
}
