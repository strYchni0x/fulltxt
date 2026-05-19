package me.fulltxt.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity

@Database(
    entities = [FileMetadataEntity::class, FileContentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FulltxtDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}
