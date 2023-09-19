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

import android.accounts.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.os.bundleOf
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import de.telekom.syncplus.LoginActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.util.EmailAccountFlagController
import de.telekom.syncplus.util.Prefs
import kotlin.concurrent.thread

class AccountAuthService : Service() {

    companion object {
        @WorkerThread
        fun cleanupAccounts(context: Context) {
            val accountManager = AccountManager.get(context)
            val accountNames = accountManager
                .getAccountsByType(context.getString(R.string.account_type))
                .map { it.name }

            // delete orphaned address book accounts
            accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))
                .map { LocalAddressBook(context, it, null) }
                .forEach {
                    try {
                        if (!accountNames.contains(it.mainAccount.name))
                            it.delete()
                    } catch (e: Exception) {
                        Logger.log.severe("Couldn't delete address book account: $e")
                        Log.e("SyncPlus", "Couldn't delete address book account", e)
                    }
                }
        }
    }

    private lateinit var accountAuthenticator: AccountAuthenticator
    private lateinit var accountManager: AccountManager
    private val onAccountUpdateListener by lazy {
        OnAccountsUpdateListener {
            thread {
                cleanupAccounts(applicationContext)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        accountAuthenticator = AccountAuthenticator(this)
        accountManager = AccountManager.get(this)
        accountManager.addOnAccountsUpdatedListener(onAccountUpdateListener, null, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        accountManager.removeOnAccountsUpdatedListener(onAccountUpdateListener)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return accountAuthenticator.iBinder.takeIf {
            intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT
        }
    }

    class AccountAuthenticator(val context: Context) : AbstractAccountAuthenticator(context) {

        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<out String>?,
            options: Bundle?
        ): Bundle {
            return if (EmailAccountFlagController.isAddAccountStarted) {
                EmailAccountFlagController.isAddAccountStarted = false
                EmailAccountFlagController.isInternalAccountSelected = true

                bundleOf()
            } else {
                val intent = Intent(context, LoginActivity::class.java)
                    .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                bundleOf(AccountManager.KEY_INTENT to intent)
            }
        }

        override fun getAuthTokenLabel(authTokenType: String?) = null

        override fun confirmCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            options: Bundle?
        ) = null

        override fun updateCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ) = null

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ) = null

        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<out String>?
        ) = null

        override fun editProperties(
            response: AccountAuthenticatorResponse?,
            accountType: String?
        ) = null
    }
}
