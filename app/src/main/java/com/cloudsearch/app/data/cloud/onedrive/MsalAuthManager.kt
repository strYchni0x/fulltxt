package me.fulltxt.app.data.cloud.onedrive

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import me.fulltxt.app.R
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MsalAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var msalApp: IMultipleAccountPublicClientApplication? = null
    private var activityRef: WeakReference<Activity>? = null
    private val authenticatedAccountIds = mutableSetOf<String>()

    var lastSignedInAccount: IAccount? = null
        private set

    private val scopes = listOf("Files.Read")

    fun setActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    fun isAuthenticated(accountId: String): Boolean = accountId in authenticatedAccountIds

    suspend fun acquireToken(accountId: String): String {
        val app = getApp()

        if (accountId.isNotEmpty()) {
            val account = getAccountById(app, accountId)
            if (account != null) {
                runCatching { acquireTokenSilent(app, account) }
                    .onSuccess { token ->
                        authenticatedAccountIds.add(accountId)
                        return token
                    }
                    .onFailure { e ->
                        if (e !is MsalUiRequiredException) throw e
                    }
            }
        }

        val activity = activityRef?.get()
            ?: throw IllegalStateException("Activity required for interactive authentication — call setActivity() first")
        return acquireTokenInteractive(app, activity)
    }

    /**
     * Reads all accounts from MSAL's encrypted token cache and marks them as authenticated.
     * Call once on app start so isAuthenticated() returns correct results after a restart.
     */
    suspend fun loadCachedAccounts() {
        runCatching {
            val app = getApp()
            suspendCancellableCoroutine { cont ->
                app.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
                    override fun onTaskCompleted(result: List<IAccount>) {
                        authenticatedAccountIds.addAll(result.map { it.id })
                        cont.resume(Unit)
                    }
                    override fun onError(exception: MsalException) = cont.resume(Unit)
                })
            }
        }
    }

    fun removeAccount(accountId: String) {
        authenticatedAccountIds.remove(accountId)
        if (accountId == lastSignedInAccount?.id) lastSignedInAccount = null
        val app = msalApp ?: return
        app.getAccount(accountId, object : IMultipleAccountPublicClientApplication.GetAccountCallback {
            override fun onTaskCompleted(result: IAccount?) {
                result ?: return
                app.removeAccount(result, object : IMultipleAccountPublicClientApplication.RemoveAccountCallback {
                    override fun onRemoved() {}
                    override fun onError(exception: MsalException) {}
                })
            }
            override fun onError(exception: MsalException) {}
        })
    }

    private suspend fun getApp(): IMultipleAccountPublicClientApplication {
        msalApp?.let { return it }
        // MSAL 5.x createMultipleAccountPublicClientApplication requires a File; extract from raw resource once.
        val configFile = File(context.cacheDir, "msal_config.json").also { file ->
            if (!file.exists()) {
                context.resources.openRawResource(R.raw.msal_config)
                    .use { input -> file.outputStream().use { input.copyTo(it) } }
            }
        }
        return suspendCancellableCoroutine { cont ->
            PublicClientApplication.createMultipleAccountPublicClientApplication(
                context,
                configFile,
                object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                    override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                        msalApp = application
                        cont.resume(application)
                    }
                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private suspend fun getAccountById(
        app: IMultipleAccountPublicClientApplication,
        accountId: String
    ): IAccount? = suspendCancellableCoroutine { cont ->
        app.getAccount(accountId, object : IMultipleAccountPublicClientApplication.GetAccountCallback {
            override fun onTaskCompleted(result: IAccount?) = cont.resume(result)
            override fun onError(exception: MsalException) = cont.resume(null)
        })
    }

    private suspend fun acquireTokenSilent(
        app: IMultipleAccountPublicClientApplication,
        account: IAccount
    ): String = suspendCancellableCoroutine { cont ->
        val params = AcquireTokenSilentParameters.Builder()
            .withScopes(scopes)
            .forAccount(account)
            .fromAuthority(account.authority)
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) = cont.resume(result.accessToken)
                override fun onError(exception: MsalException) = cont.resumeWithException(exception)
            })
            .build()
        app.acquireTokenSilentAsync(params)
    }

    private suspend fun acquireTokenInteractive(
        app: IMultipleAccountPublicClientApplication,
        activity: Activity
    ): String = suspendCancellableCoroutine { cont ->
        val params = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(scopes)
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    lastSignedInAccount = result.account
                    authenticatedAccountIds.add(result.account.id)
                    cont.resume(result.accessToken)
                }
                override fun onCancel() { cont.cancel() }
                override fun onError(exception: MsalException) = cont.resumeWithException(exception)
            })
            .build()
        app.acquireToken(params)
    }
}
