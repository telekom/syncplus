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

package de.telekom.dtagsyncpluskit.davx5.model

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager

@Suppress("unused")
class Credentials(
    context: Context,
    private val account: Account,
    serviceEnvironments: ServiceEnvironments
) {
    private val accountManager = AccountManager.get(context)

    val spicaEnv = serviceEnvironments.spicaEnv
    val idmEnv = serviceEnvironments.idmEnv
    val redirectUri = serviceEnvironments.redirectUri

    var accessToken: String
        get() = accountManager.getPassword(account)
        set(value) {
            accountManager.setPassword(account, value)
        }

    var idToken: String
        get() = accountManager.getUserData(account, IDMAccountManager.KEY_ID_TOKEN)
        set(value) {
            accountManager.setUserData(account, IDMAccountManager.KEY_ID_TOKEN, value)
        }

    fun getRefreshTokenSync(): String? {
        //check(Looper.myLooper() != Looper.getMainLooper()) { "must not be run on main thread" }
        //return accountManager.blockingGetAuthToken(account, "full_access", true)

        val token = accountManager.getUserData(account, IDMAccountManager.KEY_REFRESH_TOKEN)
        if (BuildConfig.DEBUG) {
            Logger.log.info("getRefreshTokenSync() | refreshToken: $token")
        } else {
            Logger.log.info("getRefreshTokenSync()")
        }
        return token
    }

    fun setRefreshToken(refreshToken: String) {
        if (BuildConfig.DEBUG) {
            Logger.log.info("setRefreshToken() | refreshToken: $refreshToken")
        } else {
            Logger.log.info("setRefreshTokenSync()")
        }
        accountManager.setUserData(account, IDMAccountManager.KEY_REFRESH_TOKEN, refreshToken)
    }
}
