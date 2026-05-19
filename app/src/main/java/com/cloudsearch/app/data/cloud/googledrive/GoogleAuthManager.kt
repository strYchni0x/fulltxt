package me.fulltxt.app.data.cloud.googledrive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getCredential(accountName: String): GoogleAccountCredential =
        GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_READONLY))
            .also { it.selectedAccountName = accountName }
}
