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

package de.telekom.syncplus.ui.setup.contacts.duplicates

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.DuplicatedContactsActivityBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class DuplicatedContactsActivity : BaseActivity(R.layout.duplicated_contacts_activity) {
    companion object {
        // const val EXTRA_DUPLICATES = "EXTRA_DUPLICATES"
        fun newIntent(packageContext: Context): Intent {
            return Intent(packageContext, DuplicatedContactsActivity::class.java)
        }
    }

    private val binding by viewBinding(R.id.root) { DuplicatedContactsActivityBinding.bind(it) }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(getString(R.string.title_activity_duplicated_contacts))
        binding.layoutSmallTopbar.backButtonSmall.setOnClickListener {
            if (!popFragment()) {
                finish()
            }
        }
        if (savedInstanceState == null) {
            val fragment = DuplicatesListFragment.newInstance()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }
    }

    fun setTitle(title: String?) {
        binding.layoutSmallTopbar.topbarTitle.text = title
    }
}
