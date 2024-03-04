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

package de.telekom.syncplus.ui.setup

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.karumi.dexter.MultiplePermissionsReport
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentSetupBinding
import de.telekom.syncplus.ui.dialog.CustomAlertDialog
import de.telekom.syncplus.ui.dialog.ServiceDiscoveryErrorDialog
import de.telekom.syncplus.ui.setup.calendar.SetupCalendarFragment
import de.telekom.syncplus.ui.setup.contacts.SetupContactsFragment
import de.telekom.syncplus.ui.setup.contacts.SetupContract
import de.telekom.syncplus.ui.setup.email.SetupEmailFragment
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class SetupSyncEntitiesFragment : BaseFragment(R.layout.fragment_setup) {
    override val TAG = "SETUP_FRAGMENT"

    companion object {
        fun newInstance() = SetupSyncEntitiesFragment()
    }

    private val binding by viewBinding(FragmentSetupBinding::bind)
    private val viewModel by activityViewModels<SetupViewModel>()
    private var mToggleChangedListener: ToggleChanged? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTopBar()
//
//        if (savedInstanceState != null) {
////            (activity as SetupActivity).authHolder.currentStep = 2
//
//            viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(2))
//        }
        if (savedInstanceState == null) {
            viewModel.viewEvent(SetupContract.ViewEvent.SetMaxSteps(4))
        }
        viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(1))

        mToggleChangedListener = ToggleChanged(
            requireActivity() as SetupActivity,
            binding.calendarSwitch,
            binding.emailSwitch,
            binding.addressBookSwitch,
            binding.nextButton
        )

        binding.calendarSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            mToggleChangedListener?.onCheckedChanged(buttonView, isChecked)
        }
        binding.emailSwitch.setOnCheckedChangeListener(mToggleChangedListener)
        binding.addressBookSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            mToggleChangedListener?.onCheckedChanged(buttonView, isChecked)
        }
        binding.nextButton.setOnClickListener {
            viewModel.viewEvent(SetupContract.ViewEvent.CheckPermissions)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.action.collect(::handleAction) }
                launch { viewModel.navigation.collect(::handleNavigation) }
            }
        }

        childFragmentManager.setFragmentResultListener(
            ServiceDiscoveryErrorDialog.ACTION_RETRY_SERVICE_DISCOVERY,
            viewLifecycleOwner,
        ) { _, _ -> viewModel.viewEvent(SetupContract.ViewEvent.DiscoverServices()) }
    }

    override fun onResume() {
        super.onResume()
        cancelPermissionNotification()
    }

    private fun setupTopBar() {
        viewModel.viewEvent(
            SetupContract.ViewEvent.SetupStep(
                description = getString(R.string.topbar_title_setup),
                hasBackButton = false,
                hasHelpButton = true
            )
        )
    }

    private fun handleState(state: SetupContract.State) {
        binding.emailSwitch.isChecked = state.isEmailEnabled
        binding.calendarSwitch.isChecked = state.isCalendarEnabled
        binding.addressBookSwitch.isChecked = state.isContactsEnabled
    }

    private fun handleAction(action: SetupContract.Action) {
        when (action) {
            is SetupContract.Action.TryRequestPermissions -> tryRequestPermissions(action)
            SetupContract.Action.ShowError -> showErrorDialog()
            else -> Logger.log.warning("Skipped action $action")
        }
    }

    private fun handleNavigation(navigation: SetupContract.Navigation) {
        when (navigation) {
            is SetupContract.Navigation.NavigateToNextStep -> goNext(navigation)
        }
    }

    private fun tryRequestPermissions(action: SetupContract.Action.TryRequestPermissions) {
        val permissions = when {
            action.isContactsEnabled && action.isCalendarEnabled -> {
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
                )
            }

            action.isContactsEnabled ->
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                )

            action.isCalendarEnabled ->
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
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

    private fun goNext(action: SetupContract.Navigation.NavigateToNextStep) {
        // Cancel notification if it's being disaplyed
        cancelPermissionNotification()

        val fragment = when {
            action.isContactsEnabled -> {
                SetupContactsFragment.newInstance()
            }

            action.isCalendarEnabled -> {
                SetupCalendarFragment.newInstance()
            }

            action.isEmailEnabled -> {
                SetupEmailFragment.newInstance()
            }

            else -> {
                return
            }
        }
        push(R.id.container, fragment)
    }

    // No need to display permission notification during account setup flow
    private fun cancelPermissionNotification() {
        NotificationManagerCompat.from(requireContext())
            .cancel(NotificationUtils.NOTIFY_PERMISSIONS)
    }

    private fun showErrorDialog() {
        ServiceDiscoveryErrorDialog.instantiate()
            .show(childFragmentManager, "ServiceDiscoveryErrorDialog")
    }

    private fun setupAccount() {
        viewModel.viewEvent(SetupContract.ViewEvent.SetupAccount)

        // Don't sync, yet. Sync will be initiated after each setup step.
        // accountSettings.resyncCalendars(true)
        // accountSettings.resyncContacts(true)
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

    private inner class ToggleChanged(
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
            viewModel.viewEvent(SetupContract.ViewEvent.SetCalendarEnabled(calendarSwitch.isChecked))
            viewModel.viewEvent(SetupContract.ViewEvent.SetEmailEnabled(emailSwitch.isChecked))
            viewModel.viewEvent(SetupContract.ViewEvent.SetContactsEnabled(addressBookSwitch.isChecked))
            when {
                (calendarSwitch.isChecked && !emailSwitch.isChecked && !addressBookSwitch.isChecked) ||
                        (!calendarSwitch.isChecked && emailSwitch.isChecked && !addressBookSwitch.isChecked) ||
                        (!calendarSwitch.isChecked && !emailSwitch.isChecked && addressBookSwitch.isChecked) -> {
                    viewModel.viewEvent(SetupContract.ViewEvent.SetMaxSteps(2))
                    viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(1))
                }

                (calendarSwitch.isChecked && emailSwitch.isChecked && !addressBookSwitch.isChecked) ||
                        (!calendarSwitch.isChecked && emailSwitch.isChecked && addressBookSwitch.isChecked) ||
                        (calendarSwitch.isChecked && !emailSwitch.isChecked && addressBookSwitch.isChecked) -> {
                    viewModel.viewEvent(SetupContract.ViewEvent.SetMaxSteps(3))
                    viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(1))
                }

                !calendarSwitch.isChecked && !emailSwitch.isChecked && !addressBookSwitch.isChecked -> {
                    viewModel.viewEvent(SetupContract.ViewEvent.SetMaxSteps(0))
                    viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(0))
                }

                else -> {
                    viewModel.viewEvent(SetupContract.ViewEvent.SetMaxSteps(4))
                    viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(1))
                }
            }
//
//            // Update topbar to reflect selection.
//            val topBar = (activity as TopBarActivity).topBar
//            topBar.currentStep = authHolder.currentStep
//            topBar.maxSteps = authHolder.maxSteps
//            topBar.progress = authHolder.currentStep.toFloat() / authHolder.maxSteps.toFloat()
        }
    }
}
