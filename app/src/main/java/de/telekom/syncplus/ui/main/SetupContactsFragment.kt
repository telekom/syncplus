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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.runOnMain
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.*
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.android.synthetic.main.fragment_setup_contacts.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SetupContactsFragment : BaseFragment() {
    override val TAG = "SETUP_CONTACTS_FRAGMENT"

    companion object {
        fun newInstance() = SetupContactsFragment()
    }

    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_setup_contacts, container, false)
        setupTopBar()
        updateSelection(v, authHolder.selectedGroups)
        v.dropdown.setOnClickListener {
            v.dropdown.isEnabled = false
            val intent = ContactsActivity.newIntent(requireActivity(), authHolder.selectedGroups)
            startActivityForResult(intent, ContactsActivity.SELECTED_ADDRESS_BOOKS)
        }
        v.skipButton.setOnClickListener {
            showSkipDialog { skip ->
                if (skip) {
                    goNext()
                }
            }
        }
        v.copyButton.setOnClickListener {
            v.copyButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val topBar = (activity as TopBarActivity).topBar
                val intent = ContactsCopyActivity.newIntent(
                    requireActivity(),
                    authHolder,
                    topBar.currentStep,
                    topBar.maxSteps,
                    authHolder.selectedGroups
                )
                runOnMain {
                    startActivityForResult(intent, ContactsCopyActivity.RESULTS)
                }
            }
        }
        return v
    }

    override fun onStart() {
        super.onStart()
        setupTopBar()
        view?.dropdown?.isEnabled = true
        view?.skipButton?.isEnabled = true
        view?.copyButton?.isEnabled = true
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

    private fun updateSelection(view: View?, selectedGroups: List<Group>?) {
        if (selectedGroups != null)
            view?.copyButton?.isEnabled = selectedGroups.count() > 0
        if (selectedGroups != null && selectedGroups.find { it.groupId == -1L } == null) {
            val string = selectedGroups.joinToString(
                " - ",
                transform = { it.name ?: "" }
            )
            view?.dropdown?.text = string
        } else {
            view?.dropdown?.text = getString(R.string.all_contacts)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ContactsActivity.SELECTED_ADDRESS_BOOKS -> {
                if (resultCode == Activity.RESULT_OK) {
                    val results =
                        data?.getParcelableArrayListExtra<Group>(ContactsActivity.EXTRA_RESULT)
                    Logger.log.finest("Results: $results")
                    authHolder.selectedGroups = results
                    updateSelection(view, results)
                }
            }
            ContactsCopyActivity.RESULTS -> {
                if (resultCode == Activity.RESULT_OK) {
                    goNext()
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // Stay here.
                }
            }
        }
    }

    private fun goNext() {
        // Sync contacts.
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings =
            AccountSettings(
                requireContext(),
                App.serviceEnvironments(requireContext()),
                account,
                DavNotificationUtils.reloginCallback(requireContext(), "authority")
            )
        accountSettings.resyncContacts(true)

        when {
            authHolder.calEnabled -> {
                push(R.id.container, SetupCalendarFragment.newInstance())
            }
            authHolder.emailEnabled -> {
                push(R.id.container, SetupEmailFragment.newInstance())
            }
            else -> {
                accountSettings.setSetupCompleted(true)
                startActivity(AccountsActivity.newIntent(requireActivity(), true))
            }
        }
    }
}
