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
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.syncplus.ui.main.DataPrivacyDialogActivity
import de.telekom.syncplus.ui.main.SetupFragment
import de.telekom.syncplus.ui.main.TopBarActivity
import de.telekom.syncplus.util.Prefs

class SetupActivity : TopBarActivity() {
    companion object {
        const val EXTRA_AUTH_HOLDER = "EXTRA_AUTH_HOLDER"
        fun newIntent(activity: Activity, authHolder: AuthHolder): Intent {
            val intent = Intent(activity, SetupActivity::class.java)
            intent.putExtra(EXTRA_AUTH_HOLDER, authHolder)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    val authHolder by extraNotNull<AuthHolder>(EXTRA_AUTH_HOLDER)

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, SetupFragment.newInstance())
                .commitNow()

            val prefs = Prefs(this)
            if (!prefs.consentDialogShown) {
                startActivity(DataPrivacyDialogActivity.newIntent(this))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as App).inSetup = true
    }

    override fun onPause() {
        super.onPause()
        (application as App).inSetup = false
    }

    override fun onFragmentPushed() {
        super.onFragmentPushed()
        authHolder.currentStep += 1
        topBar.currentStep = authHolder.currentStep
        topBar.maxSteps = authHolder.maxSteps
    }

    override fun onFragmentPopped() {
        super.onFragmentPopped()
        authHolder.currentStep -= 1
        topBar.currentStep = authHolder.currentStep
        topBar.maxSteps = authHolder.maxSteps

    }
}
