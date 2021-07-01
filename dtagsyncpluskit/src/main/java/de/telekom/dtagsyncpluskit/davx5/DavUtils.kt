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

package de.telekom.dtagsyncpluskit.davx5

import android.accounts.Account
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.getSystemService
import at.bitfire.ical4android.TaskProvider
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import okhttp3.HttpUrl
import org.xbill.DNS.*
import java.util.*

/**
 * Some WebDAV and related network utility methods
 */
object DavUtils {

    enum class SyncStatus {
        ACTIVE, PENDING, IDLE
    }


    @Suppress("FunctionName")
    fun ARGBtoCalDAVColor(colorWithAlpha: Int): String {
        val alpha = (colorWithAlpha shr 24) and 0xFF
        val color = colorWithAlpha and 0xFFFFFF
        return String.format(Locale.ROOT, "#%06X%02X", color, alpha)
    }


    fun lastSegmentOfUrl(url: HttpUrl): String {
        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        val segments = LinkedList<String>(url.pathSegments)
        segments.reverse()

        return segments.firstOrNull { it.isNotEmpty() } ?: "/"
    }


    fun prepareLookup(context: Context, lookup: Lookup) {
        @TargetApi(Build.VERSION_CODES.O)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* Since Android 8, the system properties net.dns1, net.dns2, ... are not available anymore.
               The current version of dnsjava relies on these properties to find the default name servers,
               so we have to add the servers explicitly (fortunately, there's an Android API to
               get the active DNS servers). */
            val connectivity = context.getSystemService<ConnectivityManager>()!!
            val activeLink = connectivity.getLinkProperties(connectivity.activeNetwork)
            if (activeLink != null) {
                // get DNS servers of active network link and set them for dnsjava so that it can send SRV queries
                val simpleResolvers = activeLink.dnsServers.map {
                    Logger.log.fine("Using DNS server ${it.hostAddress}")
                    val resolver = SimpleResolver()
                    resolver.setAddress(it)
                    resolver
                }
                val resolver = ExtendedResolver(simpleResolvers.toTypedArray())
                lookup.setResolver(resolver)
            } else
                Logger.log.severe("Couldn't determine DNS servers, dnsjava queries (SRV/TXT records) won't work")
        }
    }

    fun selectSRVRecord(records: Array<out Record>?): SRVRecord? {
        if (records == null)
            return null

        val srvRecords = records.filterIsInstance(SRVRecord::class.java)
        if (srvRecords.size <= 1)
            return srvRecords.firstOrNull()

        /* RFC 2782

           Priority
                The priority of this target host.  A client MUST attempt to
                contact the target host with the lowest-numbered priority it can
                reach; target hosts with the same priority SHOULD be tried in an
                order defined by the weight field. [...]

           Weight
                A server selection mechanism.  The weight field specifies a
                relative weight for entries with the same priority. [...]

                To select a target to be contacted next, arrange all SRV RRs
                (that have not been ordered yet) in any order, except that all
                those with weight 0 are placed at the beginning of the list.

                Compute the sum of the weights of those RRs, and with each RR
                associate the running sum in the selected order. Then choose a
                uniform random number between 0 and the sum computed
                (inclusive), and select the RR whose running sum value is the
                first in the selected order which is greater than or equal to
                the random number selected. The target host specified in the
                selected SRV RR is the next one to be contacted by the client.
        */
        val minPriority = srvRecords.map { it.priority }.minOrNull()
        val useableRecords = srvRecords.filter { it.priority == minPriority }.sortedBy { it.weight != 0 }

        val map = TreeMap<Int, SRVRecord>()
        var runningWeight = 0
        for (record in useableRecords) {
            val weight = record.weight
            runningWeight += weight
            map[runningWeight] = record
        }

        val selector = (0..runningWeight).random()
        return map.ceilingEntry(selector)!!.value
    }

    fun pathsFromTXTRecords(records: Array<Record>?): List<String> {
        val paths = LinkedList<String>()
        records?.filterIsInstance(TXTRecord::class.java)?.forEach { txt ->
            @Suppress("UNCHECKED_CAST")
            for (segment in txt.strings as List<String>)
                if (segment.startsWith("path=")) {
                    paths.add(segment.substring(5))
                    break
                }
        }
        return paths
    }


    /**
     * Returns the sync status of a given account. Checks the account itself and possible
     * sub-accounts (address book accounts).
     *
     * @param authorities sync authorities to check (usually taken from [syncAuthorities])
     *
     * @return sync status of the given account
     */
    fun accountSyncStatus(context: Context, authorities: Iterable<String>, account: Account): SyncStatus {
        // check active syncs
        if (authorities.any { ContentResolver.isSyncActive(account, it) })
            return SyncStatus.ACTIVE

        val addrBookAccounts = LocalAddressBook.findAll(context, null, account).map { it.account }
        if (addrBookAccounts.any { ContentResolver.isSyncActive(it, ContactsContract.AUTHORITY) })
            return SyncStatus.ACTIVE

        // check get pending syncs
        if (authorities.any { ContentResolver.isSyncPending(account, it) } ||
            addrBookAccounts.any { ContentResolver.isSyncPending(it, ContactsContract.AUTHORITY) })
            return SyncStatus.PENDING

        return SyncStatus.IDLE
    }

    /**
     * Requests an immediate, manual sync of all available authorities for the given account.
     *
     * @param account account to sync
     */
    fun requestSync(context: Context, account: Account) {
        for (authority in syncAuthorities(context)) {
            val extras = Bundle(2)
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
            ContentResolver.requestSync(account, authority, extras)
        }
    }

    /**
     * Returns a list of all available sync authorities for main accounts (!= address book accounts):
     *
     *   1. address books authority (not [ContactsContract.AUTHORITY], but the one which manages address book accounts)
     *   1. calendar authority
     *   1. tasks authority (if available)
     *
     * Checking the availability of authorities may be relatively expensive, so the
     * result should be cached for the current operation.
     *
     * @return list of available sync authorities for main accounts
     */
    fun syncAuthorities(context: Context): List<String> {
        val result = mutableListOf(
            context.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY
        )

        /*TaskUtils.currentProvider(context)?.let { taskProvider ->
            result += taskProvider.authority
        }*/

        return result
    }

}
