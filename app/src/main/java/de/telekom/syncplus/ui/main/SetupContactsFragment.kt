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
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.runOnMain
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.AccountsActivity
import de.telekom.syncplus.ContactsActivity
import de.telekom.syncplus.ContactsCopyActivity
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import de.telekom.syncplus.databinding.FragmentSetupContactsBinding
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import parcelableArrayList

class SetupContactsFragment : BaseFragment(R.layout.fragment_setup_contacts) {
    override val TAG = "SETUP_CONTACTS_FRAGMENT"

    companion object {
        fun newInstance() = SetupContactsFragment()
    }

    private val binding by viewBinding(FragmentSetupContactsBinding::bind)
    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupTopBar()
        updateSelection(authHolder.selectedGroups)
        binding.dropdown.setOnClickListener {
            binding.dropdown.isEnabled = false
            val intent = ContactsActivity.newIntent(requireActivity(), authHolder.selectedGroups)
            startActivityForResult(intent, ContactsActivity.SELECTED_ADDRESS_BOOKS)
        }
        binding.skipButton.setOnClickListener {
            showSkipDialog { skip ->
                if (skip) {
                    goNext()
                }
            }
        }
        binding.copyButton.setOnClickListener {
            binding.copyButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val topBar = (activity as TopBarActivity).topBar
                val intent = ContactsCopyActivity.newIntent(
                    requireActivity(),
                    authHolder,
                    topBar.currentStep,
                    topBar.maxSteps,
                    authHolder.selectedGroups,
                )
                runOnMain {
                    startActivityForResult(intent, ContactsCopyActivity.RESULTS)
                }
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

    private fun setupTopBar() {
        val topBar = (activity as TopBarActivity).topBar
        topBar.description = getString(R.string.topbar_title_copy_contacts)
        topBar.hasBackButton = true
        topBar.hasHelpButton = true
        topBar.setOnBackClickListener {
            finish()
        }
        topBar.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ContactsActivity.SELECTED_ADDRESS_BOOKS -> {
                if (resultCode == Activity.RESULT_OK) {
                    val results =
                        data?.parcelableArrayList<Group>(ContactsActivity.EXTRA_RESULT)
                    Logger.log.finest("Results: $results")
                    authHolder.selectedGroups = results
                    updateSelection(results)
                }
            }

            ContactsCopyActivity.RESULTS -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        goNext()
                    }

                    Activity.RESULT_CANCELED -> {
                        // Stay here.
                    }
                }
            }
        }
    }

    private fun goNext() {
        // Sync contacts.
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(
            requireContext(),
            account,
        )
        accountSettings.resyncContacts(true)

        when {
            authHolder.calEnabled -> {
                push(R.id.container, SetupCalendarFragment.newInstance())
            }

            authHolder.emailEnabled -> {
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
                        allTypesSynced = authHolder.allTypesSynced(),
                    ),
                )
            }
        }
    }
}
