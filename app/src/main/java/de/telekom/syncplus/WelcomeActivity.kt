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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.ui.main.WelcomeFragment

class WelcomeActivity : BaseActivity() {
    companion object {
        private const val ARG_OVERRIDE = "ARG_OVERRIDE"
        fun newIntent(
            activity: Activity,
            noRedirect: Boolean = false,
            clear: Boolean = false
        ): Intent {
            val intent = Intent(activity, WelcomeActivity::class.java)
            intent.putExtra(ARG_OVERRIDE, noRedirect)
            if (clear)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    private val mNoRedirect by extraNotNull(ARG_OVERRIDE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        val accountManager =
            IDMAccountManager(this, DavNotificationUtils.reloginCallback(this, "authority"))
        if (!mNoRedirect && accountManager.getAccounts().count() > 0) {
            startActivity(AccountsActivity.newIntent(this, false))
        } else {
            setContentView(R.layout.activity_container)
            if (savedInstanceState == null) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.container, WelcomeFragment.newInstance())
                    .commitNow()
            }
        }
    }
}
