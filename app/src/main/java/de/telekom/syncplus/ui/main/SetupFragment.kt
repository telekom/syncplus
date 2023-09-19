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
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import com.google.android.material.button.MaterialButton
import com.karumi.dexter.MultiplePermissionsReport
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.App
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.android.synthetic.main.fragment_setup.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SetupViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val accountManager by lazy {
        IDMAccountManager(app, DavNotificationUtils.reloginCallback(app, "authority"))
    }

    private val _showError = MutableSharedFlow<Unit>()
    val showError: SharedFlow<Unit> = _showError.asSharedFlow()
    private val _navigateToNextStep = MutableSharedFlow<Unit>()
    val navigateToNextStep: SharedFlow<Unit> = _navigateToNextStep.asSharedFlow()

    fun discoverServices(
        account: Account,
        accountSettings: AccountSettings,
        serviceEnvironments: ServiceEnvironments,
        calendarSyncEnabled: Boolean,
        contactSyncEnabled: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            accountManager.discoverServicesConfiguration(
                account,
                serviceEnvironments,
                calendarSyncEnabled,
                contactSyncEnabled
            )?.let {
                setupSettings(accountSettings, calendarSyncEnabled, contactSyncEnabled)
            } ?: _showError.emit(Unit)
        }
    }

    fun setupAccount(
        accountSettings: AccountSettings,
        calendarEnabled: Boolean,
        contactsEnabled: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Check whether services are discovered
            // Othervise settings applying would not have effect
            if (!isServicesDiscovered(accountSettings.account.name, Service.TYPE_CALDAV)) {
                // Show error in case services not discovered yet
                _showError.emit(Unit)
                return@launch
            }
            if (!isServicesDiscovered(accountSettings.account.name, Service.TYPE_CARDDAV)) {
                _showError.emit(Unit)
                return@launch
            }

            setupSettings(accountSettings, calendarEnabled, contactsEnabled)
        }
    }

    private suspend fun setupSettings(
        accountSettings: AccountSettings,
        calendarEnabled: Boolean,
        contactsEnabled: Boolean
    ) {
        /* Call setSyncAllCalendars/setSyncAllAddressBooks first! */
        accountSettings.setSyncAllCalendars(calendarEnabled)
        accountSettings.setCalendarSyncEnabled(calendarEnabled)
        accountSettings.setSyncAllAddressBooks(contactsEnabled)
        accountSettings.setContactSyncEnabled(contactsEnabled)
        _navigateToNextStep.emit(Unit)
    }

    private fun isServicesDiscovered(accountName: String, serviceType: String): Boolean {
        return db.serviceDao().getIdByAccountAndType(accountName, serviceType) != null
    }
}

class SetupFragment : BaseFragment() {
    override val TAG = "SETUP_FRAGMENT"

    companion object {
        fun newInstance() = SetupFragment()
    }

    private val viewModel by activityViewModels<SetupViewModel>()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTopBar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.showError.collect { showErrorDialog() } }
                launch { viewModel.navigateToNextStep.collect { navigateToNextStep() } }
            }
        }

        childFragmentManager.setFragmentResultListener(
            ServiceDiscoveryErrorDialog.ACTION_RETRY_SERVICE_DISCOVERY,
            viewLifecycleOwner
        ) { _, _ -> discoverServices() }
    }

    private fun discoverServices() {
        val account = getAccount()
        viewModel.discoverServices(
            account,
            getAccountSettings(account),
            App.serviceEnvironments(requireContext()),
            mAuthHolder.calEnabled,
            mAuthHolder.addressBookEnabled
        )
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

        requestPermissions(*permissions) { granted, error, report ->
            when {
                error != null -> {
                    Log.e("SyncPlus", "Error: Requesting Permission: ${error.message}")
                    Logger.log.severe("Error: Requesting Permission: ${error.message}")
                    showPermissionDeniedDialog(report)
                }
                granted -> {
                    setupAccount()
                }
                else -> {
                    showPermissionDeniedDialog(report)
                }
            }
        }
    }

    private fun navigateToNextStep() {
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
        push(R.id.container, fragment)
    }

    private fun showErrorDialog() {
        ServiceDiscoveryErrorDialog.instantiate()
            .show(childFragmentManager, "ServiceDiscoveryErrorDialog")
    }

    private fun getAccount(): Account {
        return Account(mAuthHolder.accountName, getString(R.string.account_type))
    }

    private fun getAccountSettings(account: Account): AccountSettings {
        return AccountSettings(
            requireContext(),
            App.serviceEnvironments(requireContext()),
            account,
            DavNotificationUtils.reloginCallback(requireContext(), "authority")
        )
    }

    private fun setupAccount() {
        val account = getAccount()
        val accountSettings = getAccountSettings(account)
        viewModel.setupAccount(
            accountSettings,
            mAuthHolder.calEnabled,
            mAuthHolder.addressBookEnabled
        )
        // Don't sync, yet. Sync will be initiated after each setup step.
        //accountSettings.resyncCalendars(true)
        //accountSettings.resyncContacts(true)
    }

    private fun showPermissionDeniedDialog(report: MultiplePermissionsReport?) {
        val (title, text) = when (report) {
            null -> Pair(
                getString(R.string.dialog_permission_required_title),
                getString(R.string.dialog_permission_required_text)
            )
            else -> {
                val denied = report.deniedPermissionResponses
                val deniedAddressBook =
                    denied.find { e -> e.permissionName == Manifest.permission.READ_CONTACTS } != null
                val deniedCalendar =
                    denied.find { e -> e.permissionName == Manifest.permission.READ_CALENDAR } != null
                when {
                    deniedAddressBook && deniedCalendar -> Pair(
                        getString(R.string.dialog_permission_required_title),
                        getString(R.string.dialog_permission_required_text)
                    )
                    deniedAddressBook -> Pair(
                        getString(R.string.dialog_contacts_permission_required_title),
                        getString(R.string.dialog_contacts_permission_required_text)
                    )
                    deniedCalendar -> Pair(
                        getString(R.string.dialog_calendar_permission_required_title),
                        getString(R.string.dialog_calendar_permission_required_text)
                    )
                    else -> Pair(
                        getString(R.string.dialog_permission_required_title),
                        getString(R.string.dialog_permission_required_text)
                    )
                }
            }
        }
        val dialog = CustomAlertDialog()
        dialog.title = title
        dialog.text = text
        dialog.hasCancelButton = false
        dialog.successText = getString(R.string.button_title_ok)
        dialog.show(parentFragmentManager, "DIALOG")
    }

    class ToggleChanged(
        private val activity: SetupActivity,
        private val calendarSwitch: SwitchCompat,
        private val emailSwitch: SwitchCompat,
        private val addressBookSwitch: SwitchCompat,
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
