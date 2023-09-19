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

import android.Manifest
import android.accounts.AccountManagerFuture
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.extensions.isPermissionGranted
import de.telekom.syncplus.ui.main.WelcomeFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WelcomeActivity : BaseActivity() {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationPermissionRationale()
                    } else {
                        showSettingDialog()
                    }
                }
            } else {
                continueWithApp()
            }
        }

    companion object {
        private const val ARG_OVERRIDE = "ARG_OVERRIDE"
        fun newIntent(
            activity: Activity,
            noRedirect: Boolean = false,
            clear: Boolean = false
        ): Intent {
            val intent = Intent(activity, WelcomeActivity::class.java)
            intent.putExtra(ARG_OVERRIDE, noRedirect)
            if (clear)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    private val viewModel by viewModels<WelcomeViewModel>()

    private val mNoRedirect by extraNotNull(ARG_OVERRIDE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            continueWithApp()
            return
        }

        if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            continueWithApp()
            return
        }

        // If permissions were previously requested but not granted, proceed to the app
        if (viewModel.wasPermissionsRequested) {
            continueWithApp()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showSettingDialog() {
        viewModel.wasPermissionsRequested = true
        MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.MaterialAlertDialog_Material3
        )
            .setTitle(R.string.notification_permission_dialog_setting_title)
            .setMessage(R.string.notification_permission_dialog_setting_message)
            .setPositiveButton(R.string.notification_permission_dialog_setting_positive_action) { _, _ ->
                val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton(R.string.notification_permission_dialog_setting_negative_action) { _, _ ->
                continueWithApp()
            }
            .show()
    }

    private fun showNotificationPermissionRationale() {
        MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.MaterialAlertDialog_Material3
        )
            .setMessage(R.string.notification_permission_dialog_rationale_message)
            .setPositiveButton(R.string.notification_permission_dialog_rationale_positive_action) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton(R.string.notification_permission_dialog_rationale_negative_action) { _, _ ->
                continueWithApp()
            }
            .show()
    }

    private fun continueWithApp() {
        val accountManager =
            IDMAccountManager(this, DavNotificationUtils.reloginCallback(this, "authority"))

        // Verify that all accounts have been setup completely.
        val futures = ArrayList<AccountManagerFuture<Bundle>>()
        for (account in accountManager.getAccounts()) {
            if (!accountManager.isSetupCompleted(account)) {
                Logger.log.info("Account deleted: $account")
                futures.add(accountManager.removeAccountAsync(account, this))
            }
        }

        val accountsDeleted = futures.size > 0
        val next = {
            Logger.log.info("Accounts: ${accountManager.getAccounts().map { it.name }}")
            if (!mNoRedirect && accountManager.getAccounts().isNotEmpty()) {
                startActivity(
                    AccountsActivity.newIntent(
                        this,
                        newAccountCreated = false,
                        energySaving = false,
                        accountDeleted = accountsDeleted
                    )
                )
            } else {
                val transact = supportFragmentManager.beginTransaction()
                transact.replace(R.id.container, WelcomeFragment.newInstance(accountsDeleted))
                if (!supportFragmentManager.isDestroyed)
                    transact.commitNow()
            }
        }

        scope.launch {
            futures.forEach { it.result }
            runOnUiThread(next)
        }
    }

}
