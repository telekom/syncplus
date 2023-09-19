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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.telekom.dtagsyncpluskit.davx5.syncadapter.SyncHolder
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.ui.main.CustomAlertDialog
import kotlinx.android.synthetic.main.layout_small_topbar.*
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

    private val mRelogin by extraNotNull(EXTRA_RELOGIN, false)
    private val mAccount by extra<Account>(EXTRA_ACCOUNT, null)

    private val viewModel by viewModels<LoginViewModel>()

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.processAuthResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        topbarTitle.text = getString(R.string.activity_login_title)
        backButtonSmall.visibility = View.GONE

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::handleAction) }
            }
        }

        if (savedInstanceState == null) {
            viewModel.startLogin(mAccount, mRelogin, mAccount?.name)
        }
    }

    override fun onDestroy() {
        // Login flow is finished or cancelled - allow syncing
        SyncHolder.setSyncAllowed(true)
        super.onDestroy()
    }

    private fun handleAction(action: LoginViewModel.Action) {
        when (action) {
            LoginViewModel.Action.Finish -> finish()
            is LoginViewModel.Action.NavigateNext -> goNext(action.authHolder)
            LoginViewModel.Action.ShowLoginDuplicated -> showLoginDuplicatedDialog()
            is LoginViewModel.Action.ShowLoginError -> showLoginErrorDialog(action.message)
            LoginViewModel.Action.ShowLoginErrorWithBooking -> showLoginErrorDialogWithBooking()
            is LoginViewModel.Action.StartLogIn -> authLauncher.launch(action.intent)
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
        dialog.text = if (BuildConfig.DEBUG) description else null
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

    private fun goNext(authHolder: AuthHolder) {
        val intent = SetupActivity.newIntent(this, authHolder)
        startActivity(intent)
    }
}
