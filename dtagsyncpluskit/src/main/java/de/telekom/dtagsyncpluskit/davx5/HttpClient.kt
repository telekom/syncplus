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

/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.telekom.dtagsyncpluskit.davx5

import android.app.Application
import android.os.Build
import android.security.KeyChain
import at.bitfire.cert4android.CustomCertManager
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.api.BearerAuthInterceptor
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import okhttp3.*
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.net.ssl.*

class HttpClient private constructor(
    val okHttpClient: OkHttpClient,
    private val certManager: CustomCertManager?
) : AutoCloseable {

    companion object {
        /** max. size of disk cache (10 MB) */
        const val DISK_CACHE_MAX_SIZE: Long = 10 * 1024 * 1024

        private val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256)
            .build()

        /** [OkHttpClient] singleton to build all clients from */
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            // set timeouts
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .connectionSpecs(Collections.singletonList(connectionSpec))

            // don't allow redirects by default, because it would break PROPFIND handling
            .followRedirects(false)

            // add User-Agent to every request
            .addNetworkInterceptor(UserAgentInterceptor)

            .build()
    }


    override fun close() {
        okHttpClient.cache?.close()
        certManager?.close()
    }

    class Builder(
        app: Application,
        accountSettings: AccountSettings? = null,
        val logger: java.util.logging.Logger = Logger.log
    ) {
        private val context = app.applicationContext
        private var certManager: CustomCertManager? = null
        private var certificateAlias: String? = null
        private var cache: Cache? = null
        private var authInterceptor: BearerAuthInterceptor? = null
        private var loggingInterceptor: HttpLoggingInterceptor? = null

        private val orig = sharedClient.newBuilder()

        init {
            // add cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
            orig.cookieJar(MemoryCookieStore())

            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                addLoggingInterceptor()
            }

            context.let {
                // custom proxy support
                /*
                        try {
                            if (settings.getBoolean(Settings.OVERRIDE_PROXY) == true) {
                                val address = InetSocketAddress(
                                    settings.getString(Settings.OVERRIDE_PROXY_HOST)
                                        ?: Settings.OVERRIDE_PROXY_HOST_DEFAULT,
                                    settings.getInt(Settings.OVERRIDE_PROXY_PORT)
                                        ?: Settings.OVERRIDE_PROXY_PORT_DEFAULT
                                )

                                val proxy = Proxy(Proxy.Type.HTTP, address)
                                orig.proxy(proxy)
                                Logger.log.log(Level.INFO, "Using proxy", proxy)
                            }
                        } catch (e: Exception) {
                            Logger.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
                        }
                        */

                //if (BuildConfig.customCerts)
                val distrustSystemCertificates = false
                customCertManager(
                    CustomCertManager(
                        context, true,
                        !(distrustSystemCertificates)
                    )
                )
            }

            // use account settings for authentication
            accountSettings?.let {
                addAuthentication(BearerAuthInterceptor(app, it.getCredentials()))
            }
        }

        fun withUnauthorizedCallback(callback: () -> Unit): Builder {
            authInterceptor?.setUnauthorizedCallback(callback)
            return this
        }

        fun setHttpLogLevel(logLevel: HttpLoggingInterceptor.Level) {
            addLoggingInterceptor()
            loggingInterceptor?.level = logLevel
        }

        private fun addLoggingInterceptor() {
            if (loggingInterceptor != null)
                return

            loggingInterceptor =
                HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        logger.finest(message)
                    }
                })
            loggingInterceptor!!.level = HttpLoggingInterceptor.Level.BODY
            orig.addInterceptor(loggingInterceptor!!)

        }

        fun withDiskCache(): Builder {
            val context = context
                ?: throw IllegalArgumentException("Context is required to find the cache directory")
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir).filterNotNull()) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    Logger.log.fine("Using disk cache: $cacheDir")
                    orig.cache(
                        Cache(
                            cacheDir,
                            DISK_CACHE_MAX_SIZE
                        )
                    )
                    break
                }
            }
            return this
        }

        fun followRedirects(follow: Boolean): Builder {
            orig.followRedirects(follow)
            return this
        }

        fun customCertManager(manager: CustomCertManager) {
            certManager = manager
        }

        fun setForeground(foreground: Boolean): Builder {
            certManager?.appInForeground = foreground
            return this
        }

        fun addAuthentication(authInterceptor: BearerAuthInterceptor): Builder {
            // CHANGES !!!
            this.authInterceptor = authInterceptor
            orig.addInterceptor(authInterceptor)
            return this
        }

        fun build(): HttpClient {
            val trustManager = certManager ?: {
                val factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(null as KeyStore?)
                factory.trustManagers.first() as X509TrustManager
            }()

            val hostnameVerifier = certManager?.hostnameVerifier(OkHostnameVerifier)
                ?: OkHostnameVerifier

            var keyManager: KeyManager? = null
            certificateAlias?.let { alias ->
                try {
                    val context = requireNotNull(context)

                    // get provider certificate and private key
                    val certs = KeyChain.getCertificateChain(context, alias) ?: return@let
                    val key = KeyChain.getPrivateKey(context, alias) ?: return@let
                    logger.fine("Using provider certificate $alias for authentication (chain length: ${certs.size})")

                    // create Android KeyStore (performs key operations without revealing secret data to DAVx5)
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    // create KeyManager
                    keyManager = object : X509ExtendedKeyManager() {
                        override fun getServerAliases(
                            p0: String?,
                            p1: Array<out Principal>?
                        ): Array<String>? = null

                        override fun chooseServerAlias(
                            p0: String?,
                            p1: Array<out Principal>?,
                            p2: Socket?
                        ) = null

                        override fun getClientAliases(p0: String?, p1: Array<out Principal>?) =
                            arrayOf(alias)

                        override fun chooseClientAlias(
                            p0: Array<out String>?,
                            p1: Array<out Principal>?,
                            p2: Socket?
                        ) =
                            alias

                        override fun getCertificateChain(forAlias: String?) =
                            certs.takeIf { forAlias == alias }

                        override fun getPrivateKey(forAlias: String?) =
                            key.takeIf { forAlias == alias }
                    }

                    // HTTP/2 doesn't support client certificates (yet)
                    // see https://tools.ietf.org/html/draft-ietf-httpbis-http2-secondary-certs-04
                    orig.protocols(listOf(Protocol.HTTP_1_1))
                } catch (e: Exception) {
                    logger.log(
                        Level.SEVERE,
                        "Couldn't set up provider certificate authentication",
                        e
                    )
                }
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                if (keyManager != null) arrayOf(keyManager) else null,
                arrayOf(trustManager),
                null
            )
            orig.sslSocketFactory(sslContext.socketFactory, trustManager)
            orig.hostnameVerifier(hostnameVerifier)

            return HttpClient(
                orig.build(),
                certManager
            )
        }

    }


    private object UserAgentInterceptor : Interceptor {
        // use Locale.US because numbers may be encoded as non-ASCII characters in other locales
        private val userAgentDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        private val userAgentDate = userAgentDateFormat.format(
            Date(
                BuildConfig.buildTime
            )
        )
        private val userAgent =
            "${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} AOS XDAV ($userAgentDate; dav4jvm; " +
                    "okhttp/${BuildConfig.okhttpVersion}) Android/${Build.VERSION.RELEASE}"

        override fun intercept(chain: Interceptor.Chain): Response {
            val locale = Locale.getDefault()
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    userAgent
                )
                .header(
                    "Accept-Language",
                    "${locale.language}-${locale.country}, ${locale.language};q=0.7, *;q=0.5"
                )
                .build()
            return chain.proceed(request)
        }

    }

}
