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

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.HelpActivity
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.fragment_copy_success.view.*

class CopySuccessFragment : BaseFragment() {
    override val TAG: String
        get() = "COPY_SUCCESS_FRAGMENT"

    companion object {
        fun newInstance() = CopySuccessFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_copy_success, container, false)
        v.nextButton.setOnClickListener {
            // TODO: Set results!
            finishWithResult(Activity.RESULT_OK, null)
        }
        return v
    }

    override fun onStart() {
        super.onStart()
        val topBar = (activity as? TopBarActivity)?.topBar
        topBar?.large = true
        topBar?.description = getString(R.string.copy_success)
        topBar?.extraDrawable = R.drawable.ic_cloud_check
        topBar?.extraDrawableSmall = 0
        topBar?.extraDescription = null
        topBar?.extraSectionButtonTitle = null
        topBar?.hasHelpButton = true
        topBar?.setOnHelpClickListener {
            startActivity(HelpActivity.newIntent(requireActivity()))
        }
    }
}
