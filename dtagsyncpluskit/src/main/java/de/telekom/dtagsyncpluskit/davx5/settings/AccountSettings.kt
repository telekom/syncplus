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

package de.telekom.dtagsyncpluskit.davx5.settings

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import at.bitfire.vcard4android.GroupMethod
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.davx5.syncadapter.SyncAdapterService
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages settings of an account.
 *
 * @throws InvalidAccountException on construction when the account doesn't exist (anymore)
 */
@Suppress("unused")
class AccountSettings(
    val context: Context,
    val serviceEnvironments: ServiceEnvironments,
    val account: Account,
    onUnauthorized: ((account: Account) -> Unit)?
) {

    companion object {
        const val SYNC_INTERVAL_MANUALLY = -1L
    }

    private val mDB = AppDatabase.getInstance(context)
    private val am = IDMAccountManager(context, onUnauthorized)

    fun db(): AppDatabase = mDB

    fun isCalendarSyncEnabled(): Boolean {
        return am.isCalendarSyncEnabled(account)
    }

    fun isContactSyncEnabled(): Boolean {
        return am.isContactSyncEnabled(account)
    }

    // THIS DOES NOT DISABLE SYNCING OF STILL ENABLED CALENDARS
    fun setCalendarSyncEnabled(enabled: Boolean) {
        am.setCalendarSyncEnabled(account, enabled)
    }

    fun setContactSyncEnabled(enabled: Boolean) {
        am.setContactSyncEnabled(account, enabled)
    }

    suspend fun setSyncAllCalendars(enabled: Boolean) =
        am.setAllCalendarsSyncEnabled(account, enabled)

    suspend fun setSyncAllAddressBooks(enabled: Boolean) =
        am.setAllAddressBookSyncEnabled(account, enabled)


    fun getSyncWifiOnly(): Boolean = false
    fun setSyncWiFiOnly(@Suppress("UNUSED_PARAMETER") wiFiOnly: Boolean) = Unit
    fun getSyncWifiOnlySSIDs(): List<String>? = null
    fun setSyncWifiOnlySSIDs(@Suppress("UNUSED_PARAMETER") ssids: List<String>?) = Unit

    fun getEventColors(): Boolean = false
    fun getManageCalendarColors(): Boolean = false

    fun getTimeRangePastDays(): Int? = 90 // 90 days

    fun getDefaultAlarm(): Int? = null // no default alarm

    fun getGroupMethod(): GroupMethod {
        return am.getGroupMethod(account)
    }

    fun setGroupMethod(method: GroupMethod) {
        am.setGroupMethod(account, method)
    }

    fun getSyncInterval(authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        return if (ContentResolver.getSyncAutomatically(account, authority)) {
            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period
                ?: SYNC_INTERVAL_MANUALLY
        } else {
            SYNC_INTERVAL_MANUALLY
        }
    }

    fun setSyncInterval(authority: String, seconds: Long) {
        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false)
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true)
            ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)
        }
    }

    fun getCredentials() = Credentials(
        context,
        account,
        serviceEnvironments
    )

    fun lastSyncDate(): String {
        val currentSyncs = ContentResolver.getCurrentSyncs().filter { it.account == account }
        if (currentSyncs.count() > 0) {
            val date = Calendar.getInstance().time
            return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
        }

        val date = Calendar.getInstance().time
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
    }

    fun resyncCalendars(fullResync: Boolean) {
        resync(CalendarContract.AUTHORITY, fullResync)
    }

    fun resyncContacts(fullResync: Boolean) {
        resync(context.getString(R.string.address_books_authority), fullResync)
    }

    private fun resync(authority: String, fullResync: Boolean) {
        val args = Bundle(1)
        args.putBoolean(
            if (fullResync)
                SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC
            else
                SyncAdapterService.SYNC_EXTRAS_RESYNC, true
        )

        ContentResolver.requestSync(account, authority, args)
    }
}
