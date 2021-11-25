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
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.SyncResult
import android.os.Bundle
import android.provider.CalendarContract
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import at.bitfire.ical4android.AndroidCalendar
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.resource.LocalCalendar
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level

abstract class CalendarsSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = CalendarsSyncAdapter(this)


    class CalendarsSyncAdapter(
        private val service: SyncAdapterService
    ): SyncAdapter(service) {

        override fun sync(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val accountSettings = AccountSettings(context, serviceEnvironments, account) {
                    service.onLoginException(authority, it)
                }

                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return
                syncWillRun(serviceEnvironments, account, extras, authority, provider, syncResult)

                if (accountSettings.getEventColors())
                    AndroidCalendar.insertColors(provider, account)
                else
                    AndroidCalendar.removeColors(provider, account)

                updateLocalCalendars(provider, account, accountSettings)

                val priorityCalendars = priorityCollections(extras)
                val calendars = AndroidCalendar
                    .find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)
                    .sortedByDescending { priorityCalendars.contains(it.id) }
                for (calendar in calendars) {
                    Logger.log.info("Synchronizing calendar #${calendar.id}, URL: ${calendar.name}")
                    CalendarSyncManager(
                        service.application,
                        serviceEnvironments,
                        account,
                        accountSettings,
                        extras,
                        authority,
                        syncResult,
                        calendar
                    ) {
                        loginExceptionOccurred(authority, it)
                    }.use {
                        it.performSync()
                    }
                }

            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync calendars", e)
            }

            syncDidRun(serviceEnvironments, account, extras, authority, provider, syncResult)
            Logger.log.info("Calendar sync complete")
        }

        private fun updateLocalCalendars(provider: ContentProviderClient, account: Account, settings: AccountSettings) {
            val db = AppDatabase.getInstance(context)
            val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)

            val remoteCalendars = mutableMapOf<HttpUrl, Collection>()
            if (service != null)
                for (collection in db.collectionDao().getSyncCalendars(service.id)) {
                    remoteCalendars[collection.url] = collection
                }

            // delete/update local calendars
            val updateColors = settings.getManageCalendarColors()
            for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null))
                calendar.name?.let {
                    val url = it.toHttpUrlOrNull()!!
                    val info = remoteCalendars[url]
                    if (info == null) {
                        Logger.log.log(Level.INFO, "Deleting obsolete local calendar", url)
                        calendar.delete()
                    } else {
                        // remote CollectionInfo found for this local collection, update data
                        Logger.log.log(Level.FINE, "Updating local calendar $url", info)
                        calendar.update(info, updateColors)
                        // we already have a local calendar for this remote collection, don't take into consideration anymore
                        remoteCalendars -= url
                    }
                }

            // create new local calendars
            for ((_, info) in remoteCalendars) {
                Logger.log.log(Level.INFO, "Adding local calendar", info)
                LocalCalendar.create(account, provider, info)
            }
        }

    }

}
