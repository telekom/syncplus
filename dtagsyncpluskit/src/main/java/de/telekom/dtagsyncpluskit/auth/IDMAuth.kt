/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.telekom.dtagsyncpluskit.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import de.telekom.dtagsyncpluskit.api.IDMEnv
import de.telekom.dtagsyncpluskit.api.TokenStore
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.openid.appauth.*
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("unused")
class IDMAuth(private val context: Context) {
    open class ErrorHandler(private val l: (Exception) -> Unit) {
        open fun onError(ex: Exception) {
            l(ex)
        }
    }

    open class SuccessHandler(private val l: (TokenStore) -> Unit) {
        open fun onSuccess(store: TokenStore) {
            l(store)
        }
    }

    companion object {
        const val RC_AUTH = 100
        private val CLAIMS = arrayOf(
            ":alia",
            ":f136",
            ":mainEmail",
            ":additionalEmail"
        )
        private val SCOPES = arrayOf(
            "openid",
            "offline_access"
        )

        private fun getScopes() = SCOPES.joinToString(separator = " ")
        private fun getClaims(): String =
            CLAIMS.joinToString(",") { "\"urn:telekom.com${it}\":{\"required\":true}" }
    }

    private var mAuthState: AuthState? = null
    private val mAuthService: AuthorizationService by lazy {
        /*val config = AppAuthConfiguration.Builder()
            .setConnectionBuilder { uri ->
                val conn = URL(uri.toString()).openConnection() as HttpURLConnection
                conn.connectTimeout = 15
                conn.readTimeout = 10
                conn.instanceFollowRedirects = false
                conn
            }
            .build()*/
        AuthorizationService(context)
    }

    private var mErrorHandler: ErrorHandler? = null
    private var mSuccessHandler: SuccessHandler? = null

    fun setErrorHandler(l: (Exception) -> Unit) {
        mErrorHandler = ErrorHandler(l)
    }

    fun setSuccessHandler(l: (TokenStore) -> Unit) {
        mSuccessHandler = SuccessHandler(l)
    }

    suspend fun startLoginProcess(
        fragment: Fragment,
        redirectUri: Uri,
        loginHint: String?,
        env: IDMEnv
    ) {
        val intent = getLoginProcessIntent(redirectUri, loginHint, env)
        fragment.startActivityForResult(intent, RC_AUTH)
    }

    suspend fun startLoginProcess(
        activity: Activity,
        redirectUri: Uri,
        loginHint: String?,
        env: IDMEnv
    ) {
        val intent = getLoginProcessIntent(redirectUri, loginHint, env)
        activity.startActivityForResult(intent, RC_AUTH)
    }

    suspend fun getLoginProcessIntent(redirectUri: Uri, loginHint: String?, env: IDMEnv): Intent {
        val serviceConfiguration = fetchFromIssuer(env.baseUrl)
        val authRequest = AuthorizationRequest.Builder(
            serviceConfiguration,
            env.clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScope(getScopes())
            .setPrompt("x-no-sso")
            .setLoginHint(loginHint)
            .setAdditionalParameters(
                hashMapOf(
                    "claims" to "{\"id_token\":{${getClaims()}}}"
                )
            )
            .build()

        mAuthState = AuthState(serviceConfiguration)
        return mAuthService.getAuthorizationRequestIntent(authRequest)
    }

    private suspend fun fetchFromIssuer(baseUrl: String): AuthorizationServiceConfiguration =
        suspendCoroutine { cont ->
            val configurationUri = Uri.parse("${baseUrl}/.well-known/openid-configuration")
            AuthorizationServiceConfiguration.fetchFromUrl(configurationUri) { config, exc ->
                if (exc != null || config == null) {
                    cont.resumeWithException(
                        exc ?: IllegalStateException("ServiceConfiguration is null")
                    )
                } else {
                    cont.resume(config)
                }
            }
        }

    private fun handleAuthorizationResponse(response: AuthorizationResponse) {
        val tokenExchRequest = response.createTokenExchangeRequest()
        mAuthService.performTokenRequest(tokenExchRequest) { resp, ex ->
            mAuthState?.update(resp, ex)
            when {
                ex != null -> {
                    Logger.log.severe("Error: Authorization: $ex")
                    Log.e("SyncPlus", "Error: Authorization", ex)
                    mErrorHandler?.onError(ex)
                }
                resp != null -> {
                    updateRefreshToken(resp)
                }
                else -> {
                    throw IllegalStateException("Should not happen")
                }
            }
        }
    }

    private fun updateRefreshToken(response: TokenResponse) {
        val idToken = response.idToken ?: throw IllegalStateException("idToken is null")
        val lastRequest = response.request
        val tokenRequest = TokenRequest.Builder(lastRequest.configuration, lastRequest.clientId)
            .setAuthorizationCode(lastRequest.authorizationCode)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRedirectUri(lastRequest.redirectUri)
            .setScope("spica")
            .setCodeVerifier(lastRequest.codeVerifier)
            .setRefreshToken(response.refreshToken)
            .setAdditionalParameters(HashMap())
            .build()

        mAuthService.performTokenRequest(tokenRequest) { resp, ex ->
            when {
                ex != null -> {
                    Logger.log.severe("Error: Refreshing Token: $ex")
                    Log.e("SyncPlus", "Error: Refreshing Token", ex)
                    mErrorHandler?.onError(ex)
                }
                resp != null -> {
                    val accessToken =
                        resp.accessToken ?: throw IllegalStateException("accessToken is null")
                    val refreshToken =
                        resp.refreshToken ?: throw IllegalStateException("refreshToken is null")
                    val store = TokenStore(accessToken, idToken, refreshToken)
                    mSuccessHandler?.onSuccess(store)
                }
                else -> {
                    throw IllegalStateException("Should not happen")
                }
            }
        }
    }

    fun getAccessTokenSync1(env: IDMEnv, refreshToken: String, lock: Any): Pair<String, String>? {
        var ready = false
        var result: Pair<String, String>? = null

        val configurationUri = Uri.parse("${env.baseUrl}/.well-known/openid-configuration")
        AuthorizationServiceConfiguration.fetchFromUrl(configurationUri) { config, exc ->
            if (exc != null || config == null) {
                synchronized(lock) {
                    ready = true
                    lock.notifyAll()
                }
            } else {
                mAuthState = mAuthState ?: AuthState(config)
                val req = TokenRequest.Builder(config, env.clientId)
                    .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .setScope("spica")
                    .build()

                mAuthService.performTokenRequest(req) { resp, ex ->
                    when {
                        ex != null -> {
                            Logger.log.severe("Error: Refreshing Token: $ex")
                            Logger.log.severe("refreshToken: $refreshToken")
                            Log.e("SyncPlus", "Error: Refreshing Token", ex)
                            synchronized(lock) {
                                ready = true
                                lock.notifyAll()
                            }
                        }
                        resp != null -> {
                            synchronized(lock) {
                                ready = true
                                result = Pair(resp.accessToken!!, resp.refreshToken!!)
                                lock.notifyAll()
                            }
                        }
                        else -> {
                            throw IllegalStateException("Should not happen")
                        }
                    }
                }
            }
        }

        synchronized(lock) {
            while (!ready) {
                try {
                    lock.wait()
                } catch (ex: InterruptedException) {
                    ex.printStackTrace()
                }
            }
        }

        return result
    }

    fun getAccessTokenSync(
        env: IDMEnv,
        @Suppress("UNUSED_PARAMETER") redirectUri: Uri,
        refreshToken: String
    ): Pair<String, String>? =
        runBlocking func@{
            val serviceConfiguration = fetchFromIssuer(env.baseUrl)
            mAuthState = mAuthState ?: AuthState(serviceConfiguration)
            val req = TokenRequest.Builder(serviceConfiguration, env.clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .setScope("spica")
                .build()

            val (resp, ex) = performTokenRequest(req)
            return@func when {
                ex != null -> {
                    Logger.log.severe("Error: Refreshing Token: $ex")
                    Logger.log.severe("refreshToken: $refreshToken")
                    Log.e("SyncPlus", "Error: Refreshing Token", ex)
                    null
                }
                resp != null -> {
                    Pair(resp.accessToken!!, resp.refreshToken!!)
                }
                else -> {
                    throw IllegalStateException("Should not happen")
                }
            }
        }

    fun getAccessToken(
        env: IDMEnv,
        redirectUri: Uri,
        refreshToken: String,
        callback: (accessToken: String?, refreshToken: String?) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val (accessToken, newRefreshToken) = getAccessTokenSync(env, redirectUri, refreshToken)
                ?: Pair(null, null)
            callback(accessToken, newRefreshToken)
        }
    }

    private suspend fun performTokenRequest(tokenRequest: TokenRequest): Pair<TokenResponse?, AuthorizationException?> =
        suspendCoroutine { cont ->
            mAuthService.performTokenRequest(tokenRequest) { response, ex ->
                cont.resume(Pair(response, ex))
            }
        }

    fun onActivityResult(
        requestCode: Int,
        @Suppress("UNUSED_PARAMETER") resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode == RC_AUTH && data != null) {
            val response = AuthorizationResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            mAuthState?.update(response, ex)
            return if (response != null) {
                handleAuthorizationResponse(response)
                true
            } else {
                Logger.log.severe("Error: Authorization: $ex")
                Log.e("SyncPlus", "Error: Authorization", ex)
                mErrorHandler?.onError(ex ?: Exception("Authorization Error"))
                false
            }
        }

        return false
    }
}
