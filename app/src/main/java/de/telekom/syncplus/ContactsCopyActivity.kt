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
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.syncplus.ui.main.CopyProgressFragment
import de.telekom.syncplus.ui.main.TopBarActivity

class ContactsCopyActivity : TopBarActivity() {
    companion object {
        const val EXTRA_AUTH_HOLDER = "EXTRA_AUTH_HOLDER"
        const val EXTRA_STEP = "EXTRA_STEP"
        const val EXTRA_MAX_STEPS = "EXTRA_MAX_STEPS"
        const val EXTRA_GROUPS = "EXTRA_GROUPS"
        const val RESULTS = 102

        fun newIntent(
            activity: Activity,
            authHolder: AuthHolder,
            currentStep: Int,
            maxSteps: Int,
            selectedGroups: List<Group>?
        ): Intent {
            val intent = Intent(activity, ContactsCopyActivity::class.java)
            intent.putExtra(EXTRA_AUTH_HOLDER, authHolder)
            intent.putExtra(EXTRA_STEP, currentStep)
            intent.putExtra(EXTRA_MAX_STEPS, maxSteps)
            if (selectedGroups != null)
                intent.putExtra(EXTRA_GROUPS, ArrayList(selectedGroups))
            return intent
        }
    }

    val authHolder by lazy<AuthHolder?> {
        intent.getParcelableExtra(EXTRA_AUTH_HOLDER)
    }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = CopyProgressFragment.newInstance(
                authHolder ?: throw IllegalStateException("AuthHolder missing")
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, fragment.TAG)
                .commitNow()
        }
    }
}
