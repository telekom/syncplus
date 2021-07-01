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

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.telekom.dtagsyncpluskit.R

interface FragmentCallbacks {
    fun onFragmentPopped()
    fun onFragmentPushed()
}

open class BaseActivity : AppCompatActivity(), FragmentCallbacks {

    // Bookkeeping for backstack.
    private var backstackSize: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        if (!isTablet) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

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

    override fun onBackPressed() {
        supportFragmentManager.fragments.forEach {
            (it as? BaseFragment)?.onBackPressed()
        }
        super.onBackPressed()
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
        withAnimation: Boolean = true
    ) {
        val transaction = supportFragmentManager.beginTransaction()
        if (withAnimation) {
            transaction.setCustomAnimations(
                R.anim.in_right,
                R.anim.out_left,
                R.anim.in_left,
                R.anim.out_right
            )
        }

        transaction
            .replace(containerViewId, fragment)
            .addToBackStack(fragment.TAG)
            .commit()
    }
}
