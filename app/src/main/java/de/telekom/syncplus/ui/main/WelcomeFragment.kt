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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.IntroActivity
import de.telekom.syncplus.LoginActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentWelcomeBinding
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.Prefs
import de.telekom.syncplus.util.viewbinding.viewBinding

class WelcomeFragment : BaseFragment(R.layout.fragment_welcome) {
    override val TAG: String
        get() = "WELCOME_FRAGMENT"

    companion object {
        private const val ARG_ACCOUNTS_DELETED = "ARG_ACCOUNTS_DELETED"

        fun newInstance(accountsDeleted: Boolean = false): WelcomeFragment {
            val args = Bundle(1)
            args.putBoolean(ARG_ACCOUNTS_DELETED, accountsDeleted)
            val fragment = WelcomeFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val binding by viewBinding(FragmentWelcomeBinding::bind)
    private val mAccountDeleted by extraNotNull(ARG_ACCOUNTS_DELETED, false)

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val hasAccounts = IDMAccountManager(requireContext())
            .getAccounts()
            .isNotEmpty()
        if (hasAccounts) {
            val lp = binding.title.layoutParams as ConstraintLayout.LayoutParams
            lp.verticalBias = 0.3f
            binding.title.layoutParams = lp
            binding.title.text = getString(R.string.welcome_new_account)
            binding.message.text = getString(R.string.message_new_account)
            binding.button2.visibility = View.VISIBLE
            binding.button2.setOnClickListener { finishActivity() }
        } else {
            binding.title.text = getString(R.string.welcome_title)
            binding.button2.visibility = View.GONE
        }

        binding.button1.setOnClickListener {
            if (!prefs.allTypesPrevSynced) {
                startActivity(Intent(context, IntroActivity::class.java))
            } else {
                startSetup()
            }
        }

        if (mAccountDeleted) {
            AccountDeletedDialog.instantiate().show(
                childFragmentManager,
                "AccountDeleted",
            )
        }
    }

    private fun startSetup() {
        startActivity(LoginActivity.newIntent(requireActivity()))
    }
}
