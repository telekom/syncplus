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
import android.os.Handler
import de.telekom.dtagsyncpluskit.auth.IDMAuth
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val app: Application,
    private val credentials: Credentials,
    private val noTokenRefresh: Boolean = false,
    private var unauthorizedCallback: (() -> Unit)? = null
) : Interceptor {
    private val mAuth = IDMAuth(app)

    fun setUnauthorizedCallback(callback: (() -> Unit)?) {
        unauthorizedCallback = callback
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalReq = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${credentials.accessToken}")
            .build()

        if (noTokenRefresh) {
            return chain.proceed(originalReq)
        }

        var res: Response
        synchronized(app) {
            Logger.log.info("Enter Lock --->")
            res = chain.proceed(originalReq)
            if (res.code == 401) { /* unauthorized */
                Logger.log.info("401 Unauthorized: Refresh AccessToken")

                val unauthorized: (res: Response) -> Response = {
                    Handler(app.mainLooper).post {
                        unauthorizedCallback?.invoke()
                    }
                    Logger.log.info("<--- Exit Lock (401)")
                    res
                }
                val idmEnv = credentials.idmEnv
                val redirectUri = credentials.redirectUri
                val refreshToken = credentials.getRefreshTokenSync() ?: return unauthorized(res)
                val (accessToken, newRefreshToken) =
                    mAuth.getAccessTokenSync1(idmEnv, refreshToken, app)
                        ?: return unauthorized(res)
                credentials.accessToken = accessToken
                credentials.setRefreshToken(newRefreshToken)
                Logger.log.finest("!!! ACCESSTOKEN REFRESHED !!! ")

                res.close()
                val newReq = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${credentials.accessToken}")
                    .build()
                val newRes = chain.proceed(newReq)
                if (newRes.code == 401)
                    return unauthorized(newRes)

                Logger.log.info("<--- Exit Lock (~401)")
                return newRes
            }

            Logger.log.info("<--- Exit Lock (200)")
        }

        return res
    }
}
