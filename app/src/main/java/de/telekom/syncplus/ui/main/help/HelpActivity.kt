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

package de.telekom.syncplus.ui.main.help

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.HelpActivityBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class HelpActivity : BaseActivity(R.layout.help_activity) {
    companion object {
        fun newIntent(activity: Activity) = Intent(activity, HelpActivity::class.java)
    }

    private val binding by viewBinding(R.id.root) { HelpActivityBinding.bind(it) }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.help_activity)
        binding.layoutSmallTopbar.backButtonSmall.visibility = View.GONE
        binding.layoutSmallTopbar.backButtonSmall.setOnClickListener {
            if (HelpFragment.instance?.currentWebView?.goBackInWebView() == true) {
                return@setOnClickListener
            }
            if (!popFragment()) {
                finish()
            }
        }
        binding.layoutSmallTopbar.closeButtonSmall.visibility = View.VISIBLE
        binding.layoutSmallTopbar.closeButtonSmall.setOnClickListener {
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
        binding.layoutSmallTopbar.backButtonSmall.visibility = View.GONE
    }

    override fun onFragmentPushed() {
        super.onFragmentPushed()
        binding.layoutSmallTopbar.backButtonSmall.visibility = View.VISIBLE
    }
}
