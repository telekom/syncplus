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

package de.telekom.syncplus.ui.main

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.openPlayStore
import de.telekom.syncplus.*
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.AccountObserver
import de.telekom.syncplus.util.AccountObserverDelegate
import de.telekom.syncplus.util.EmailAccountFlagController
import kotlinx.android.synthetic.main.fragment_setup_email.view.*

class SetupEmailFragment : BaseFragment(), AccountObserver by AccountObserverDelegate() {
    override val TAG = "SETUP_EMAIL_FRAGMENT"

    companion object {
        fun newInstance() = SetupEmailFragment()
        private const val PRE_INSTALLED_APPS_RESULT_CODE = 4210
        private const val PLAY_STORE_RESULT_CODE = 2104
    }

    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_setup_email, container, false)
        v.downloadMailAppLayout.setOnClickListener {
            openPlayStore(
                this,
                "de.telekom.mail", PLAY_STORE_RESULT_CODE
            )
        }
        v.preinstalledMailAppLayout.setOnClickListener {
            EmailAccountFlagController.initiateAddAccount()
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT)
            startActivityForResult(intent, PRE_INSTALLED_APPS_RESULT_CODE)
        }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init(this)
    }

    override fun onStart() {
        super.onStart()
        val topBar = (activity as? TopBarActivity)?.topBar
        topBar?.currentStep = authHolder.currentStep
        topBar?.maxSteps = authHolder.maxSteps
        topBar?.description = getString(R.string.topbar_title_email)
        topBar?.hasBackButton = true
        topBar?.hasBackButton = true
        topBar?.setOnBackClickListener { finish() }
        topBar?.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
    }

    override fun onDestroy() {
        EmailAccountFlagController.isAddAccountStarted = false
        EmailAccountFlagController.isInternalAccountSelected = false
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        EmailAccountFlagController.isAddAccountStarted = false

        when (requestCode) {
            PRE_INSTALLED_APPS_RESULT_CODE -> updateAccounts(resultCode)
            PLAY_STORE_RESULT_CODE -> goNext()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateAccounts(resultCode: Int) {
        val accountSet = getAddedAccounts()
        Logger.log.info("[Account adding] returned with result code $resultCode")
        Logger.log.info("[Account adding] accounts changed - ${accountSet.isNotEmpty()}")

        if (BuildConfig.DEBUG) {
            Logger.log.info("[Account adding] accounts added - ${accountSet.map { it.name to it.type }}")
        }

        // There were attempts to add an internal account - showing error
        Logger.log.info("There were attempts to add an internal account - showing error")
        if (EmailAccountFlagController.isInternalAccountSelected) {
            EmailAccountFlagController.isInternalAccountSelected = false
            showErrorDialog()
            return
        }

        val addedAccount = accountSet.lastOrNull()
        if (addedAccount != null) {
            Logger.log.info("Account is added - handling")
            handleAccountAdded(addedAccount)
        } else if (EmailAccountFlagController.isTimeoutExceed()) {
            Logger.log.info("Account is not added but timeout exceeded - go next")
            goNext()
        } else {
            Logger.log.info("Account is not added - show error")
            showErrorDialog()
        }
    }

    private fun handleAccountAdded(account: Account) {
        if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches() ||
            EmailAccountFlagController.isWhitelisted(account.type)
        ) {
            // Added account is an email account - go next
            Logger.log.info("Added account is an email account - go next")
            goNext()
        } else if (EmailAccountFlagController.isTimeoutExceed()) {
            Logger.log.info("Added account is not an email account but timeout exceeded - go next")
            goNext()
        } else {
            // Added account is not an email account - show error dialog
            Logger.log.info("Added account is not an email account - show error dialog")
            showErrorDialog()
        }
    }

    private fun showErrorDialog() {
        EmailSetupErrorDialog.instantiate()
            .show(childFragmentManager, "EmailSetupErrorDialog")
    }

    private fun goNext() {
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(
            requireContext(),
            App.serviceEnvironments(requireContext()),
            account,
            DavNotificationUtils.reloginCallback(requireContext(), "authority")
        )
        accountSettings.setSetupCompleted(true)

        startActivity(
            AccountsActivity.newIntent(
                requireActivity(),
                newAccountCreated = true,
                allTypesSynced = authHolder.allTypesSynced(),
            )
        )
    }
}
