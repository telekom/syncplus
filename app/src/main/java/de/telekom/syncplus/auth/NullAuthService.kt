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

package de.telekom.syncplus.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import de.telekom.syncplus.ui.main.account.AccountsActivity

class NullAuthService : Service() {
    private lateinit var accountAuthenticator: AccountAuthenticator

    override fun onCreate() {
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?) =
        accountAuthenticator.iBinder.takeIf { intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT }

    private class AccountAuthenticator(
        val context: Context,
    ) : AbstractAccountAuthenticator(context) {
        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<String>?,
            options: Bundle?,
        ): Bundle {
            // TODO: Remove commented code after successful tests
//            return if (EmailAccountFlagController.isAddAccountStarted) {
//                EmailAccountFlagController.isAddAccountStarted = false
//                EmailAccountFlagController.isInternalAccountSelected = true
//
//                bundleOf()
//            } else {
            val intent =
                Intent(context, AccountsActivity::class.java)
                    .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            return bundleOf(AccountManager.KEY_INTENT to intent)
//            }
        }

        override fun editProperties(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
        ) = null

        override fun getAuthTokenLabel(p0: String?) = null

        override fun confirmCredentials(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: Bundle?,
        ) = null

        override fun updateCredentials(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: String?,
            p3: Bundle?,
        ) = null

        override fun getAuthToken(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: String?,
            p3: Bundle?,
        ) = null

        override fun hasFeatures(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: Array<out String>?,
        ) = null
    }
}
