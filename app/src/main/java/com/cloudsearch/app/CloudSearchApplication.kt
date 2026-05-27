package me.fulltxt.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FulltxtApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INDEXING,
                "Indexierung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Fortschritt beim Indexieren von Cloud-Dateien"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_INDEXING = "indexing_channel"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
