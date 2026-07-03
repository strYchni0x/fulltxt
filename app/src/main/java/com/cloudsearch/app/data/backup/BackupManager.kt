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
     * Exportiert den Index als portables, mit Benutzer-Passphrase verschlüsseltes Backup.
     *
     * Der laufende Index ist mit SQLCipher und einem gerätegebundenen Schlüssel verschlüsselt, sodass
     * seine Rohbytes nie auf einem anderen Gerät wiederhergestellt werden könnten. Wir exportieren
     * daher einen *Klartext*-Snapshot (über `sqlcipher_export` auf der bestehenden Room-Verbindung)
     * und verschlüsseln ihn mit der [passphrase] des Benutzers über [BackupCrypto] neu. Der
     * Klartext-Snapshot liegt nur im app-privaten Speicher und wird unmittelbar danach gelöscht.
     */
    suspend fun exportTo(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("fulltxt.db")
        val tmpPlain = File(dbFile.parentFile, "fulltxt_export.tmp")
        try {
            tmpPlain.delete()
            val sdb = db.openHelper.writableDatabase
            // Etwaige WAL-Seiten in die Haupt-DB einbringen, damit der Export ein konsistenter Snapshot ist.
            sdb.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()
            sdb.execSQL("ATTACH DATABASE ? AS plaintext KEY ''", arrayOf<Any?>(tmpPlain.absolutePath))
            try {
                // Der Cursor MUSS weitergeschaltet werden — sqlcipher_export() läuft nur, wenn die Zeile
                // abgerufen wird. Ein Schließen ohne moveToFirst() lässt die Klartext-Kopie leer.
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
     * Stellt ein von [exportTo] erstelltes Backup wieder her. Das Backup wird mit der [passphrase] des
     * Benutzers in eine Klartext-Temp-Datei entschlüsselt, validiert und dann mit dem Index-Schlüssel
     * dieses Geräts neu verschlüsselt, bevor es die laufende Datenbank ersetzt. Ein falsches Passwort
     * oder eine beschädigte Datei schlägt während der Entschlüsselung fehl — bevor der bestehende Index
     * angetastet wird — sodass der aktuelle Index nie verloren geht.
     */
    suspend fun importFrom(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("fulltxt.db")
        val tmpPlain = File(dbFile.parentFile, "fulltxt_import.tmp")
        val tmpEnc = File(dbFile.parentFile, "fulltxt_import_enc.tmp")

        try {
            // 1. Das Backup in eine Klartext-Temp-Datei entschlüsseln (wirft bei falscher Passphrase/Beschädigung).
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpPlain.outputStream().use { out -> BackupCrypto.decrypt(input, out, passphrase) }
            } ?: throw IOException("Cannot read backup file")

            // 2. Prüfen, dass die entschlüsselten Daten tatsächlich eine SQLite-Datenbank sind.
            if (!SqlCipherUtils.isPlaintext(tmpPlain)) {
                throw IllegalArgumentException("Keine gültige FullTXT-Backup-Datei.")
            }

            // 3. Mit dem Index-Schlüssel dieses Geräts neu verschlüsseln.
            SqlCipherUtils.encryptFromPlaintext(tmpPlain, keyManager.passphrase(), tmpEnc)

            // 4. Die verschlüsselte Kopie an ihren Platz tauschen.
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
