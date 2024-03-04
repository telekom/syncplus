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

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.AccountsSettingsActivityBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class AccountSettingsActivity : BaseActivity(R.layout.accounts_settings_activity) {
    companion object {
        private const val ARG_ACCOUNT = "ARG_ACCOUNT"

        fun newIntent(
            activity: Activity,
            account: Account,
        ): Intent {
            val intent = Intent(activity, AccountSettingsActivity::class.java)
            intent.putExtra(ARG_ACCOUNT, account)
            return intent
        }
    }

    private val mAccount by extraNotNull<Account>(ARG_ACCOUNT)
    private val binding by viewBinding(R.id.root) { AccountsSettingsActivityBinding.bind(it) }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.layoutSmallTopbar.topbarTitle.text = getString(R.string.title_settings)
        binding.layoutSmallTopbar.backButtonSmall.setOnClickListener { finish() }
        if (savedInstanceState == null) {
            val fragment = AccountSettingsFragment.newInstance(mAccount)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }
    }
}
