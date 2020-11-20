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
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.Prefs
import kotlinx.android.synthetic.main.dialog_debug.view.*
import java.util.logging.Level

class DebugDialog(private val context1: Context) : DialogFragment() {

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_debug, null)

        val prefs = Prefs(context1)
        view.loggingSwitch.isChecked = prefs.loggingEnabled
        view.loggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.loggingEnabled = isChecked
            Toast.makeText(
                context1,
                "Logging ${if (isChecked) {
                    "enabled"
                } else {
                    "disabled"
                }}",
                Toast.LENGTH_SHORT
            ).show()

        }


        val serviceEnvironments = App.serviceEnvironments(context1)
        val onUnauthorized = DavNotificationUtils.reloginCallback(context1, "authority")
        val accountManager = IDMAccountManager(context1, onUnauthorized)

        view.resetAccessTokenButton.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings =
                    AccountSettings(context1, serviceEnvironments, it, onUnauthorized)
                Logger.log.info("Reset AccessToken for ${it.name}")
                val credentials = accountSettings.getCredentials()
                credentials.accessToken = "ACCESSTOKENRESET"
            }
            Toast.makeText(context1, "Access Token Reset", Toast.LENGTH_SHORT).show()
        }

        view.resetRefreshTokenButton.isEnabled = false
        view.resetRefreshTokenButton.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings =
                    AccountSettings(context1, serviceEnvironments, it, onUnauthorized)
                Logger.log.info("Reset RefreshToken for ${it.name}")
                val credentials = accountSettings.getCredentials()
                credentials.setRefreshToken("REFRESHTOKENRESET")
            }

            Toast.makeText(context1, "Refresh Token Reset", Toast.LENGTH_SHORT).show()
        }

        view.syncEverythingForAllAccounts.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings =
                    AccountSettings(context1, serviceEnvironments, it, onUnauthorized)
                accountSettings.resyncCalendars(true)
                Toast.makeText(
                    context1,
                    "Sync started for ${it.name} for Calendar",
                    Toast.LENGTH_SHORT
                ).show()
                accountSettings.resyncContacts(true)
                Toast.makeText(
                    context1,
                    "Sync started for ${it.name} for Contacts",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        view.loggerSpinner.setItems(
            "ALL",
            "FINEST",
            "FINER",
            "FINE",
            "CONFIG",
            "INFO",
            "WARNING",
            "SEVERE",
            "OFF"
        )
        when (Logger.log.level) {
            Level.ALL -> view.loggerSpinner.selectedIndex = 0
            Level.FINEST -> view.loggerSpinner.selectedIndex = 1
            Level.FINER -> view.loggerSpinner.selectedIndex = 2
            Level.FINE -> view.loggerSpinner.selectedIndex = 3
            Level.CONFIG -> view.loggerSpinner.selectedIndex = 4
            Level.INFO -> view.loggerSpinner.selectedIndex = 5
            Level.WARNING -> view.loggerSpinner.selectedIndex = 6
            Level.SEVERE -> view.loggerSpinner.selectedIndex = 7
            Level.OFF -> view.loggerSpinner.selectedIndex = 8
        }
        view.loggerSpinner.setOnItemSelectedListener { _, position, _, _ ->
            when (position) {
                0 -> Logger.log.level = Level.ALL
                1 -> Logger.log.level = Level.FINEST
                2 -> Logger.log.level = Level.FINER
                3 -> Logger.log.level = Level.FINE
                4 -> Logger.log.level = Level.CONFIG
                5 -> Logger.log.level = Level.INFO
                6 -> Logger.log.level = Level.WARNING
                7 -> Logger.log.level = Level.SEVERE
                8 -> Logger.log.level = Level.OFF
            }
        }

        /*
        var loggerLevel = HttpLoggingInterceptor.Level.NONE
        view.httpLoggerSpinner.setItems("NONE", "BASIC", "HEADERS", "BODY")
        when (loggerLevel) {
            HttpLoggingInterceptor.Level.NONE -> view.httpLoggerSpinner.selectedIndex = 0
            HttpLoggingInterceptor.Level.BASIC -> view.httpLoggerSpinner.selectedIndex = 1
            HttpLoggingInterceptor.Level.HEADERS -> view.httpLoggerSpinner.selectedIndex = 2
            HttpLoggingInterceptor.Level.BODY -> view.httpLoggerSpinner.selectedIndex = 3
        }
        view.httpLoggerSpinner.setOnItemSelectedListener { _, position, id, item ->
            when (position) {
                0 -> loggerLevel = HttpLoggingInterceptor.Level.NONE
                1 -> loggerLevel = HttpLoggingInterceptor.Level.BASIC
                2 -> loggerLevel = HttpLoggingInterceptor.Level.HEADERS
                3 -> loggerLevel = HttpLoggingInterceptor.Level.BODY
            }
        }
        */

        val dialog = object : Dialog(requireContext(), theme) {
            //override fun onBackPressed() {}
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        //dialog.setCancelable(true) // make it canceable, since no dismiss button is present
        dialog.setContentView(view)
        return dialog
    }
}
