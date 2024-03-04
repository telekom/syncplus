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
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.dtagsyncpluskit.xdav.CollectionFetcher
import de.telekom.syncplus.AccountsActivity
import de.telekom.syncplus.App
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import de.telekom.syncplus.databinding.FragmentSetupCalendarBinding
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CalendarCollectionsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val mDB = AppDatabase.getInstance(app)
    private val accountManager by lazy {
        IDMAccountManager(app)
    }
    private val _fetcher = MutableLiveData<CollectionFetcher?>(null)
    private val _showError = MutableSharedFlow<Unit>()
    val fetcher: LiveData<CollectionFetcher?> = _fetcher
    val showError: SharedFlow<Unit> = _showError.asSharedFlow()

    fun discoverServices(
        account: Account,
        serviceEnvironments: ServiceEnvironments,
        collectionType: String,
        calendarSyncEnabled: Boolean,
        contactSyncEnabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            accountManager.discoverServicesConfiguration(
                account,
                serviceEnvironments,
                calendarSyncEnabled,
                contactSyncEnabled,
            )?.let {
                fetch(account, collectionType, calendarSyncEnabled)
            } ?: _showError.emit(Unit)
        }
    }

    fun fetch(
        account: Account,
        collectionType: String,
        isSyncEnabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val serviceType = when (collectionType) {
                Collection.TYPE_CALENDAR -> Service.TYPE_CALDAV
                Collection.TYPE_ADDRESSBOOK -> Service.TYPE_CARDDAV
                else -> null
            } ?: return@launch
            val authority = when (collectionType) {
                Collection.TYPE_CALENDAR -> CalendarContract.AUTHORITY
                Collection.TYPE_ADDRESSBOOK -> ContactsContract.AUTHORITY
                else -> null
            } ?: return@launch

            val serviceId = mDB.serviceDao().getIdByAccountAndType(account.name, serviceType)

            if (serviceId == null) {
                _showError.emit(Unit)
                return@launch
            }

            val newFetcher = CollectionFetcher(
                app,
                account,
                serviceId,
                collectionType,
            ) {
                DavNotificationUtils.showReloginNotification(app, authority, it)
            }
            newFetcher.refresh(isSyncEnabled)
            _fetcher.postValue(newFetcher)
        }
    }

    fun enableSync(
        item: Collection,
        enabled: Boolean,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val newItem = item.copy(sync = enabled)
        mDB.collectionDao().update(newItem)
        item.sync = enabled // also update the collection item
    }

    fun enableSync(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val serviceId = fetcher.value?.serviceId?.value ?: return@launch
            mDB.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_CALENDAR)
                .forEach { item ->
                    val newItem = item.copy(sync = enabled)
                    mDB.collectionDao().update(newItem)
                    item.sync = enabled // also update the collection item
                }
        }
    }
}

class SetupCalendarFragment : BaseFragment(R.layout.fragment_setup_calendar) {
    override val TAG = "SETUP_CALENDAR_FRAGMENT"
    private val viewModel by activityViewModels<CalendarCollectionsViewModel>()

    companion object {
        fun newInstance() = SetupCalendarFragment()
    }

    private val binding by viewBinding(FragmentSetupCalendarBinding::bind)
    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.nextButton.isEnabled = false
        binding.nextButton.setOnClickListener {
            goNext()
        }
        binding.list.adapter = CalendarAdapter(requireContext(), ArrayList(), viewModel) { adapter ->
            val selection = adapter.dataSource.filter { it.sync }
            binding.nextButton.isEnabled = selection.isNotEmpty()
        }

        viewModel.fetcher.observe(viewLifecycleOwner) { fetcher ->
            fetcher?.collections?.removeObservers(viewLifecycleOwner)
            fetcher?.collections?.observe(viewLifecycleOwner) { collections ->
                val adapter = binding.list.adapter as? CalendarAdapter
                adapter?.dataSource =
                    AccountSettingsFragment.sortCalendarCollections(collections.toList())
                adapter?.notifyDataSetChanged()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.showError.collect { showErrorDialog() }
                }
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
        val topBar = (activity as? TopBarActivity)?.topBar
        val authHolder = (activity as? SetupActivity)?.authHolder
        topBar?.large = false
        topBar?.currentStep = authHolder?.currentStep ?: -1
        topBar?.maxSteps = authHolder?.maxSteps ?: -1
        topBar?.description = getString(R.string.topbar_title_sync_calendar)
        topBar?.hasBackButton = true
        topBar?.hasHelpButton = true
        topBar?.setOnBackClickListener { finish() }
        topBar?.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
    }

    private fun discoverServices() {
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        viewModel.discoverServices(
            account,
            App.serviceEnvironments(),
            Collection.TYPE_CALENDAR,
            authHolder.calEnabled,
            authHolder.addressBookEnabled,
        )
    }

    private fun fetchCollections() {
        viewModel.fetch(
            Account(authHolder.accountName, getString(R.string.account_type)),
            Collection.TYPE_CALENDAR,
            isSyncEnabled = true, // this will make a fetched collections sync flag set to true by default
        )
    }

    private fun showErrorDialog() {
        ServiceDiscoveryErrorDialog.instantiate()
            .show(childFragmentManager, "ServiceDiscoveryErrorDialog")
    }

    private fun goNext() {
        // Sync calendars.
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings = AccountSettings(requireContext(), account)
        accountSettings.resyncCalendars(true)
        // Assume the account setup is completed right away because the
        // email configuration may change outside of the application.
        accountSettings.setSetupCompleted(true)

        if (authHolder.emailEnabled) {
            push(R.id.container, SetupEmailFragment.newInstance())
        } else {
            startActivity(
                AccountsActivity.newIntent(
                    requireActivity(),
                    newAccountCreated = true,
                    allTypesSynced = authHolder.allTypesSynced(),
                ),
            )
        }
    }

    class CalendarAdapter(
        context: Context,
        dataSource: List<Collection>,
        private val viewModel: CalendarCollectionsViewModel,
        private val onSelectionChanged: ((adapter: CalendarAdapter) -> Unit)? = null,
    ) : BaseListAdapter<Collection>(context, dataSource) {
        private class ViewHolder(view: View?) {
            val title = view?.findViewById<TextView>(R.id.title)
            val subtitle = view?.findViewById<TextView>(R.id.subtitle)
            val editIcon = view?.findViewById<ImageView>(R.id.editIcon)
            val toggle = view?.findViewById<Switch>(R.id.toggle)
        }

        override fun notifyDataSetChanged() {
            super.notifyDataSetChanged()
            onSelectionChanged?.invoke(this)
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.calendar_row, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val item = getItem(position)
            viewHolder.title?.text = item.title()
            viewHolder.toggle?.isChecked = item.sync
            if (item.readOnly()) {
                viewHolder.subtitle?.text = context.getString(R.string.write_protected)
                viewHolder.subtitle?.visibility = View.VISIBLE
                viewHolder.editIcon?.visibility = View.VISIBLE
            } else {
                viewHolder.subtitle?.visibility = View.GONE
                viewHolder.editIcon?.visibility = View.GONE
            }

            viewHolder.toggle?.setOnCheckedChangeListener { _, isChecked ->
                viewModel.enableSync(item, isChecked)
                onSelectionChanged?.invoke(this)
            }

            return rowView!!
        }

        override fun isEnabled(position: Int): Boolean {
            return dataSource[position].sync
        }

        override fun areAllItemsEnabled(): Boolean {
            return dataSource.all { it.sync }
        }
    }
}
