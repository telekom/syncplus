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

package de.telekom.syncplus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.ui.main.AccountsFragment
import kotlinx.android.synthetic.main.activity_accounts.*

class AccountsActivity : BaseActivity() {
    companion object {
        private const val ARG_NEW = "ARG_NEW"
        fun newIntent(context: Context, newAccountCreated: Boolean): Intent {
            val intent = Intent(context, AccountsActivity::class.java)
            intent.putExtra(ARG_NEW, newAccountCreated)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    private val mNewAccountCreated by extraNotNull(ARG_NEW, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)
        image.setImageResource(
            if (mNewAccountCreated) R.drawable.ic_cloud_check_filled
            else R.drawable.ic_cloud_progress_filled
        )
        accountsTitle.text =
            getString(
                if (mNewAccountCreated) R.string.setup_finished
                else R.string.syncplus_accounts
            )

        if (savedInstanceState == null) {
            val fragment = AccountsFragment.newInstance(mNewAccountCreated)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }

        helpButton.setOnClickListener {
            startActivity(HelpActivity.newIntent(this))
        }
    }
}
