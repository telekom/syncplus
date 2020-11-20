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

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.openPlayStore
import de.telekom.syncplus.AccountsActivity
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.SetupActivity
import kotlinx.android.synthetic.main.fragment_setup_email.view.*
import kotlinx.coroutines.launch

class SetupEmailFragment : BaseFragment() {
    override val TAG = "SETUP_EMAIL_FRAGMENT"

    companion object {
        fun newInstance() = SetupEmailFragment()
    }

    private val authHolder by lazy {
        (activity as SetupActivity).authHolder
    }

    private var mNext = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_setup_email, container, false)
        v.downloadMailAppLayout.setOnClickListener {
            openPlayStore(context, "de.telekom.mail")
            mNext = true
        }
        v.preinstalledMailAppLayout.setOnClickListener {
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT)
            startActivity(intent)
            mNext = true
        }
        return v
    }

    override fun onStart() {
        super.onStart()
        val topBar = (activity as? TopBarActivity)?.topBar
        topBar?.currentStep = authHolder.currentStep
        topBar?.maxSteps = authHolder.maxSteps
        topBar?.description = getString(R.string.topbar_title_email)
        topBar?.hasBackButton = true
        topBar?.hasBackButton = true
        topBar?.setOnBackClickListener { finish() }
        topBar?.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }

        if (mNext) {
            mNext = false
            goNext()
        }
    }

    private fun goNext() {
        startActivity(AccountsActivity.newIntent(requireActivity(), true))
    }
}
