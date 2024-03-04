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
import androidx.annotation.LayoutRes
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

abstract class BaseFragment : Fragment {
    abstract val TAG: String

    constructor() : super()
    constructor(
        @LayoutRes contentLayoutId: Int,
    ) : super(contentLayoutId)

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

    protected fun <T> instanceState(defaultValue: T) = InstanceStateProvider.NotNull<T>(savable, defaultValue)

    fun finish() {
        supportFragmentManager?.popBackStackImmediate()
    }

    fun finishActivity() {
        requireActivity().finish()
    }

    fun finishWithResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        baseActivity?.setResult(resultCode, intent ?: Intent())
        baseActivity?.finish()
    }

    fun <T : BaseFragment> push(
        containerViewId: Int,
        fragment: T,
        withAnimation: Boolean = true,
    ) {
        baseActivity?.pushFragment(containerViewId, fragment, withAnimation)
    }

    fun requirePermission(
        permission: String,
        callback: (granted: Boolean, report: MultiplePermissionsReport?) -> Unit,
    ) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return callback(true, null)
        }

        requestPermissions(permission) { granted, error, report ->
            if (error != null) {
                Logger.log.severe("Error: Requesting Permission: ${error.message}")
                callback(false, report)
                return@requestPermissions
            }

            callback(granted, report)
        }
    }

    fun requestPermissions(
        vararg permissions: String,
        callback: (granted: Boolean, error: Error?, report: MultiplePermissionsReport?) -> Unit,
    ) {
        requestPermissions(listOf(*permissions), callback)
    }

    fun requestPermissions(
        permissions: List<String>,
        callback: (granted: Boolean, error: Error?, report: MultiplePermissionsReport?) -> Unit,
    ) {
        if (permissions.isEmpty()) {
            return callback(true, null, null)
        }

        val listener =
            object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    when {
                        report == null -> callback(false, Error("Report is null"), report)
                        report.areAllPermissionsGranted() -> callback(true, null, null)
                        else -> callback(false, null, report)
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?,
                ) {
                    token?.continuePermissionRequest()
                }
            }

        val errorListener =
            PermissionRequestErrorListener {
                callback(false, Error(it.toString()), null)
            }

        Dexter.withActivity(activity)
            .withPermissions(permissions)
            .withListener(listener)
            .withErrorListener(errorListener)
            .onSameThread()
            .check()
    }
}
