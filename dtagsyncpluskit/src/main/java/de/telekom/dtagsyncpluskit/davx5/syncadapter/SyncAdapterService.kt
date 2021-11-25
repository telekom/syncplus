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

/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.telekom.dtagsyncpluskit.davx5.syncadapter

import android.accounts.Account
import android.app.Service
import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import java.lang.ref.WeakReference
import java.util.logging.Level

abstract class SyncAdapterService : Service() {

    companion object {
        /** Keep a list of running syncs to block multiple calls at the same time,
         *  like run by some devices. Weak references are used for the case that a thread
         *  is terminated and the `finally` block which cleans up [runningSyncs] is not
         *  executed. */
        private val runningSyncs = mutableListOf<WeakReference<Pair<String, Account>>>()

        /**
         * Specifies an list of IDs which are requested to be synchronized before
         * the other collections. For instance, if some calendars of a CalDAV
         * account are visible in the calendar app and others are hidden, the visible calendars can
         * be synchronized first, so that the "Refresh" action in the calendar app is more responsive.
         *
         * Extra type: String (comma-separated list of IDs)
         *
         * In case of calendar sync, the extra value is a list of Android calendar IDs.
         * In case of task sync, the extra value is an a list of OpenTask task list IDs.
         */
        const val SYNC_EXTRAS_PRIORITY_COLLECTIONS = "priority_collections"

        /**
         * Requests a re-synchronization of all entries. For instance, if this extra is
         * set for a calendar sync, all remote events will be listed and checked for remote
         * changes again.
         *
         * Useful if settings which modify the remote resource list (like the CalDAV setting
         * "sync events n days in the past") have been changed.
         */
        const val SYNC_EXTRAS_RESYNC = "resync"

        /**
         * Requests a full re-synchronization of all entries. For instance, if this extra is
         * set for an address book sync, all contacts will be downloaded again and updated in the
         * local storage.
         *
         * Useful if settings which modify parsing/local behavior have been changed.
         */
        const val SYNC_EXTRAS_FULL_RESYNC = "full_resync"

        /* Migrated from the companion object from the SyncAdapter */
        fun priorityCollections(extras: Bundle): Set<Long> {
            val ids = mutableSetOf<Long>()
            extras.getString(SYNC_EXTRAS_PRIORITY_COLLECTIONS)?.let { rawIds ->
                for (rawId in rawIds.split(','))
                    try {
                        ids += rawId.toLong()
                    } catch (e: NumberFormatException) {
                        Logger.log.log(
                            Level.WARNING,
                            "Couldn't parse SYNC_EXTRAS_PRIORITY_COLLECTIONS",
                            e
                        )
                    }
            }
            return ids
        }
    }

    protected abstract fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!

    abstract class SyncAdapter(
        private val service: SyncAdapterService
    ) : AbstractThreadedSyncAdapter(service.applicationContext, false) {

        abstract fun sync(
            serviceEnvironments: ServiceEnvironments,
            account: Account,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            syncResult: SyncResult
        )

        override fun onPerformSync(
            account: Account,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            Logger.log.log(
                Level.INFO,
                "$authority sync of $account has been initiated",
                extras.keySet().joinToString(", ")
            )
            val redirectUri = Uri.parse(context.getString(R.string.REDIRECT_URI))
            val serviceEnvironments = ServiceEnvironments
                .fromBuildConfig(
                    redirectUri,
                    de.telekom.dtagsyncpluskit.BuildConfig.ENVIRON[de.telekom.dtagsyncpluskit.BuildConfig.FLAVOR]!!
                )

            // prevent multiple syncs of the same authority to be run for the same account
            val currentSync = Pair(authority, account)
            synchronized(runningSyncs) {
                if (runningSyncs.any { it.get() == currentSync }) {
                    Logger.log.warning("There's already another $authority sync running for $account, aborting")
                    return
                }
                runningSyncs += WeakReference(currentSync)
            }

            try {
                // required for dav4jvm (ServiceLoader)
                Thread.currentThread().contextClassLoader = context.classLoader

                SyncManager.cancelNotifications(
                    NotificationManagerCompat.from(context),
                    authority,
                    account
                )
                sync(serviceEnvironments, account, extras, authority, provider, syncResult)
            } finally {
                synchronized(runningSyncs) {
                    runningSyncs.removeAll { it.get() == null || it.get() == currentSync }
                }
            }

            Logger.log.info("Sync for $currentSync finished")
        }

        override fun onSecurityException(
            account: Account,
            extras: Bundle,
            authority: String,
            syncResult: SyncResult
        ) {
            Logger.log.log(
                Level.WARNING,
                "Security exception when opening content provider for $authority"
            )
            syncResult.databaseError = true
            securityExceptionOccurred(account, syncResult)
        }

        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.getSyncWifiOnly()) {
                // WiFi required
                val connectivityManager =
                    context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

                // check for connected WiFi network
                var wifiAvailable = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    connectivityManager.allNetworks.forEach { network ->
                        connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            )
                                wifiAvailable = true
                        }
                    }
                } else {
                    val network = connectivityManager.activeNetworkInfo
                    if (network?.isConnected == true && network.type == ConnectivityManager.TYPE_WIFI)
                        wifiAvailable = true
                }
                if (!wifiAvailable) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }
                // if execution reaches this point, we're on a connected WiFi

                /*settings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                    // getting the WiFi name requires location permission (and active location services) since Android 8.1
                    // see https://issuetracker.google.com/issues/70633700
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(context, SettingsActivity::class.java)
                        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, settings.account)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        notifyPermissions(intent)
                    }

                    val wifi = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val info = wifi.connectionInfo
                    if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                        Logger.log.info("Connected to wrong WiFi network (${info.ssid}), ignoring")
                        return false
                    }
                }*/
            }
            return true
        }

        protected fun securityExceptionOccurred(account: Account, syncResult: SyncResult) {
            Logger.log.finest("securityExceptionOccurred")
            service.onSecurityException(account, syncResult)
        }

        protected fun loginExceptionOccurred(authority: String, account: Account) {
            Logger.log.finest("loginExceptionOccurred")
            service.onLoginException(authority, account)
        }

        protected fun syncWillRun(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            service.onSyncWillRun(serviceEnvironments, account, extras, authority, provider, syncResult)
        }

        protected fun syncDidRun(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            service.onSyncWillRun(serviceEnvironments, account, extras, authority, provider, syncResult)
        }
    }

    abstract fun onSecurityException(account: Account, syncResult: SyncResult)
    abstract fun onLoginException(authority: String, account: Account)
    abstract fun onSyncWillRun(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)
    abstract fun onSyncDidRun(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)
}
