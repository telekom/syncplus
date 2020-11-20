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

import android.Manifest
import android.accounts.Account
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Switch
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.App
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.android.synthetic.main.fragment_setup.view.*
import kotlinx.coroutines.launch

class SetupFragment : BaseFragment() {
    override val TAG = "SETUP_FRAGMENT"

    companion object {
        fun newInstance() = SetupFragment()
    }

    private lateinit var mToggleChangedListener: ToggleChanged
    private val mAuthHolder by lazy {
        (requireActivity() as SetupActivity).authHolder
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_setup, container, false)
        if (savedInstanceState != null) {
            (activity as SetupActivity).authHolder.currentStep = 2
        }
        setupTopBar()
        mToggleChangedListener = ToggleChanged(
            requireActivity() as SetupActivity,
            v.calendarSwitch,
            v.emailSwitch,
            v.addressBookSwitch,
            v.nextButton
        )

        v.calendarSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            mToggleChangedListener.onCheckedChanged(buttonView, isChecked)
        }
        v.emailSwitch.setOnCheckedChangeListener(mToggleChangedListener)
        v.addressBookSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            mToggleChangedListener.onCheckedChanged(buttonView, isChecked)
        }
        v.nextButton.setOnClickListener {
            goNext()
        }

        return v
    }

    override fun onStart() {
        super.onStart()
        setupTopBar()
    }

    private fun setupTopBar() {
        val topBar = (activity as TopBarActivity).topBar
        val authHolder = (activity as SetupActivity).authHolder
        topBar.currentStep = authHolder.currentStep
        topBar.maxSteps = authHolder.maxSteps
        topBar.description = getString(R.string.topbar_title_setup)
        topBar.hasBackButton = false
        topBar.hasHelpButton = true
        topBar.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
    }

    private fun goNext() {
        val fragment = when {
            mAuthHolder.addressBookEnabled -> {
                SetupContactsFragment.newInstance()
            }
            mAuthHolder.calEnabled -> {
                SetupCalendarFragment.newInstance()
            }
            mAuthHolder.emailEnabled -> {
                SetupEmailFragment.newInstance()
            }
            else -> {
                return
            }
        }

        val permissions = when {
            mAuthHolder.addressBookEnabled && mAuthHolder.calEnabled -> {
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            }
            mAuthHolder.addressBookEnabled -> arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
            mAuthHolder.calEnabled -> arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            else -> arrayOf()
        }

        requestPermissions(*permissions) { granted, error ->
            when {
                error != null -> {
                    Log.e("SyncPlus", "Error: Requesting Permission: ${error.message}")
                    Logger.log.severe("Error: Requesting Permission: ${error.message}")
                    showPermissionDeniedDialog()
                }
                granted -> {
                    setupAccount()
                    push(R.id.container, fragment)
                }
                else -> {
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private fun setupAccount() = lifecycleScope.launch {
        val account = Account(mAuthHolder.accountName, getString(R.string.account_type))
        val accountSettings =
            AccountSettings(
                requireContext(),
                App.serviceEnvironments(requireContext()),
                account,
                DavNotificationUtils.reloginCallback(requireContext(), "authority")
            )

        /* Call setSyncAllCalendars/setSyncAllAddressBooks first! */
        accountSettings.setSyncAllCalendars(mAuthHolder.calEnabled)
        accountSettings.setSyncAllAddressBooks(mAuthHolder.addressBookEnabled)
        accountSettings.setCalendarSyncEnabled(mAuthHolder.calEnabled)
        accountSettings.setContactSyncEnabled(mAuthHolder.addressBookEnabled)
        // Don't sync, yet. Sync will be initiated after each setup step.
        //accountSettings.resyncCalendars(true)
        //accountSettings.resyncContacts(true)
    }

    private fun showPermissionDeniedDialog() {
        val dialog = CustomAlertDialog()
        dialog.title = getString(R.string.dialog_permission_required_title)
        dialog.text = getString(R.string.dialog_permission_required_text)
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.button_title_ok)
        dialog.show(parentFragmentManager, "DIALOG")
    }

    class ToggleChanged(
        private val activity: SetupActivity,
        private val calendarSwitch: Switch,
        private val emailSwitch: Switch,
        private val addressBookSwitch: Switch,
        private val nextButton: MaterialButton
    ) : CompoundButton.OnCheckedChangeListener {

        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            nextButton.isEnabled =
                calendarSwitch.isChecked || emailSwitch.isChecked || addressBookSwitch.isChecked

            // Update AuthHolder with new toggle values.
            val authHolder = activity.authHolder
            authHolder.calEnabled = calendarSwitch.isChecked
            authHolder.emailEnabled = emailSwitch.isChecked
            authHolder.addressBookEnabled = addressBookSwitch.isChecked
            when {
                (calendarSwitch.isChecked && !emailSwitch.isChecked && !addressBookSwitch.isChecked)
                        || (!calendarSwitch.isChecked && emailSwitch.isChecked && !addressBookSwitch.isChecked)
                        || (!calendarSwitch.isChecked && !emailSwitch.isChecked && addressBookSwitch.isChecked) -> {
                    authHolder.currentStep = 1
                    authHolder.maxSteps = 2
                }
                (calendarSwitch.isChecked && emailSwitch.isChecked && !addressBookSwitch.isChecked)
                        || (!calendarSwitch.isChecked && emailSwitch.isChecked && addressBookSwitch.isChecked)
                        || (calendarSwitch.isChecked && !emailSwitch.isChecked && addressBookSwitch.isChecked) -> {
                    authHolder.currentStep = 1
                    authHolder.maxSteps = 3
                }
                !calendarSwitch.isChecked && !emailSwitch.isChecked && !addressBookSwitch.isChecked -> {
                    authHolder.currentStep = 0
                    authHolder.maxSteps = 0
                }
                else -> {
                    authHolder.currentStep = 1
                    authHolder.maxSteps = 4
                }
            }

            // Update topbar to reflect selection.
            val topBar = (activity as TopBarActivity).topBar
            topBar.currentStep = authHolder.currentStep
            topBar.maxSteps = authHolder.maxSteps
            topBar.progress = authHolder.currentStep.toFloat() / authHolder.maxSteps.toFloat()
        }
    }
}
