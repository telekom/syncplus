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

package de.telekom.syncplus

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.api.IDMEnv
import de.telekom.dtagsyncpluskit.api.TokenStore
import de.telekom.dtagsyncpluskit.auth.IDMAuth
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.runOnMain
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.ui.main.CustomAlertDialog
import kotlinx.android.synthetic.main.layout_small_topbar.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    companion object {
        private const val EXTRA_RELOGIN = "EXTRA_RELOGIN"
        private const val EXTRA_ACCOUNT = "EXTRA_ACCOUNT"
        fun newIntent(
            context: Context,
            relogin: Boolean = false,
            account: Account? = null
        ): Intent {
            val intent = Intent(context, LoginActivity::class.java)
            intent.putExtra(EXTRA_RELOGIN, relogin)
            intent.putExtra(EXTRA_ACCOUNT, account)
            return intent
        }
    }

    private val mAuth by lazy {
        IDMAuth(this)
    }

    private val mRelogin by extraNotNull<Boolean>(EXTRA_RELOGIN)
    private val mAccount by extra<Account>(EXTRA_ACCOUNT, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        topbarTitle.text = getString(R.string.activity_login_title)
        backButtonSmall.visibility = View.GONE
        startLogin(mAccount?.name)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IDMAuth.RC_AUTH && data != null) {
            try {
                mAuth.onActivityResult(requestCode, resultCode, data)
            } catch (ex: Exception) {
                Logger.log.severe("Login Error 0: $ex")
                Log.e("SyncPlus", "Login Error 0", ex)
                showLoginErrorDialog(ex.localizedMessage)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun startLogin(loginHint: String?) {
        mAuth.setErrorHandler { ex ->
            Logger.log.severe("Login Error 1: $ex")
            Log.e("SyncPlus", "Login Error 1", ex)
            showLoginErrorDialog(ex.localizedMessage)
        }
        mAuth.setSuccessHandler { store ->
            try {
                Logger.log.severe("Login Success! (${store.getAlias()})")
                if (mRelogin)
                    reloginAccount(store)
                else
                    createAccount(store)
            } catch (ex: Exception) {
                Logger.log.severe("Login Error 2: $ex")
                Log.e("SyncPlus", "Login Error 2", ex)
                showLoginErrorDialog(ex.localizedMessage)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val redirectUri = Uri.parse(getString(R.string.REDIRECT_URI))
                val env = IDMEnv.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!)
                val intent = mAuth.getLoginProcessIntent(redirectUri, loginHint, env)
                runOnMain {
                    startActivityForResult(intent, IDMAuth.RC_AUTH)
                }
            } catch (ex: Exception) {
                Logger.log.severe("Login Error 3: $ex")
                Log.e("SyncPlus", "Login Error 3", ex)
                showLoginErrorDialog(ex.localizedMessage)
            }
        }
    }

    private fun showLoginErrorDialogWithBooking() {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_login_failed_title_no_account)
        dialog.text = getString(R.string.dialog_login_failed_text_no_account)
        dialog.cancelText = getString(R.string.button_title_back)
        dialog.successText = getString(R.string.button_title_freemail_booking)
        dialog.setOnCancelListener {
            finish()
        }
        dialog.setOnSuccessListener {
            openRegistrationInWeb()
        }
        dialog.show(supportFragmentManager, "DIALOG")
    }

    private fun openRegistrationInWeb() {
        val isTablet = this.resources.getBoolean(R.bool.isTablet)
        val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        val uri = Uri.parse(environ[if (isTablet) 7 else 6])
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun showLoginErrorDialog(description: String? = null) {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_login_failed_title)
        if (BuildConfig.DEBUG)
            dialog.text = description
        else
            dialog.text = null
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.button_title_back)
        dialog.setOnSuccessListener {
            finish()
        }
        dialog.show(supportFragmentManager, "DIALOG")
    }

    private fun showLoginDuplicatedDialog() {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_login_duplicated_title)
        dialog.text = null
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.button_title_back)
        dialog.setOnSuccessListener {
            finish()
        }
        dialog.show(supportFragmentManager, "DIALOG")
    }

    private fun reloginAccount(store: TokenStore) = lifecycleScope.launch(Dispatchers.IO) func@{
        val account = mAccount ?: return@func showLoginErrorDialog()
        // Logged in account differs from old account.
        if (account.name != store.getAlias()) {
            return@func showLoginErrorDialog()
        }

        val serviceEnvironments = App.serviceEnvironments(this@LoginActivity)
        val accountSettings =
            AccountSettings(this@LoginActivity, serviceEnvironments, account, null)
        val credentials = accountSettings.getCredentials()
        credentials.idToken = store.getIdToken()
        credentials.accessToken = store.getAccessToken()
        credentials.setRefreshToken(store.getRefreshToken())

        runOnMain { finish() }
    }

    private fun createAccount(store: TokenStore) = lifecycleScope.launch(Dispatchers.IO) func@{
        val serviceEnvironments = App.serviceEnvironments(this@LoginActivity)
        val accountName = store.getAlias() ?: return@func showLoginErrorDialogWithBooking()
        val accountManager = IDMAccountManager(this@LoginActivity, null)
        if (accountManager.getAccounts().find { it.name == accountName } != null) {
            return@func showLoginDuplicatedDialog()
        }

        (application as App).inSetup = true
        val authHolder = AuthHolder(accountName, store)
        if (authHolder.createAccount(application, serviceEnvironments) == null) {
            Logger.log.severe("Error: Creating Account")
            (application as App).inSetup = false
            return@func showLoginErrorDialog("Error creating account.")
        }

        runOnMain { goNext(authHolder) }
    }

    private fun goNext(authHolder: AuthHolder) {
        val intent = SetupActivity.newIntent(this, authHolder)
        startActivity(intent)
    }
}
