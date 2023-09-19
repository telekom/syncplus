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

import android.content.Context
import android.os.Handler
import at.bitfire.dav4jvm.exception.UnauthorizedException
import de.telekom.dtagsyncpluskit.auth.IDMAuth
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val context: Context,
    private val credentials: Credentials,
    private val noTokenRefresh: Boolean = false,
    private var unauthorizedCallback: (() -> Unit)? = null
) : Interceptor {
    private val mAuth = IDMAuth(context)

    fun setUnauthorizedCallback(callback: (() -> Unit)?) {
        unauthorizedCallback = callback
    }

    private fun buildUnauthorized(cb: (() -> Unit)? = null): Response {
        Handler(context.mainLooper).post {
            unauthorizedCallback?.invoke()
        }
        cb?.invoke()
        return Response.Builder()
            .code(401)
            .build()
    }

    private fun forwardUnauthorized(res: Response, cb: (() -> Unit)? = null): Response {
        Handler(context.mainLooper).post {
            unauthorizedCallback?.invoke()
        }
        cb?.invoke()
        CountlyWrapper.recordHandledException(UnauthorizedException("Spica: 401 Unauthorized"))
        return res
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        Logger.log.info("intercept()")

        synchronized(BearerAuthInterceptor::class.java) {
            Logger.log.info("Enter Lock --->")

            val accessToken = credentials.accessToken ?: return buildUnauthorized {
                Logger.log.info("<--- Exit Lock (accessToken NULL)")
            }
            val originalReq = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            if (noTokenRefresh) {
                Logger.log.info("<--- Exit Lock")
                return chain.proceed(originalReq)
            }

            val res: Response = chain.proceed(originalReq)
            if (res.code == 401) { /* unauthorized */
                Logger.log.info("401 Unauthorized: Refresh AccessToken")
                val idmEnv = credentials.idmEnv
                val refreshToken =
                    credentials.getRefreshTokenSync() ?: return forwardUnauthorized(res) {
                        Logger.log.info("<--- Exit Lock (401)")
                    }
                val (newAccessToken, newRefreshToken) =
                    mAuth.getAccessTokenSync1(idmEnv, refreshToken, context)
                        ?: return forwardUnauthorized(res) {
                            // We've been getting back an error,
                            // reset accessToken and refreshToken to null.
                            credentials.accessToken = null
                            credentials.setRefreshToken(null)
                            Logger.log.info("<--- Exit Lock (401)")
                        }
                credentials.accessToken = newAccessToken
                credentials.setRefreshToken(newRefreshToken)
                Logger.log.finest("!!! ACCESSTOKEN REFRESHED !!! ")

                res.close()
                val newReq = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${credentials.accessToken}")
                    .build()
                val newRes = chain.proceed(newReq)
                if (newRes.code == 401) {
                    return forwardUnauthorized(newRes) {
                        Logger.log.info("<--- Exit Lock (401)")
                    }
                }
                Logger.log.info("<--- Exit Lock (~401)")
                return newRes
            }

            Logger.log.info("<--- Exit Lock (200)")
            return res
        }
    }
}
