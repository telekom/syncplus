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

package de.telekom.dtagsyncpluskit.model

import android.app.Application
import android.os.Parcelable
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.api.TokenStore
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import kotlinx.parcelize.Parcelize

@Parcelize
data class AuthHolder(
    var accountName: String,
    var tokenStore: TokenStore,
    var calEnabled: Boolean = true,
    var emailEnabled: Boolean = true,
    var addressBookEnabled: Boolean = true,
    var currentStep: Int = 1,
    var maxSteps: Int = 4,
    var selectedGroups: List<Group>? = null,
) : Parcelable {
    suspend fun createAccount(
        app: Application,
        serviceEnvironments: ServiceEnvironments,
    ): Credentials? {
        val alias = tokenStore.getAlias() ?: return null
        val idToken = tokenStore.getIdToken()
        val accessToken = tokenStore.getAccessToken()
        val refreshToken = tokenStore.getRefreshToken()

        // The unauthorized case can not happen.
        val am = IDMAccountManager(app.applicationContext)
        return am.createAccount(
            serviceEnvironments = serviceEnvironments,
            email = alias,
            password = refreshToken,
            loginHint = idToken,
            authToken = accessToken,
            calEnabled = calEnabled,
            cardEnabled = addressBookEnabled,
        )
    }

    fun allTypesSynced(): Boolean {
        return calEnabled && emailEnabled && addressBookEnabled
    }
}
