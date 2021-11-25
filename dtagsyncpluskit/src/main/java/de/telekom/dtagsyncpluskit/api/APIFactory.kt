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

package de.telekom.dtagsyncpluskit.api

import android.app.Application
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit

object APIFactory {
    private fun httpClientBuilder(): OkHttpClient.Builder {
        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256)
            .build()
        val builder = OkHttpClient().newBuilder()
        builder
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionSpecs(Collections.singletonList(connectionSpec))

        /* Add custom UserAgent */
        val userAgent = "${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} AOS REST"
        builder.addNetworkInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    userAgent
                )
                .build()
            chain.proceed(req)
        }

        /* Add the logging interceptor last. */
        if (BuildConfig.DEBUG) {
            val logger: java.util.logging.Logger = Logger.log
            val loggingInterceptor = HttpLoggingInterceptor(
                object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        logger.finest(message)
                    }
                })
            // Temporary, log all headers.
            //loggingInterceptor.redactHeader("Authorization")
            //loggingInterceptor.redactHeader("Cookie")
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(loggingInterceptor)
        }

        return builder
    }

    private fun retrofitClient(
        baseUrl: String,
        httpClient: OkHttpClient
    ): Retrofit {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .client(httpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    fun spicaAPI(
        app: Application,
        credentials: Credentials
    ): SpicaAPI {
        val builder = httpClientBuilder()
        builder.addInterceptor(BearerAuthInterceptor(app, credentials))
        return retrofitClient(
            credentials.spicaEnv.baseUrl,
            builder.build()
        ).create(SpicaAPI::class.java)
    }

    fun idmAPI(env: IDMEnv): IDMAPI {
        val client = httpClientBuilder()
        return retrofitClient(env.baseUrl, client.build()).create(IDMAPI::class.java)
    }
}
