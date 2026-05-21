package me.fulltxt.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity

@Database(
    entities = [FileMetadataEntity::class, FileContentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FulltxtDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE file_metadata ADD COLUMN webUrl TEXT")
    }
}
