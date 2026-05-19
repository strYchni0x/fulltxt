package me.fulltxt.app.data.cloud.onedrive

import android.content.Context
import com.microsoft.identity.client.IPublicClientApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsalAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var msalApp: IPublicClientApplication? = null

    suspend fun getApp(): IPublicClientApplication {
        TODO("Initialize from res/raw/msal_config.json via PublicClientApplication.createMultipleAccountPublicClientApplication")
    }

    suspend fun acquireToken(accountId: String): String {
        TODO("Silent token acquisition with interactive fallback; scope: Files.Read")
    }

    fun removeAccount(accountId: String) {
        TODO("Remove MSAL account entry and clear cached tokens")
    }
}
