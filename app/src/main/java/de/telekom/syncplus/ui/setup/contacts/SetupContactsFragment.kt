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

package de.telekom.syncplus.ui.setup.contacts

import android.accounts.Account
import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentSetupContactsBinding
import de.telekom.syncplus.ui.dialog.CustomAlertDialog
import de.telekom.syncplus.ui.main.account.AccountsActivity
import de.telekom.syncplus.ui.setup.SetupViewModel
import de.telekom.syncplus.ui.setup.calendar.SetupCalendarFragment
import de.telekom.syncplus.ui.setup.contacts.copy.ContactsCopyActivity
import de.telekom.syncplus.ui.setup.contacts.list.ContactsActivity
import de.telekom.syncplus.ui.setup.email.SetupEmailFragment
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch
import parcelableArrayList

class SetupContactsFragment : BaseFragment(R.layout.fragment_setup_contacts) {
    override val TAG = "SETUP_CONTACTS_FRAGMENT"

    companion object {
        fun newInstance() = SetupContactsFragment()
    }

    private val binding by viewBinding(FragmentSetupContactsBinding::bind)
    private val viewModel: SetupContract.ViewModel by activityViewModels<SetupViewModel>()

    private val copyContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            Activity.RESULT_OK -> {
                viewModel.viewEvent(SetupContract.ViewEvent.TryToGoNextStep)
            }
        }
    }

    private val selectGroupsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            Activity.RESULT_OK -> {
                val results = it.data?.parcelableArrayList<Group>(ContactsActivity.EXTRA_RESULT)
                Logger.log.finest("Results: $results")
                viewModel.viewEvent(SetupContract.ViewEvent.SetSelectedGroups(results ?: listOf()))
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupTopBar()

        binding.dropdown.setOnClickListener {
            viewModel.viewEvent(SetupContract.ViewEvent.TryToSelectGroups)
        }
        binding.skipButton.setOnClickListener {
            showSkipDialog { skip ->
                if (skip) {
                    viewModel.viewEvent(SetupContract.ViewEvent.TryToGoNextStep)
                }
            }
        }
        binding.copyButton.setOnClickListener {
            viewModel.viewEvent(SetupContract.ViewEvent.TryToCopyContacts)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.action.collect(::handleAction) }
                launch { viewModel.navigation.collect(::handleNavigation) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setupTopBar()
        binding.dropdown.isEnabled = true
        binding.skipButton.isEnabled = true
        binding.copyButton.isEnabled = true
    }

    private fun handleState(state: SetupContract.State) {
        updateSelection(state.selectedGroups)
    }

    private fun handleAction(action: SetupContract.Action) {
        when (action) {
            is SetupContract.Action.CopyContacts -> copyContacts(action)
            is SetupContract.Action.SelectGroups -> selectGroups(action.selectedGroups)
            else -> Logger.log.warning("Skipped action $action")
        }
    }

    private fun handleNavigation(navigation: SetupContract.Navigation) {
        when (navigation) {
            is SetupContract.Navigation.NavigateToNextStep -> goNext(navigation)
        }
    }

    private fun selectGroups(selectedGroups: List<Group>?) {
        binding.dropdown.isEnabled = false
        val intent = ContactsActivity.newIntent(requireActivity(), selectedGroups)
        selectGroupsLauncher.launch(intent)
    }

    private fun copyContacts(action: SetupContract.Action.CopyContacts) {
        binding.copyButton.isEnabled = false

        val intent = ContactsCopyActivity.newIntent(
            requireActivity(),
            accountName = action.accountName,
            currentStep = action.currentStep,
            maxSteps = action.maxSteps,
            selectedGroups = action.selectedGroups
        )

        copyContactsLauncher.launch(intent)
    }

    private fun setupTopBar() {
        viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(2))
        viewModel.viewEvent(
            SetupContract.ViewEvent.SetupStep(
                description = getString(R.string.topbar_title_copy_contacts),
                hasBackButton = true,
                hasHelpButton = true
            )
        )
    }

    private fun showSkipDialog(l: (Boolean) -> Unit) {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_sync_confirm_title)
        dialog.text = getString(R.string.dialog_sync_confirm_text)
        dialog.cancelText = getString(R.string.button_title_back)
        dialog.successText = getString(R.string.button_title_next)
        dialog.setOnCancelListener {
            l(false)
        }
        dialog.setOnSuccessListener {
            l(true)
        }
        supportFragmentManager?.let {
            dialog.show(it, null)
        } ?: throw IllegalStateException("FragmentManager missing")
    }

    private fun updateSelection(selectedGroups: List<Group>?) {
        if (selectedGroups != null) {
            binding.copyButton.isEnabled = selectedGroups.isNotEmpty()
        }
        if (selectedGroups != null && selectedGroups.find { it.groupId == -1L } == null) {
            val string =
                selectedGroups.joinToString(
                    " - ",
                    transform = { it.name ?: "" },
                )
            binding.dropdown.text = string
        } else {
            binding.dropdown.text = getString(R.string.all_contacts)
        }
    }

    private fun goNext(action: SetupContract.Navigation.NavigateToNextStep) {
        // Sync contacts.
        val account = Account(action.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(
            requireContext(),
            account,
        )
        accountSettings.resyncContacts(true)

        when {
            action.isCalendarEnabled -> {
                push(R.id.container, SetupCalendarFragment.newInstance())
            }

            action.isEmailEnabled -> {
                // Assume the account setup is completed right away because the
                // email configuration may change outside of the application
                accountSettings.setSetupCompleted(true)
                push(R.id.container, SetupEmailFragment.newInstance())
            }

            else -> {
                accountSettings.setSetupCompleted(true)
                startActivity(
                    AccountsActivity.newIntent(
                        requireActivity(),
                        newAccountCreated = true,
                        allTypesSynced = action.isCalendarEnabled && action.isEmailEnabled && action.isContactsEnabled
                    ),
                )
            }
        }
    }
}
