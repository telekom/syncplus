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

@file:Suppress("unused", "UNUSED_PARAMETER")

package de.telekom.dtagsyncpluskit.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.PermissionRequestErrorListener
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.utils.InstanceStateProvider

interface OnBackPressed {
    fun onBackPressed()
}

abstract class BaseFragment : Fragment(), OnBackPressed {
    abstract val TAG: String

    override fun onBackPressed() {}

    protected val supportFragmentManager: FragmentManager?
        get() = activity?.supportFragmentManager

    protected val baseActivity: BaseActivity?
        get() = activity as? BaseActivity

    private val savable = Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            savable.putAll(savedInstanceState.getBundle("_state"))
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("_state", savable)
        super.onSaveInstanceState(outState)
    }

    protected fun <T> instanceState() = InstanceStateProvider.Nullable<T>(savable)
    protected fun <T> instanceState(defaultValue: T) =
        InstanceStateProvider.NotNull<T>(savable, defaultValue)

    fun finish() {
        supportFragmentManager?.popBackStackImmediate()
    }

    fun finishActivity() {
        requireActivity().finish()
    }

    fun finishWithResult(resultCode: Int, intent: Intent?) {
        baseActivity?.setResult(resultCode, intent ?: Intent())
        baseActivity?.finish()
    }

    fun <T : BaseFragment> push(containerViewId: Int, fragment: T, withAnimation: Boolean = true) {
        baseActivity?.pushFragment(containerViewId, fragment, withAnimation)
    }

    fun requirePermission(permission: String, callback: (granted: Boolean) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return callback(true)
        }

        requestPermissions(permission) { granted, error ->
            if (error != null) {
                Logger.log.severe("Error: Requesting Permission: ${error.message}")
                callback(false)
                return@requestPermissions
            }

            callback(granted)
        }
    }

    fun requestPermissions(
        vararg permissions: String,
        callback: (granted: Boolean, error: Error?) -> Unit
    ) {
        requestPermissions(listOf(*permissions), callback)
    }

    fun requestPermissions(
        permissions: List<String>,
        callback: (granted: Boolean, error: Error?) -> Unit
    ) {
        if (permissions.count() == 0) {
            return callback(true, null)
        }

        val listener = object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                when {
                    report == null -> callback(false, Error("Report is null"))
                    report.areAllPermissionsGranted() -> callback(true, null)
                    else -> callback(false, null)
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<PermissionRequest>?,
                token: PermissionToken?
            ) {
                token?.continuePermissionRequest()
            }
        }

        val errorListener = PermissionRequestErrorListener {
            callback(false, Error(it.toString()))
        }

        Dexter.withActivity(activity)
            .withPermissions(permissions)
            .withListener(listener)
            .withErrorListener(errorListener)
            .onSameThread()
            .check()
    }
}
