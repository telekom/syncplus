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
import android.net.Uri
import android.os.Bundle
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.ActivityWebviewBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

class WebViewActivity : BaseActivity() {
    companion object {
        private const val ARG_URL = "ARG_URL"

        fun newIntent(
            activity: Activity,
            url: Uri,
        ): Intent {
            val intent = Intent(activity, WebViewActivity::class.java)
            intent.putExtra(ARG_URL, url)
            return intent
        }
    }

    private val mUrl by extraNotNull<Uri>(ARG_URL)
    private val binding by viewBinding(R.id.root) { ActivityWebviewBinding.bind(it) }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.layoutSmallTopbar.topbarTitle.text = getString(R.string.help_for_syncplus)
        binding.layoutSmallTopbar.backButtonSmall.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            val fragment = WebViewFragment.newInstance(mUrl)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }
    }
}
