package me.fulltxt.app.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.data.local.FulltxtDatabase
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FulltxtDatabase
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        // Merge WAL into main database file before copying
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        val dbFile = context.getDatabasePath("fulltxt.db")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("Cannot open output stream")
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("fulltxt.db")

        // Validate: check SQLite magic bytes
        val header = context.contentResolver.openInputStream(uri)?.use {
            val buf = ByteArray(16); it.read(buf); buf
        } ?: throw IOException("Cannot read backup file")
        val magic = "SQLite format 3"
        if (!header.take(15).toByteArray().toString(Charsets.UTF_8).startsWith(magic.take(15))) {
            throw IllegalArgumentException("Keine gültige FullTXT-Backup-Datei.")
        }

        db.close()

        context.contentResolver.openInputStream(uri)?.use { input ->
            dbFile.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open input stream")

        // Remove stale WAL/SHM so the restored database opens cleanly
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
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
