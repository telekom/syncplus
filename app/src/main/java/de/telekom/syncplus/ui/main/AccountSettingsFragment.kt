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
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings.Companion.SYNC_INTERVAL_MANUALLY
import de.telekom.dtagsyncpluskit.dp
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.DialogListviewBinding
import de.telekom.syncplus.databinding.FragmentAccountsSettingsBinding
import de.telekom.syncplus.extensions.isConnectionAvailable
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class AccountSettingsFragment : BaseFragment(R.layout.fragment_accounts_settings) {
    override val TAG: String
        get() = "ACCOUNT_SETTINGS_FRAGMENT"

    companion object {
        private const val ARG_ACCOUNT = "ARG_ACCOUNT"

        fun newInstance(account: Account): AccountSettingsFragment {
            val args = Bundle(1)
            args.putParcelable(ARG_ACCOUNT, account)
            val fragment = AccountSettingsFragment()
            fragment.arguments = args
            return fragment
        }

        fun sortCalendarCollections(collections: List<Collection>): List<Collection> {
            val mainCalendar =
                collections.find { it.url.toString().endsWith("USER_CALENDAR-MAIN/") }
            val birthdaysCalendar =
                collections.find { it.url.toString().endsWith("ADDRESS_BOOK/") }

            val privateCalendars =
                collections.filter {
                    val url = it.url.toString()
                    !url.endsWith("USER_CALENDAR-MAIN/") &&
                            !url.endsWith("ADDRESS_BOOK/") &&
                            !url.contains("INFO_CHANNEL-")
                }.sortedBy { it.displayName }

            val infoCalendars =
                collections.filter {
                    val url = it.url.toString()
                    url.contains("INFO_CHANNEL-")
                }.sortedBy { it.displayName }

            val calendars = privateCalendars.toMutableList()
            if (mainCalendar != null) {
                calendars.add(0, mainCalendar)
            }
            if (birthdaysCalendar != null) {
                calendars.add(1, birthdaysCalendar)
            }
            calendars.addAll(infoCalendars)

            return calendars
        }
    }

    private val mAccount by extraNotNull<Account>(ARG_ACCOUNT)
    private val mHandler = Handler(Looper.getMainLooper())
    private val viewModel by activityViewModels<CalendarCollectionsViewModel>()
    private val binding by viewBinding(FragmentAccountsSettingsBinding::bind)

    override fun onResume() {
        super.onResume()
        updateLastSyncDate(getAccountSettings(mAccount))
    }

    override fun onStop() {
        super.onStop()
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun displayConnectionErrorDialog() {
        NetworkErrorDialog.instantiate()
            .show(childFragmentManager, "NetworkErrorDialog")
    }

    private fun updateLastSyncDate(
        accountSettings: AccountSettings,
    ) {
        val calendarSyncDate = accountSettings.lastSyncDate(CalendarContract.AUTHORITY)
        if (calendarSyncDate != null) {
            val (dayMonYear, hourMin) = calendarSyncDate
            binding.synctextCalendar.text = getString(R.string.synctext_format, dayMonYear, hourMin)
            binding.synctextCalendar.isVisible = accountSettings.isCalendarSyncEnabled()
        } else {
            binding.synctextCalendar.isVisible = false
        }

        val addressSyncDate = accountSettings.lastSyncDate(ContactsContract.AUTHORITY)
        if (addressSyncDate != null) {
            val (dayMonYear, hourMin) = addressSyncDate
            binding.synctext.text = getString(R.string.synctext_format, dayMonYear, hourMin)
            binding.synctext.isVisible = accountSettings.isContactSyncEnabled()
        } else {
            binding.synctext.isVisible = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupCalendarSubView(accountSettings: AccountSettings) {
        val calendarEnabled = accountSettings.isCalendarSyncEnabled()
        binding.calendarSwitch.isChecked = calendarEnabled
        binding.calendarList.adapter = CalendarAdapter(requireContext(), ArrayList(), viewModel)
        setListOpened(calendarEnabled)

        val enableCalendarSync: (isEnabled: Boolean) -> Unit = { isEnabled ->
            accountSettings.setCalendarSyncEnabled(isEnabled)
            viewModel.enableSync(isEnabled)

            val adapter = binding.calendarList.adapter as? CalendarAdapter
            adapter?.notifyDataSetChanged()
            setListOpened(isEnabled)
            setupSyncDropdown(accountSettings)
            updateLastSyncDate(accountSettings)
        }

        binding.calendarSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Request required permissions first.
                val requiredPermissions = arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
                requestPermissions(*requiredPermissions) { granted, error, _ ->
                    if (granted) {
                        enableCalendarSync(true)
                    } else {
                        Logger.log.severe("Error: Granting Permission: $error")
                        // TODO: Direct to tutorial turning them back on.
                        binding.calendarSwitch.isChecked = false
                    }
                }
            } else {
                enableCalendarSync(false)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val accountSettings = getAccountSettings(mAccount)
        binding.email.text = mAccount.name
        setupCalendarSubView(accountSettings)
        setupAddressBookSubView(accountSettings)
        setupSyncDropdown(accountSettings)
        // Update sync interval in onResume()

        val context = requireContext()
        binding.syncnowButton.setOnClickListener {
            // Check here whether the network is available, and display an error if it's not
            if (context.getSystemService<ConnectivityManager>()?.isConnectionAvailable() == false) {
                displayConnectionErrorDialog()
                return@setOnClickListener
            }

            binding.syncnowButton.isEnabled = false
            binding.syncnowButton.icon =
                ContextCompat.getDrawable(context, R.drawable.ic_sync_now_icon)
            binding.syncnowtextview.text = requireContext().getString(R.string.sync_now)
            accountSettings.resyncCalendars(false)
            accountSettings.resyncContacts(false)

            mHandler.postDelayed({
                binding.syncnowButton.isEnabled = true
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_sync_check_animated)
                binding.syncnowButton.icon = drawable
                binding.syncnowtextview.text = requireContext().getString(R.string.sync_done)
                mHandler.post { (drawable as? AnimatedVectorDrawable)?.start() }
                mHandler.postDelayed({
                    if (binding.syncnowButton.isEnabled) {
                        binding.syncnowtextview.text = requireContext().getString(R.string.sync_now)
                        binding.syncnowButton.icon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_sync_now_icon)
                    }

                    // Sync should be completed now, update last synced date.
                    updateLastSyncDate(accountSettings)
                }, 5000)

                // It's not guaranteed that the sync is done at this point, we'll try updating it
                // once again in a couple seconds.
                updateLastSyncDate(accountSettings)
            }, 2500)
        }

        viewModel.fetcher.observe(viewLifecycleOwner) { fetcher ->
            fetcher?.collections?.removeObservers(viewLifecycleOwner)
            fetcher?.collections?.observe(viewLifecycleOwner) { collections ->
                val adapter = binding.calendarList.adapter as? CalendarAdapter
                adapter?.dataSource = sortCalendarCollections(collections.toList())
                adapter?.notifyDataSetChanged()
                setListOpened(getAccountSettings(mAccount).isCalendarSyncEnabled())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showError.collect { showErrorDialog() }
            }
        }

        childFragmentManager.setFragmentResultListener(
            ServiceDiscoveryErrorDialog.ACTION_RETRY_SERVICE_DISCOVERY,
            viewLifecycleOwner,
        ) { _, _ -> discoverServices() }

        fetchCollections()
    }

    private fun discoverServices() {
        val settings = getAccountSettings(mAccount)
        viewModel.discoverServices(
            mAccount,
            App.serviceEnvironments(),
            Collection.TYPE_CALENDAR,
            settings.isCalendarSyncEnabled(),
            settings.isContactSyncEnabled(),
        )
    }

    private fun fetchCollections() {
        viewModel.fetch(
            mAccount,
            Collection.TYPE_CALENDAR,
            isSyncEnabled = false, // Rely only on stored values to not change its sync state forcefully
        )
    }

    private fun getAccountSettings(account: Account): AccountSettings {
        return AccountSettings(
            requireContext(),
            account,
        )
    }

    private fun showErrorDialog() {
        ServiceDiscoveryErrorDialog.instantiate()
            .show(childFragmentManager, "ServiceDiscoveryErrorDialog")
    }

    private fun setupAddressBookSubView(accountSettings: AccountSettings) {
        binding.addressBookSwitch.isChecked = accountSettings.isContactSyncEnabled()

        val enableContactSync: (isEnabled: Boolean) -> Unit = { isEnabled ->
            accountSettings.setContactSyncEnabled(isEnabled)
            viewLifecycleOwner.lifecycleScope.launch {
                accountSettings.setSyncAllAddressBooks(isEnabled)
            }
            setupSyncDropdown(accountSettings)
            updateLastSyncDate(accountSettings)
        }

        binding.addressBookSwitch.setOnCheckedChangeListener { _, isChecked ->
            //v.synctext.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                // Request required permissions first.
                val requiredPermissions = arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                )
                requestPermissions(*requiredPermissions) { granted, error, _ ->
                    if (granted) {
                        enableContactSync(true)
                    } else {
                        Logger.log.severe("Error: Granting Permission: $error")
                        // TODO: Direct to tutorial turning them back on.
                        binding.addressBookSwitch.isChecked = false
                    }
                }
            } else {
                enableContactSync(false)
            }
        }
    }

    private fun setupSyncDropdown(accountSettings: AccountSettings) {
        val getSyncInterval: () -> Long? = {
            val addressBookSyncInterval = accountSettings.getSyncInterval(ContactsContract.AUTHORITY)
            val calendarSyncInterval = accountSettings.getSyncInterval(CalendarContract.AUTHORITY)
            val ret = (addressBookSyncInterval ?: calendarSyncInterval)
            Logger.log.info("getSyncInterval = $ret")
            ret
        }

        val syncInterval = getSyncInterval()
        if (syncInterval == null) {
            binding.moreSettingsWrapper.visibility = View.GONE
            return
        }

        binding.moreSettingsWrapper.visibility = View.VISIBLE
        Logger.log.info("formatSyncIntervalString = ${formatSyncIntervalString(syncInterval)}")
        binding.moreSettingsText.text = formatSyncIntervalString(syncInterval)
        binding.moreSettingsButton.setOnClickListener {
            showSyncIntervalDialog(getSyncInterval()) { interval ->
                binding.moreSettingsText.text = formatSyncIntervalString(interval)
                accountSettings.setSyncInterval(CalendarContract.AUTHORITY, interval)
                accountSettings.setSyncInterval(ContactsContract.AUTHORITY, interval)
            }
        }
    }

    private fun formatSyncIntervalString(interval: Long): String {
        Logger.log.info("formatSyncIntervalString($interval)")
        if (interval == 0L || interval == SYNC_INTERVAL_MANUALLY) {
            return getString(R.string.sync_disabled)
        }

        val intervalInMinutes = interval / 60
        if (intervalInMinutes <= 60) {
            return getString(R.string.every_x_minutes, intervalInMinutes)
        }

        return getString(R.string.every_x_hours, intervalInMinutes / 60)
    }

    private fun setListOpened(opened: Boolean) {
        var totalHeight = 0
        val adapter = binding.calendarList.adapter ?: return
        val vg: ViewGroup = binding.calendarList
        for (i in 0 until adapter.count) {
            val listItem = adapter.getView(i, null, vg)
            listItem.measure(0, 0)
            // Log.d("SyncPlus", "measuredHeight($i) = ${listItem.measuredHeight} (${listItem.measuredHeight.dp}dp)")
            totalHeight += listItem.measuredHeight + 4.dp
        }
        // Log.d("SyncPlus", "dividerHeight: ${listView.dividerHeight} (${listView.dividerHeight.dp}dp)")
        // Log.d("SyncPlus", "adapter.count: ${adapter.count}")
        if (binding.calendarList.dividerHeight > 0) {
            totalHeight += binding.calendarList.dividerHeight * adapter.count
        }

        binding.calendarList.layoutParams =
            binding.calendarList.layoutParams.apply {
                height = totalHeight
            }
        binding.calendarList.requestLayout()

        binding.calendarListWrapper.layoutParams =
            binding.calendarListWrapper.layoutParams.apply {
                height = if (opened) totalHeight else 0
            }
        binding.calendarListWrapper.requestLayout()
    }

    @SuppressLint("InflateParams")
    fun showSyncIntervalDialog(
        selected: Long?,
        onSelected: (interval: Long) -> Unit,
    ) {
        data class Interval(val seconds: Long, var selected: Boolean = false) {
            val title = formatSyncIntervalString(seconds)
        }

        data class ViewHolder(private val view: View?) {
            val root = view?.findViewById<View>(R.id.root)
            val title = view?.findViewById<TextView>(R.id.title)
        }

        val dataSource =
            arrayListOf(
                Interval(14400L), // 4 hours
                Interval(10800L), // 3 hours
                Interval(7200L), // 2 hours
                Interval(3600L), // 1 hour
            )

        if (Build.VERSION.SDK_INT >= 24) {
            dataSource.addAll(
                arrayOf(
                    Interval(1800), // 30 minutes
                    Interval(900), // 15 minutes
                ),
            )
        }

        dataSource.find { it.seconds == selected }?.selected = true
        val adapter = object : BaseListAdapter<Interval>(requireContext(), dataSource) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup?,
            ): View {
                val viewHolder: ViewHolder?
                val rowView: View?

                if (convertView == null) {
                    rowView = inflater.inflate(R.layout.row_pick_sync_interval, parent, false)
                    viewHolder = ViewHolder(rowView)
                    rowView.tag = viewHolder
                } else {
                    rowView = convertView
                    viewHolder = rowView.tag as ViewHolder
                }

                val item = getItem(position)
                viewHolder.title?.text = item.title
                viewHolder.root?.setBackgroundResource(
                    if (item.selected) R.color.listSelected else android.R.color.transparent,
                )

                return rowView!!
            }
        }
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogListviewBinding.inflate(layoutInflater, null, false)
        binding.list.adapter = adapter
        binding.list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            onSelected(dataSource[position].seconds)
        }
        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setContentView(binding.root, lp)
        dialog.show()
    }

    class CalendarAdapter(
        context: Context,
        dataSource: List<Collection>,
        private val viewModel: CalendarCollectionsViewModel,
    ) : BaseListAdapter<Collection>(context, dataSource) {
        private val isTablet = context.resources.getBoolean(R.bool.isTablet)

        private class ViewHolder(view: View?) {
            val titleTextView = view?.findViewById<TextView>(R.id.title)
            val checkView = view?.findViewById<CheckBox>(R.id.checkbox)
            val editableIcon = view?.findViewById<ImageView>(R.id.editIcon)
            val subtitle = view?.findViewById<TextView>(R.id.subtitle)
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.account_settings_calendar_item, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val item = getItem(position)
            viewHolder.titleTextView?.text = item.title()
            viewHolder.checkView?.isChecked = item.sync
            viewHolder.checkView?.setOnCheckedChangeListener { _, isChecked ->
                viewModel.enableSync(item, isChecked)
            }

            if (item.readOnly()) {
                viewHolder.editableIcon?.visibility = View.VISIBLE
                viewHolder.subtitle?.visibility = if (isTablet) View.VISIBLE else View.GONE
            } else {
                viewHolder.editableIcon?.visibility = View.GONE
                viewHolder.subtitle?.visibility = View.GONE
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
