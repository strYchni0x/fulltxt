package me.fulltxt.app.di

import android.content.Context
import androidx.room.Room
import me.fulltxt.app.data.local.FulltxtDatabase
import me.fulltxt.app.data.local.dao.FileIndexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FulltxtDatabase =
        Room.databaseBuilder(context, FulltxtDatabase::class.java, "fulltxt.db")
            .build()

    @Provides
    fun provideFileIndexDao(db: FulltxtDatabase): FileIndexDao = db.fileIndexDao()
}
