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

package de.telekom.syncplus.ui.setup.calendar

import android.accounts.Account
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.CalendarRowBinding
import de.telekom.syncplus.databinding.FragmentSetupCalendarBinding
import de.telekom.syncplus.extensions.inflater
import de.telekom.syncplus.ui.dialog.ServiceDiscoveryErrorDialog
import de.telekom.syncplus.ui.main.account.AccountSettingsFragment
import de.telekom.syncplus.ui.main.account.AccountsActivity
import de.telekom.syncplus.ui.setup.SetupViewModel
import de.telekom.syncplus.ui.setup.contacts.SetupContract
import de.telekom.syncplus.ui.setup.email.SetupEmailFragment
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class SetupCalendarFragment : BaseFragment(R.layout.fragment_setup_calendar) {

    companion object {
        fun newInstance() = SetupCalendarFragment()
    }

    override val TAG = "SETUP_CALENDAR_FRAGMENT"

    private val viewModel by activityViewModels<SetupViewModel>()

    private val binding by viewBinding(FragmentSetupCalendarBinding::bind)

    private val adapter by lazy {
        CalendarAdapter { item, isChecked ->
            viewModel.viewEvent(SetupContract.ViewEvent.EnableCalendarSync(item, isChecked))
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.viewEvent(SetupContract.ViewEvent.SetCurrentStep(3))

        binding.nextButton.isEnabled = false
        binding.nextButton.setOnClickListener {
            viewModel.viewEvent(SetupContract.ViewEvent.TryToGoNextStep)
        }
        binding.list.adapter = adapter

        viewModel.fetcher.observe(viewLifecycleOwner) { fetcher ->
            fetcher?.collections?.removeObservers(viewLifecycleOwner)
            fetcher?.collections?.observe(viewLifecycleOwner) { collections ->
                adapter.submitList(AccountSettingsFragment.sortCalendarCollections(collections.toList())) {
                    if (getView() != null) {
                        binding.nextButton.isEnabled = true
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::handleAction) }
                launch { viewModel.navigation.collect(::handleNavigation) }
            }
        }

        childFragmentManager.setFragmentResultListener(
            ServiceDiscoveryErrorDialog.ACTION_RETRY_SERVICE_DISCOVERY,
            viewLifecycleOwner,
        ) { _, _ -> discoverServices() }

        fetchCollections()
    }

    override fun onStart() {
        super.onStart()

        viewModel.viewEvent(
            SetupContract.ViewEvent.SetupStep(
                description = getString(R.string.topbar_title_sync_calendar),
                hasBackButton = true,
                hasHelpButton = true,
                large = false
            )
        )
    }

    private fun handleAction(action: SetupContract.Action) {
        when (action) {
            SetupContract.Action.ShowError -> showErrorDialog()
            else -> Logger.log.warning("Skipped action $action")
        }
    }

    private fun handleNavigation(navigation: SetupContract.Navigation) {
        when (navigation) {
            is SetupContract.Navigation.NavigateToNextStep -> goNext(navigation)
        }
    }

    private fun discoverServices() {
        viewModel.viewEvent(SetupContract.ViewEvent.DiscoverServices(Collection.TYPE_CALENDAR))
    }

    private fun fetchCollections() {
        // this will make a fetched collections sync flag set to true by default
        viewModel.viewEvent(SetupContract.ViewEvent.FetchCollections(Collection.TYPE_CALENDAR, true))
    }

    private fun showErrorDialog() {
        ServiceDiscoveryErrorDialog.instantiate()
            .show(childFragmentManager, "ServiceDiscoveryErrorDialog")
    }

    private fun goNext(action: SetupContract.Navigation.NavigateToNextStep) {
        // Sync calendars.
        val account = Account(action.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(requireContext(), account)
        accountSettings.resyncCalendars(true)
        // Assume the account setup is completed right away because the
        // email configuration may change outside of the application.
        accountSettings.setSetupCompleted(true)

        if (action.isEmailEnabled) {
            push(R.id.container, SetupEmailFragment.newInstance())
        } else {
            startActivity(
                AccountsActivity.newIntent(
                    requireActivity(),
                    newAccountCreated = true,
                    allTypesSynced = action.isCalendarEnabled && action.isContactsEnabled && action.isEmailEnabled,
                ),
            )
        }
    }

    private class CalendarAdapter(
        private val onCheckChanged: (collection: Collection, isSelected: Boolean) -> Unit,
    ) : ListAdapter<Collection, CalendarAdapter.ViewHolder>(CollectionDiffCallback) {

        object CollectionDiffCallback : DiffUtil.ItemCallback<Collection>() {
            override fun areItemsTheSame(oldItem: Collection, newItem: Collection): Boolean {
                return oldItem.id == newItem.id
            }

            override fun getChangePayload(oldItem: Collection, newItem: Collection): Any? {
                return if (oldItem.sync != newItem.sync) newItem.sync else null
            }

            override fun areContentsTheSame(oldItem: Collection, newItem: Collection): Boolean {
                return oldItem == newItem
            }
        }

        inner class ViewHolder(private val binding: CalendarRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Collection) {
                with(binding) {
                    title.text = item.title()

                    if (item.readOnly()) {
                        subtitle.text = root.context.getString(R.string.write_protected)
                        subtitle.visibility = View.VISIBLE
                        editIcon.visibility = View.VISIBLE
                    } else {
                        subtitle.visibility = View.GONE
                        editIcon.visibility = View.GONE
                    }

                    bindSyncState(item, item.sync)
                }
            }

            fun bindSyncState(item: Collection, isSyncEnabled: Boolean) {
                binding.toggle.setOnCheckedChangeListener(null)
                binding.toggle.isChecked = isSyncEnabled
                binding.toggle.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(item, isChecked)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(CalendarRowBinding.inflate(parent.inflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) {
                holder.bind(getItem(position))
            } else {
                holder.bindSyncState(getItem(position), payloads.first() as Boolean)
            }
        }
    }
}
