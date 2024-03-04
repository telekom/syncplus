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

import android.os.Parcelable
import com.auth0.android.jwt.JWT
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.lang.IllegalStateException

@Parcelize
class TokenStore(
    private var accessToken: String?,
    private var idToken: String?,
    private var refreshToken: String?,
) : Parcelable {
    @IgnoredOnParcel
    private val mJWT by lazy {
        try {
            if (idToken != null) {
                JWT(idToken!!)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getAccessToken(): String {
        return accessToken ?: throw IllegalStateException("No AccessToken set")
    }

    fun getIdToken(): String {
        return idToken ?: throw IllegalStateException("No IdToken set")
    }

    fun getRefreshToken(): String {
        return refreshToken ?: throw IllegalStateException("No RefreshToken set")
    }

    fun getAlias(): String? {
        val alias = mJWT?.getClaim("urn:telekom.com:alia")?.asString()
        val domain = mJWT?.getClaim("urn:telekom.com:domt")?.asString()
        val email = mJWT?.getClaim("urn:telekom.com:mainEmail")?.asString()
        return when {
            email != null -> email
            domain != null && alias != null -> "$alias@$domain"
            else -> null
        }
    }

    fun setAccessToken(token: String) {
        accessToken = token
    }
}
