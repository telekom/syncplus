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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.ActivityAccountsBinding
import de.telekom.syncplus.ui.dialog.AccountDeletedDialog
import de.telekom.syncplus.ui.dialog.DataPrivacyDialogActivity
import de.telekom.syncplus.ui.main.help.HelpActivity
import de.telekom.syncplus.util.Prefs
import de.telekom.syncplus.util.viewbinding.viewBinding

class AccountsActivity : BaseActivity(R.layout.activity_accounts) {
    companion object {
        private const val ARG_NEW = "ARG_NEW"
        private const val ARG_ENERGY_SAVING = "ARG_ENERGY_SAVING"
        private const val ARG_ACCOUNT_DELETED = "ARG_ACCOUNT_DELETED"
        private const val ALL_TYPES_SYNCED = "ALL_TYPES_SYNCED"

        fun newIntent(
            context: Context,
            newAccountCreated: Boolean,
            allTypesSynced: Boolean? = null,
            energySaving: Boolean = false,
            accountDeleted: Boolean = false,
        ): Intent {
            val intent = Intent(context, AccountsActivity::class.java)
            intent.putExtra(ARG_NEW, newAccountCreated)
            intent.putExtra(ARG_ENERGY_SAVING, energySaving)
            intent.putExtra(ARG_ACCOUNT_DELETED, accountDeleted)
            allTypesSynced?.let { intent.putExtra(ALL_TYPES_SYNCED, allTypesSynced) }
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    private val mNewAccountCreated by extraNotNull(ARG_NEW, false)
    private val mAccountDeleted by extraNotNull(ARG_ACCOUNT_DELETED, false)
    private val allTypesSynced by extra(ALL_TYPES_SYNCED, null)
    private val binding by viewBinding(R.id.root) { ActivityAccountsBinding.bind(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.image.setImageResource(
            if (mNewAccountCreated) {
                R.drawable.ic_cloud_check_filled
            } else {
                R.drawable.ic_cloud_progress_filled
            },
        )
        binding.accountsTitle.text =
            getString(
                if (mNewAccountCreated) {
                    R.string.setup_finished
                } else {
                    R.string.syncplus_accounts
                },
            )

        if (savedInstanceState == null) {
            val fragment = AccountsFragment.newInstance(mNewAccountCreated)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }

        binding.helpButton.setOnClickListener {
            startActivity(HelpActivity.newIntent(this))
        }

        if (mAccountDeleted) {
            AccountDeletedDialog.instantiate().show(
                supportFragmentManager,
                "AccountDeleted",
            )
        }

        val prefs = Prefs(this)
        if (!prefs.consentDialogShown) {
            startActivity(DataPrivacyDialogActivity.newIntent(this))
        }
        allTypesSynced?.let {
            it as Boolean
            prefs.allTypesPrevSynced = it
        }
    }
}
