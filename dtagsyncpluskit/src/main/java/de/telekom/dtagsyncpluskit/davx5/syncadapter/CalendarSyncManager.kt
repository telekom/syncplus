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
import android.app.Application
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.DavResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.*
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.DavUtils
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.SyncState
import de.telekom.dtagsyncpluskit.davx5.resource.LocalCalendar
import de.telekom.dtagsyncpluskit.davx5.resource.LocalEvent
import de.telekom.dtagsyncpluskit.davx5.resource.LocalResource
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import net.fortuna.ical4j.model.Dur
import net.fortuna.ical4j.model.component.VAlarm
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT)
 */
class CalendarSyncManager(
    app: Application,
    serviceEnvironments: ServiceEnvironments,
    account: Account,
    accountSettings: AccountSettings,
    extras: Bundle,
    authority: String,
    syncResult: SyncResult,
    localCalendar: LocalCalendar,
    unauthorizedCallback: (account: Account) -> Unit
): SyncManager<LocalEvent, LocalCalendar, DavCalendar>(app, serviceEnvironments, account, accountSettings, extras, authority, syncResult, localCalendar) {

    private val mUnauthorizedCallback = unauthorizedCallback

    override fun prepare(): Boolean {
        collectionURL = (localCollection.name ?: return false).toHttpUrlOrNull() ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        localCollection.processDirtyExceptions()

        return true
    }

    override fun queryCapabilities(): SyncState? =
            useRemoteCollection {
                var syncState: SyncState? = null
                it.propfind(0, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                    if (relation == Response.HrefRelation.SELF) {
                        response[SupportedReportSet::class.java]?.let { supported ->
                            hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                        }

                        syncState = syncState(response)
                    }
                }

                Logger.log.info("Server supports Collection Sync: $hasCollectionSync")
                syncState
            }

    override fun syncAlgorithm() = if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
        SyncAlgorithm.PROPFIND_REPORT
            else
        SyncAlgorithm.COLLECTION_SYNC

    override fun prepareUpload(resource: LocalEvent): RequestBody = useLocal(resource) {
        val event = requireNotNull(resource.event)
        Logger.log.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

        val os = ByteArrayOutputStream()
        event.write(os)

        RequestBody.create(
                DavCalendar.MIME_ICALENDAR_UTF8,
                os.toByteArray()
        )
    }

    override fun listAllRemote(callback: DavResponseCallback) {
        // calculate time range limits
        var limitStart: Date? = null
        accountSettings.getTimeRangePastDays()?.let { pastDays ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -pastDays)
            limitStart = calendar.time
        }

        return useRemoteCollection { remote ->
            Logger.log.info("Querying events since $limitStart")
            remote.calendarQuery("VEVENT", limitStart, null, callback)
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        Logger.log.info("Downloading ${bunch.size} iCalendars: $bunch")
        if (bunch.size == 1) {
            val remote = bunch.first()
            // only one contact, use GET
            useRemote(DavResource(httpClient.okHttpClient, remote)) { resource ->
                resource.get(DavCalendar.MIME_ICALENDAR.toString()) { response ->
                    // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                    val eTag = response.header("ETag")?.let { GetETag(it).eTag }
                            ?: throw DavException("Received CalDAV GET response without ETag")

                    response.body!!.use {
                        processVEvent(resource.fileName(), eTag, it.charStream())
                    }
                }
            }
        } else
            // multiple iCalendars, use calendar-multi-get
            useRemoteCollection {
                it.multiget(bunch) { response, _ ->
                    useRemote(response) {
                        if (!response.isSuccess()) {
                            Logger.log.warning("Received non-successful multiget response for ${response.href}")
                            return@useRemote
                        }

                        val eTag = response[GetETag::class.java]?.eTag
                                ?: throw DavException("Received multi-get response without ETag")

                        val calendarData = response[CalendarData::class.java]
                        val iCal = calendarData?.iCalendar
                                ?: throw DavException("Received multi-get response without address data")

                        processVEvent(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(iCal))
                    }
                }
            }
    }

    override fun postProcess() {
    }


    // helpers

    private fun processVEvent(fileName: String, eTag: String, reader: Reader) {
        val events: List<Event>
        try {
            events = Event.eventsFromReader(reader)
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (events.size == 1) {
            val event = events.first()

            // set default reminder for non-full-day events, if requested
            val defaultAlarmMinBefore = accountSettings.getDefaultAlarm()
            if (defaultAlarmMinBefore != null && !event.isAllDay() && event.alarms.isEmpty()) {
                val alarm = VAlarm(Dur(0, 0, -defaultAlarmMinBefore, 0))
                Logger.log.log(Level.FINE, "${event.uid}: Adding default alarm", alarm)
                event.alarms += alarm
            }

            // update local event, if it exists
            useLocal(localCollection.findByName(fileName)) { local ->
                if (local != null) {
                    Logger.log.log(Level.INFO, "Updating $fileName in local calendar", event)
                    local.eTag = eTag
                    local.update(event)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.log(Level.INFO, "Adding $fileName to local calendar", event)
                    useLocal(LocalEvent(localCollection, event, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)) {
                        it.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_event)

    override fun onUnauthorized(account: Account) {
        mUnauthorizedCallback(account)
    }

}
