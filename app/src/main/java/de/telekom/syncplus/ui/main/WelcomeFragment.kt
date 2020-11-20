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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.IntroActivity
import de.telekom.syncplus.LoginActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.Prefs
import kotlinx.android.synthetic.main.fragment_welcome.view.*

class WelcomeFragment : BaseFragment() {
    override val TAG: String
        get() = "WELCOME_FRAGMENT"

    companion object {
        fun newInstance() = WelcomeFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_welcome, container, false)
        val prefs = Prefs(requireContext())
        val hasAccounts = IDMAccountManager(
            requireContext(),
            DavNotificationUtils.reloginCallback(requireContext(), "authority")
        ).getAccounts().count() > 0
        if (hasAccounts) {
            val lp = v.title.layoutParams as ConstraintLayout.LayoutParams
            lp.verticalBias = 0.3f
            v.title.layoutParams = lp
            v.title.text = getString(R.string.welcome_new_account)
            v.message.text = getString(R.string.message_new_account)
            v.button2.visibility = View.VISIBLE
            v.button2.setOnClickListener { finishActivity() }
        } else {
            v.title.text = getString(R.string.welcome_title)
            v.button2.visibility = View.GONE
        }

        v.button1.setOnClickListener {
            if (prefs.starts == 0) {
                val intent = Intent(context, IntroActivity::class.java)
                startActivity(intent)
            } else {
                startSetup()
            }

            prefs.starts += 1
        }

        return v
    }

    private fun startSetup() {
        startActivity(LoginActivity.newIntent(requireActivity()))
    }
}
