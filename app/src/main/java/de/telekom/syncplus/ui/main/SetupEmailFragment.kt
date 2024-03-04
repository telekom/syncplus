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
import android.view.View
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.openPlayStore
import de.telekom.syncplus.AccountsActivity
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import de.telekom.syncplus.databinding.FragmentSetupEmailBinding
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.viewbinding.viewBinding

class SetupEmailFragment : BaseFragment(R.layout.fragment_setup_email) {
    override val TAG = "SETUP_EMAIL_FRAGMENT"

    companion object {
        fun newInstance() = SetupEmailFragment()

        private const val PRE_INSTALLED_APPS_RESULT_CODE = 4210
        private const val PLAY_STORE_RESULT_CODE = 2104
    }

    private val binding by viewBinding(FragmentSetupEmailBinding::bind)
    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.downloadMailAppLayout.setOnClickListener {
            openPlayStore(
                this,
                "de.telekom.mail",
                PLAY_STORE_RESULT_CODE,
            )
        }
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
//        EmailAccountFlagController.isAddAccountStarted = false
//        EmailAccountFlagController.isInternalAccountSelected = false
        super.onDestroy()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
//        EmailAccountFlagController.isAddAccountStarted = false

        when (requestCode) {
//            PRE_INSTALLED_APPS_RESULT_CODE -> updateAccounts(resultCode)
            PLAY_STORE_RESULT_CODE -> goNext()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun goNext() {
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(requireContext(), account)
        accountSettings.setSetupCompleted(true)

        startActivity(
            AccountsActivity.newIntent(
                requireActivity(),
                newAccountCreated = true,
                allTypesSynced = authHolder.allTypesSynced(),
            ),
        )
    }
}
