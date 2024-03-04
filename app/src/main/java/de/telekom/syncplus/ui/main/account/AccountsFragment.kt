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

package de.telekom.syncplus.ui.main.account

import android.Manifest
import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.*
import de.telekom.syncplus.databinding.DialogDeleteAccountBinding
import de.telekom.syncplus.databinding.FragmentAccountBinding
import de.telekom.syncplus.ui.dialog.EnergySaverDialog
import de.telekom.syncplus.ui.main.welcome.WelcomeActivity
import de.telekom.syncplus.util.Prefs
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.coroutines.launch

class AccountsFragment : BaseFragment(R.layout.fragment_account) {
    override val TAG: String
        get() = "SETUP_FINISHED_FRAGMENT"

    companion object {
        private const val ARG_NEW = "ARG_NEW"

        fun newInstance(newAccountCreated: Boolean): AccountsFragment {
            val args = Bundle(1)
            args.putBoolean(ARG_NEW, newAccountCreated)
            val fragment = AccountsFragment()
            fragment.arguments = args
            return fragment
        }
    }

    @Suppress("UNUSED")
    private val mNewAccountCreated by extraNotNull(ARG_NEW, false)
    private lateinit var accountManager: IDMAccountManager
    private val binding by viewBinding(FragmentAccountBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountManager = IDMAccountManager(requireContext())

        val prefs = Prefs(requireContext())
        val currentVersionCode = BuildConfig.VERSION_CODE
        if (prefs.currentVersionCode < currentVersionCode) {
            prefs.currentVersionCode = currentVersionCode.toInt()
        }

        if (!prefs.energySavingDialogShown && accountManager.getAccounts().isNotEmpty()) {
            EnergySaverDialog.instantiate().show(
                childFragmentManager,
                "EnergySaver",
            )
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding.list.adapter = AccountAdapter(requireContext(), getAccounts(), { position, adapter ->
            val account = adapter.getItem(position)
            startActivity(AccountSettingsActivity.newIntent(requireActivity(), account))
        }, { position, adapter ->
            val account = adapter.getItem(position)
            showDeleteDialog(account.name) {
                lifecycleScope.launch {
                    accountManager.removeAccount(account)
                    adapter.removeItemAt(position)
                    adapter.notifyDataSetChanged()
                }
            }
        })
        binding.button.setOnClickListener {
            startActivity(
                WelcomeActivity.newIntent(
                    requireActivity(),
                    noRedirect = true
                )
            )
        }

        val adapter = binding.list.adapter as? AccountAdapter
        val accounts = getAccounts()
        adapter?.dataSource = accounts
        adapter?.notifyDataSetChanged()
        if (adapter?.dataSource?.isEmpty() == true) {
            startActivity(
                WelcomeActivity.newIntent(
                    requireActivity(),
                    clear = true
                )
            )
        } else {
            val (hasCalendarSync, hasContactSync) = hasAnySyncEnabled(accounts)
            val requiredPermissions = ArrayList<String>()
            if (hasCalendarSync) {
                requiredPermissions.add(Manifest.permission.READ_CALENDAR)
                requiredPermissions.add(Manifest.permission.WRITE_CALENDAR)
            }
            if (hasContactSync) {
                requiredPermissions.add(Manifest.permission.READ_CONTACTS)
                requiredPermissions.add(Manifest.permission.WRITE_CONTACTS)
            }

            // Request required permissions first.
            requestPermissions(requiredPermissions) { granted, error, _ ->
                when {
                    granted -> {}
                    else -> {
                        Logger.log.severe("Error: Granting Permission: $error")
                    }
                }
            }
        }
    }

    private fun hasAnySyncEnabled(accounts: List<Account>): Pair<Boolean, Boolean> {
        var hasCalendarSync = false
        var hasContactSync = false
        for (account in accounts) {
            val accountSettings = AccountSettings(requireContext(), account)

            if (accountSettings.isCalendarSyncEnabled()) {
                hasCalendarSync = true
            }
            if (accountSettings.isContactSyncEnabled()) {
                hasContactSync = true
            }
        }

        return Pair(hasCalendarSync, hasContactSync)
    }

    private fun getAccounts(): List<Account> {
        return accountManager.getAccounts().toMutableList()
    }

    private fun deleteAppData() {
        val prefs = Prefs(requireContext())
        prefs.consentDialogShown = false
        prefs.allTypesPrevSynced = false
    }

    private fun showDeleteDialog(
        accountName: String,
        onDelete: () -> Unit,
    ) {
        val binding = DialogDeleteAccountBinding.inflate(layoutInflater, null, false)
        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setView(binding.root)
                .setCancelable(false)
                .create()
                .apply {
                    setCanceledOnTouchOutside(false)
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                }
        binding.email.text = accountName

        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.acceptButton.setOnClickListener {
            dialog.dismiss()
            onDelete()
            deleteAppData()
        }
        dialog.show()
    }

    class AccountAdapter(
        context: Context,
        dataSource: List<Account>,
        private val onEdit: ((position: Int, adapter: AccountAdapter) -> Unit)? = null,
        private val onDelete: ((position: Int, adapter: AccountAdapter) -> Unit)? = null
    ) : BaseListAdapter<Account>(context, dataSource) {
        private val accountManager = IDMAccountManager(context)

        private class ViewHolder(view: View?) {
            val title = view?.findViewById<TextView>(R.id.title)
            val subtitle = view?.findViewById<TextView>(R.id.subtitle)
            val editButton = view?.findViewById<ImageButton>(R.id.editButton)
            val deleteButton = view?.findViewById<ImageButton>(R.id.deleteButton)
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.account_row, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val item = getItem(position)
            viewHolder.title?.text = item.name

            val calEnabled = accountManager.isCalendarSyncEnabled(item)
            val cardEnabled = accountManager.isContactSyncEnabled(item)
            val syncItems = arrayOf(
                if (calEnabled) context.getString(R.string.calendar) else null,
                if (cardEnabled) context.getString(R.string.address_book) else null
            ).filterNotNull()
            viewHolder.subtitle?.text = syncItems.joinToString(separator = ", ")
            viewHolder.editButton?.setOnClickListener { onEdit?.invoke(position, this) }
            viewHolder.deleteButton?.setOnClickListener { onDelete?.invoke(position, this) }

            return rowView!!
        }

        fun removeItemAt(position: Int) {
            val newDataSource = dataSource.toMutableList()
            newDataSource.removeAt(position)
            dataSource = newDataSource
        }
    }
}
