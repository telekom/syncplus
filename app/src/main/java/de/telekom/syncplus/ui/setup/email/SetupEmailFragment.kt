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

package de.telekom.syncplus.ui.setup.email

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.openPlayStore
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentSetupEmailBinding
import de.telekom.syncplus.ui.main.account.AccountsActivity
import de.telekom.syncplus.ui.setup.SetupViewModel
import de.telekom.syncplus.ui.setup.contacts.SetupContract
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class SetupEmailFragment : BaseFragment(R.layout.fragment_setup_email) {
    override val TAG = "SETUP_EMAIL_FRAGMENT"

    companion object {
        fun newInstance() = SetupEmailFragment()

        private const val PRE_INSTALLED_APPS_RESULT_CODE = 4210
        private const val PLAY_STORE_RESULT_CODE = 2104
    }

    private val binding by viewBinding(FragmentSetupEmailBinding::bind)
    private val viewModel by activityViewModels<SetupViewModel>()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.navigation.collect(::handleAction) }
            }
        }

        viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(4))
    }

    override fun onStart() {
        super.onStart()
        viewModel.viewEvent(
            SetupContract.ViewEvent.SetupStep(
                description = getString(R.string.topbar_title_email),
                hasBackButton = true,
                hasHelpButton = false
            )
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
//        EmailAccountFlagController.isAddAccountStarted = false

        when (requestCode) {
//            PRE_INSTALLED_APPS_RESULT_CODE -> updateAccounts(resultCode)
            PLAY_STORE_RESULT_CODE -> viewModel.viewEvent(SetupContract.ViewEvent.TryToGoNextStep)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleAction(action: SetupContract.Navigation) {
        when (action) {
            is SetupContract.Navigation.NavigateToNextStep -> goNext(action)
            else -> Logger.log.warning("Skipped action $action")
        }
    }

    private fun goNext(action: SetupContract.Navigation.NavigateToNextStep) {
        val account = Account(action.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(requireContext(), account)
        accountSettings.setSetupCompleted(true)

        startActivity(
            AccountsActivity.newIntent(
                requireActivity(),
                newAccountCreated = true,
                allTypesSynced = action.isEmailEnabled && action.isContactsEnabled && action.isCalendarEnabled,
            ),
        )
    }
}
