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

package de.telekom.dtagsyncpluskit.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentActivity
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper

interface FragmentCallbacks {
    fun onFragmentPopped()

    fun onFragmentPushed()
}

open class BaseActivity : FragmentActivity, FragmentCallbacks {
    // Bookkeeping for backstack.
    private var backstackSize: Int = 0

    constructor() : super()
    constructor(
        @LayoutRes contentLayoutId: Int,
    ) : super(contentLayoutId)

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        super.onCreate(savedInstanceState)
        backstackSize = supportFragmentManager.backStackEntryCount
        supportFragmentManager.addOnBackStackChangedListener {
            val currentCount = supportFragmentManager.backStackEntryCount
            if (currentCount > backstackSize) {
                onFragmentPushed()
            } else if (currentCount < backstackSize) {
                onFragmentPopped()
            }

            // Update backstackSize after each change.
            backstackSize = supportFragmentManager.backStackEntryCount
        }
    }

    override fun onStart() {
        super.onStart()
        CountlyWrapper.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        CountlyWrapper.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        CountlyWrapper.onConfigurationChanged(newConfig)
    }

    override fun onFragmentPopped() {
    }

    override fun onFragmentPushed() {
    }

    open fun popFragment(): Boolean {
        return supportFragmentManager.popBackStackImmediate()
    }

    open fun <T : BaseFragment> pushFragment(
        containerViewId: Int,
        fragment: T,
        withAnimation: Boolean = true,
    ) {
        val transaction = supportFragmentManager.beginTransaction()
        if (withAnimation) {
            transaction.setCustomAnimations(
                R.anim.in_right,
                R.anim.out_left,
                R.anim.in_left,
                R.anim.out_right,
            )
        }

        transaction
            .replace(containerViewId, fragment, fragment.TAG)
            .addToBackStack(fragment.TAG)
            .commit()
    }
}
