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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.telekom.dtagsyncpluskit.dp
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.IntroActivity
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.fragment_intro_mail.view.*

class IntroMailFragment : BaseFragment() {
    override val TAG: String
        get() = "INTRO_MAIL_FRAGMENT"

    companion object {
        private const val ARG_LISTENER = "ARG_LISTENER"
        private const val ARG_BOTTOM_PADDING = "ARG_BOTTOM_PADDING"
        fun newInstance(
            l: IntroActivity.OnCancelListener,
            bottomPadding: Int
        ): IntroMailFragment {
            val args = Bundle(2)
            args.putParcelable(ARG_LISTENER, l)
            args.putInt(ARG_BOTTOM_PADDING, bottomPadding)
            val fragment = IntroMailFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mListener by extraNotNull<IntroActivity.OnCancelListener>(ARG_LISTENER)
    private val mBottomPadding by extraNotNull<Int>(ARG_BOTTOM_PADDING)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_intro_mail, container, false)
        v.setPadding(0, 16.dp, 0, mBottomPadding + 8.dp)
        v.cancelButton.setOnClickListener {
            mListener.onCancel()
        }
        return v
    }
}
