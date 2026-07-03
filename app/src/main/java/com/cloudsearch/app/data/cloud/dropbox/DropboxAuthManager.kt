package me.fulltxt.app.data.cloud.dropbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.R
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val REDIRECT_URI = "fulltxt://dropbox-auth"
        private const val TOKEN_URL    = "https://api.dropbox.com/oauth2/token"
        private const val ACCOUNT_URL  = "https://api.dropboxapi.com/2/users/get_current_account"

        /** Statischer Kanal für die Kommunikation DropboxCallbackActivity → DropboxAuthManager. */
        @Volatile private var pendingCallback: CompletableDeferred<String>? = null

        /** Wird von DropboxCallbackActivity aufgerufen, wenn der OAuth-Redirect eintrifft. */
        @JvmStatic
        fun deliverCode(code: String?) {
            Log.d("DropboxAuth", "deliverCode: code=${code?.take(8)}… pendingCallback=$pendingCallback")
            val d = pendingCallback ?: run {
                Log.e("DropboxAuth", "deliverCode: pendingCallback is null – ignoring")
                return
            }
            pendingCallback = null
            if (code != null) d.complete(code)
            else d.completeExceptionally(Exception("OAuth-Vorgang abgebrochen"))
        }
    }

    // ── Persistierter Zustand ─────────────────────────────────────────────────

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dropbox_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Temporäre Prefs für den PKCE-Code-Verifier (übersteht ein Prozess-Ende während des Browser-Flows). */
    private val tempPrefs by lazy {
        context.getSharedPreferences("dropbox_auth_temp", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    var lastSignedInAccount: DropboxAccountInfo? = null
        private set

    // ── Öffentliche API ───────────────────────────────────────────────────────

    /**
     * Gibt einen "Bearer <token>"-String für [accountId] zurück und erneuert ihn bei Ablauf.
     * Wirft, wenn keine Tokens gespeichert sind.
     */
    suspend fun getBearerHeader(accountId: String): String =
        "Bearer ${getValidAccessToken(accountId)}"

    fun isAuthenticated(accountId: String): Boolean =
        prefs.getString("${accountId}_refresh", null) != null

    /**
     * Vollständiger Anmelde-Flow.
     *
     * - Wenn [accountId] nicht leer ist und gültige Tokens existieren → gecachte Infos zurückgeben.
     * - Andernfalls → PKCE-Browser-Flow.
     *
     * Nach Abschluss ist [lastSignedInAccount] gesetzt.
     */
    suspend fun authenticate(accountId: String): DropboxAccountInfo {
        if (accountId.isNotEmpty() && isAuthenticated(accountId)) {
            val info = getStoredAccountInfo(accountId)
            if (info != null) {
                lastSignedInAccount = info
                return info
            }
        }
        return startBrowserFlow()
    }

    fun signOut(accountId: String) {
        prefs.edit()
            .remove("${accountId}_access")
            .remove("${accountId}_refresh")
            .remove("${accountId}_expiry")
            .remove("${accountId}_display_name")
            .remove("${accountId}_email")
            .apply()
        if (lastSignedInAccount?.accountId == accountId) lastSignedInAccount = null
    }

    // ── PKCE-Browser-Flow ─────────────────────────────────────────────────────

    private suspend fun startBrowserFlow(): DropboxAccountInfo {
        val verifier  = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val appKey    = context.getString(R.string.dropbox_app_key)

        // Verifier persistieren, falls Android die App beendet, während der Browser offen ist
        tempPrefs.edit().putString("code_verifier", verifier).apply()

        val authUrl = Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id",             appKey)
            .appendQueryParameter("response_type",         "code")
            .appendQueryParameter("redirect_uri",          REDIRECT_URI)
            .appendQueryParameter("code_challenge",        challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("token_access_type",     "offline")
            .appendQueryParameter("scope",                 "files.metadata.read files.content.read account_info.read")
            .build()

        val deferred = CompletableDeferred<String>()
        pendingCallback = deferred

        // Browser öffnen
        context.startActivity(
            Intent(Intent.ACTION_VIEW, authUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        Log.d("DropboxAuth", "Awaiting OAuth code…")
        val code         = deferred.await()
        Log.d("DropboxAuth", "Code received (${code.take(8)}…), exchanging for token")
        val storedVerifier = tempPrefs.getString("code_verifier", null)
            ?: throw IllegalStateException("PKCE code_verifier verloren")
        tempPrefs.edit().remove("code_verifier").apply()

        val tokenResponse = exchangeCodeForToken(code, storedVerifier, appKey)
        Log.d("DropboxAuth", "Token exchange OK, account_id=${tokenResponse.accountId}")
        val dbAccountId   = tokenResponse.accountId
            ?: throw Exception("Kein account_id in Token-Antwort")

        saveTokens(dbAccountId, tokenResponse)

        val info = fetchCurrentAccount(tokenResponse.accessToken)
        Log.d("DropboxAuth", "Account info: ${info.displayName} <${info.email}>")
        saveAccountInfo(dbAccountId, info)
        lastSignedInAccount = info
        return info
    }

    // ── Token-Lebenszyklus ────────────────────────────────────────────────────

    private suspend fun getValidAccessToken(accountId: String): String {
        val expiry = prefs.getLong("${accountId}_expiry", 0L)
        // 60 s vor Ablauf erneuern
        if (System.currentTimeMillis() < expiry - 60_000L) {
            return prefs.getString("${accountId}_access", null)
                ?: refreshAccessToken(accountId)
        }
        return refreshAccessToken(accountId)
    }

    private suspend fun refreshAccessToken(accountId: String): String =
        withContext(Dispatchers.IO) {
            val refreshToken = prefs.getString("${accountId}_refresh", null)
                ?: throw IllegalStateException("Kein Refresh-Token für $accountId")
            val appKey = context.getString(R.string.dropbox_app_key)

            val body = FormBody.Builder()
                .add("grant_type",    "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id",     appKey)
                .build()

            val response = okHttpClient.newCall(
                Request.Builder().url(TOKEN_URL).post(body).build()
            ).execute()

            val json = response.body?.string()
                ?: throw Exception("Leere Antwort beim Token-Refresh")
            if (!response.isSuccessful)
                throw Exception("Token-Refresh fehlgeschlagen: HTTP ${response.code}")

            val parsed = gson.fromJson(json, DropboxTokenResponse::class.java)
            val newExpiry = System.currentTimeMillis() + parsed.expiresIn * 1000L
            prefs.edit()
                .putString("${accountId}_access", parsed.accessToken)
                .putLong("${accountId}_expiry",   newExpiry)
                .apply()

            parsed.accessToken
        }

    private suspend fun exchangeCodeForToken(
        code: String,
        verifier: String,
        appKey: String
    ): DropboxTokenResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("code",          code)
            .add("grant_type",    "authorization_code")
            .add("client_id",     appKey)
            .add("redirect_uri",  REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()

        val response = okHttpClient.newCall(
            Request.Builder().url(TOKEN_URL).post(body).build()
        ).execute()

        val json = response.body?.string()
            ?: throw Exception("Leere Antwort beim Token-Austausch")
        if (!response.isSuccessful)
            throw Exception("Token-Austausch fehlgeschlagen: HTTP ${response.code} – $json")

        gson.fromJson(json, DropboxTokenResponse::class.java)
    }

    private fun saveTokens(accountId: String, response: DropboxTokenResponse) {
        val expiry = System.currentTimeMillis() + response.expiresIn * 1000L
        prefs.edit()
            .putString("${accountId}_access",  response.accessToken)
            .putLong("${accountId}_expiry",    expiry)
            .apply()
        response.refreshToken?.let { rt ->
            prefs.edit().putString("${accountId}_refresh", rt).apply()
        }
    }

    // ── Konto-Infos ───────────────────────────────────────────────────────────

    private suspend fun fetchCurrentAccount(accessToken: String): DropboxAccountInfo =
        withContext(Dispatchers.IO) {
            // Dropbox-RPC-Endpunkte ohne Argumente benötigen einen literalen JSON-"null"-Body.
            val request = Request.Builder()
                .url(ACCOUNT_URL)
                .post("null".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val json = response.body?.string()
                ?: throw Exception("Leere Antwort von /users/get_current_account")
            if (!response.isSuccessful)
                throw Exception("Konto-Abfrage fehlgeschlagen: HTTP ${response.code}")

            val account = gson.fromJson(json, DropboxAccount::class.java)
            DropboxAccountInfo(
                accountId   = account.accountId,
                displayName = account.name?.displayName ?: account.accountId,
                email       = account.email ?: ""
            )
        }

    private fun saveAccountInfo(accountId: String, info: DropboxAccountInfo) {
        prefs.edit()
            .putString("${accountId}_display_name", info.displayName)
            .putString("${accountId}_email",        info.email)
            .apply()
    }

    private fun getStoredAccountInfo(accountId: String): DropboxAccountInfo? {
        val name  = prefs.getString("${accountId}_display_name", null) ?: return null
        val email = prefs.getString("${accountId}_email",        null) ?: return null
        return DropboxAccountInfo(accountId, name, email)
    }

    // ── PKCE-Hilfsfunktionen ──────────────────────────────────────────────────

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
