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
import de.telekom.syncplus.AccountsActivity

class NullAuthService : Service() {

    private lateinit var accountAuthenticator: AccountAuthenticator

    override fun onCreate() {
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?) =
        accountAuthenticator.iBinder.takeIf { intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT }


    private class AccountAuthenticator(
        val context: Context
    ) : AbstractAccountAuthenticator(context) {

        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<String>?,
            options: Bundle?
        ): Bundle {
            val intent = Intent(context, AccountsActivity::class.java)
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            val bundle = Bundle()
            bundle.putParcelable(AccountManager.KEY_INTENT, intent)
            return bundle
        }

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?) =
            null

        override fun getAuthTokenLabel(p0: String?) = null
        override fun confirmCredentials(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: Bundle?
        ) = null

        override fun updateCredentials(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: String?,
            p3: Bundle?
        ) = null

        override fun getAuthToken(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: String?,
            p3: Bundle?
        ) = null

        override fun hasFeatures(
            p0: AccountAuthenticatorResponse?,
            p1: Account?,
            p2: Array<out String>?
        ) = null
    }
}
