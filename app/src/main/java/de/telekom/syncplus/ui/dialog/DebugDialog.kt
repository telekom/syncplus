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

package de.telekom.syncplus.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.databinding.DialogDebugBinding
import de.telekom.syncplus.util.Prefs
import java.util.logging.Level

class DebugDialog : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDebugBinding.inflate(layoutInflater, null, false)

        val prefs = Prefs(requireContext())
        binding.loggingSwitch.isChecked = prefs.loggingEnabled
        binding.loggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.loggingEnabled = isChecked
            Toast.makeText(
                requireContext(),
                "Logging ${
                    if (isChecked) {
                        "enabled"
                    } else {
                        "disabled"
                    }
                }",
                Toast.LENGTH_SHORT,
            ).show()
        }

        val accountManager = IDMAccountManager(requireContext())

        binding.resetAccessTokenButton.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings = AccountSettings(requireContext(), it)
                Logger.log.info("Reset AccessToken for ${it.name}")
                val credentials = accountSettings.getCredentials()
                credentials.accessToken = "ACCESSTOKENRESET"
            }
            Toast.makeText(requireContext(), "Access Token Reset", Toast.LENGTH_SHORT).show()
        }

        binding.resetRefreshTokenButton.isEnabled = false
        binding.resetRefreshTokenButton.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings = AccountSettings(requireContext(), it)
                Logger.log.info("Reset RefreshToken for ${it.name}")
                val credentials = accountSettings.getCredentials()
                credentials.setRefreshToken("REFRESHTOKENRESET")
            }

            Toast.makeText(requireContext(), "Refresh Token Reset", Toast.LENGTH_SHORT).show()
        }

        binding.syncEverythingForAllAccounts.setOnClickListener {
            accountManager.getAccounts().forEach {
                val accountSettings = AccountSettings(requireContext(), it)
                accountSettings.resyncCalendars(true)
                Toast.makeText(
                    requireContext(),
                    "Sync started for ${it.name} for Calendar",
                    Toast.LENGTH_SHORT,
                ).show()
                accountSettings.resyncContacts(true)
                Toast.makeText(
                    requireContext(),
                    "Sync started for ${it.name} for Contacts",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        binding.loggerSpinner.setItems(
            "ALL",
            "FINEST",
            "FINER",
            "FINE",
            "CONFIG",
            "INFO",
            "WARNING",
            "SEVERE",
            "OFF",
        )
        when (Logger.log.level) {
            Level.ALL -> binding.loggerSpinner.selectedIndex = 0
            Level.FINEST -> binding.loggerSpinner.selectedIndex = 1
            Level.FINER -> binding.loggerSpinner.selectedIndex = 2
            Level.FINE -> binding.loggerSpinner.selectedIndex = 3
            Level.CONFIG -> binding.loggerSpinner.selectedIndex = 4
            Level.INFO -> binding.loggerSpinner.selectedIndex = 5
            Level.WARNING -> binding.loggerSpinner.selectedIndex = 6
            Level.SEVERE -> binding.loggerSpinner.selectedIndex = 7
            Level.OFF -> binding.loggerSpinner.selectedIndex = 8
        }
        binding.loggerSpinner.setOnItemSelectedListener { _, position, _, _ ->
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

        val dialog =
            MaterialAlertDialogBuilder(requireContext(), theme)
                .setCancelable(true)
                .setView(binding.root)
                .create()
        // override fun onBackPressed() {}
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        // dialog.setCancelable(true) // make it canceable, since no dismiss button is present
        return dialog
    }
}
