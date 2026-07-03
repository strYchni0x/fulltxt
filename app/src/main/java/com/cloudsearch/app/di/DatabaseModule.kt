package me.fulltxt.app.di

import android.content.Context
import androidx.room.Room
import me.fulltxt.app.data.local.DatabaseKeyManager
import me.fulltxt.app.data.local.FulltxtDatabase
import me.fulltxt.app.data.local.MIGRATION_1_2
import me.fulltxt.app.data.local.MIGRATION_2_3
import me.fulltxt.app.data.local.SqlCipherUtils
import me.fulltxt.app.data.local.dao.FileIndexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): FulltxtDatabase {
        System.loadLibrary("sqlcipher")

        val dbFile = context.getDatabasePath("fulltxt.db")
        // Einmaliger Übergang zu einem verschlüsselten Index: Ein bereits vorhandener *Klartext*-Index
        // kann nicht mit SQLCipher geöffnet werden, daher wird er verworfen und verschlüsselt neu
        // erstellt. Der Benutzer indexiert einmal neu. (Keine In-place-sqlcipher_export-Migration — der
        // Index ist aus der Cloud reproduzierbar.)
        if (SqlCipherUtils.isPlaintext(dbFile)) {
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }

        val factory = SupportOpenHelperFactory(keyManager.passphrase().toByteArray(Charsets.UTF_8))
        return Room.databaseBuilder(context, FulltxtDatabase::class.java, "fulltxt.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideFileIndexDao(db: FulltxtDatabase): FileIndexDao = db.fileIndexDao()
}
