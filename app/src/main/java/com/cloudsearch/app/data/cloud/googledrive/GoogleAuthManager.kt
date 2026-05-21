package me.fulltxt.app.data.cloud.googledrive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val driveScope = Scope(DriveScopes.DRIVE_READONLY)

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(driveScope)
        .build()

    private var signInLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingAuth: CompletableDeferred<String>? = null
    private val authenticatedAccountIds = mutableSetOf<String>()

    var lastSignedInAccount: GoogleSignInAccount? = null
        private set

    fun setSignInLauncher(launcher: ActivityResultLauncher<Intent>?) {
        signInLauncher = launcher
    }

    fun handleSignInResult(resultCode: Int, data: Intent?) {
        val deferred = pendingAuth ?: return
        pendingAuth = null
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val email = account.email ?: ""
                lastSignedInAccount = account
                authenticatedAccountIds.add(email)
                deferred.complete(email)
            } catch (e: ApiException) {
                deferred.completeExceptionally(e)
            }
        } else {
            deferred.completeExceptionally(Exception("Google Sign-In cancelled"))
        }
    }

    fun isAuthenticated(accountId: String): Boolean {
        if (accountId in authenticatedAccountIds) return true
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return lastAccount.email == accountId && GoogleSignIn.hasPermissions(lastAccount, driveScope)
    }

    suspend fun signIn(): String {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount?.email != null && GoogleSignIn.hasPermissions(lastAccount, driveScope)) {
            val email = lastAccount.email!!
            authenticatedAccountIds.add(email)
            return email
        }

        val launcher = signInLauncher
            ?: throw IllegalStateException("No SignIn launcher registered — call setSignInLauncher() from MainActivity")

        val deferred = CompletableDeferred<String>()
        pendingAuth = deferred
        launcher.launch(GoogleSignIn.getClient(context, signInOptions).signInIntent)
        return deferred.await()
    }

    fun signOut(accountId: String) {
        authenticatedAccountIds.remove(accountId)
        GoogleSignIn.getClient(context, signInOptions).signOut()
    }

    fun getCredential(accountName: String): GoogleAccountCredential =
        GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_READONLY))
            .also { it.selectedAccountName = accountName }
}
