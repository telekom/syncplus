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
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.dtagsyncpluskit.xdav.CollectionFetcher
import de.telekom.syncplus.*
import de.telekom.syncplus.R
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.android.synthetic.main.fragment_setup_calendar.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarCollectionsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val mDB = AppDatabase.getInstance(app)

    private val _fetcher = MutableLiveData<CollectionFetcher?>(null)
    val fetcher: LiveData<CollectionFetcher?> = _fetcher

    fun fetch(account: Account, serviceEnvironments: ServiceEnvironments, collectionType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val serviceType = when (collectionType) {
                Collection.TYPE_CALENDAR -> Service.TYPE_CALDAV
                Collection.TYPE_ADDRESSBOOK -> Service.TYPE_CARDDAV
                else -> null
            } ?: return@launch
            val authority = when (collectionType) {
                Collection.TYPE_CALENDAR -> CalendarContract.AUTHORITY
                Collection.TYPE_ADDRESSBOOK -> app.getString(R.string.address_books_authority)
                else -> null
            } ?: return@launch

            val serviceId =
                mDB.serviceDao().getIdByAccountAndType(account.name, serviceType)
                    ?: return@launch

            val newFetcher = CollectionFetcher(
                app,
                account,
                serviceId,
                collectionType
            ) {
                DavNotificationUtils.showReloginNotification(app, authority, it)
            }
            newFetcher.refresh(serviceEnvironments)
            _fetcher.postValue(newFetcher)
        }
    }

    fun enableSync(item: Collection, enabled: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
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

class SetupCalendarFragment : BaseFragment() {
    override val TAG = "SETUP_CALENDAR_FRAGMENT"

    companion object {
        fun newInstance() = SetupCalendarFragment()
    }

    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val model by activityViewModels<CalendarCollectionsViewModel>()
        val v = inflater.inflate(R.layout.fragment_setup_calendar, container, false)
        v.nextButton.isEnabled = false
        v.nextButton.setOnClickListener {
            goNext()
        }
        v.list.adapter =
            CalendarAdapter(requireContext(), ArrayList(), model) { adapter ->
                val selection = adapter.dataSource.filter { it.sync }
                v.nextButton.isEnabled = selection.count() > 0
            }


        model.fetch(account, App.serviceEnvironments(requireContext()), Collection.TYPE_CALENDAR)
        model.fetcher.observe(viewLifecycleOwner, Observer { fetcher ->
            fetcher?.collections?.removeObservers(viewLifecycleOwner)
            fetcher?.collections?.observe(viewLifecycleOwner, Observer { collections ->
                val adapter = v.list.adapter as? CalendarAdapter
                adapter?.dataSource =
                    AccountSettingsFragment.sortCalendarCollections(collections.toList())
                adapter?.notifyDataSetChanged()
            })
        })

        return v
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

    private fun goNext() {
        // Sync calendars.
        val account = Account(authHolder.accountName, getString(R.string.account_type))
        val accountSettings =
            AccountSettings(
                requireContext(),
                App.serviceEnvironments(requireContext()),
                account,
                DavNotificationUtils.reloginCallback(requireContext(), CalendarContract.AUTHORITY)
            )
        accountSettings.resyncCalendars(true)

        if (authHolder.emailEnabled) {
            push(R.id.container, SetupEmailFragment.newInstance())
        } else {
            accountSettings.setSetupCompleted(true)
            startActivity(AccountsActivity.newIntent(requireActivity(), true))
        }
    }

    class CalendarAdapter(
        context: Context,
        dataSource: List<Collection>,
        private val viewModel: CalendarCollectionsViewModel,
        private val onSelectionChanged: ((adapter: CalendarAdapter) -> Unit)? = null
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

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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
