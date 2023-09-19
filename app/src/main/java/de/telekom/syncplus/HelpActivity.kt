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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.ui.main.HelpFragment
import kotlinx.android.synthetic.main.layout_small_topbar.*

class HelpActivity : BaseActivity() {

    companion object {
        fun newIntent(activity: Activity) = Intent(activity, HelpActivity::class.java)
    }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.help_activity)
        backButtonSmall.visibility = View.GONE
        backButtonSmall.setOnClickListener {
            if (HelpFragment.instance?.currentWebView?.goBackInWebView() == true)
                return@setOnClickListener
            if (!popFragment())
                finish()
        }
        closeButtonSmall.visibility = View.VISIBLE
        closeButtonSmall.setOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            val fragment = HelpFragment.newInstance()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }
    }

    override fun onFragmentPopped() {
        super.onFragmentPopped()
        backButtonSmall.visibility = View.GONE
    }

    override fun onFragmentPushed() {
        super.onFragmentPushed()
        backButtonSmall.visibility = View.VISIBLE
    }
}
