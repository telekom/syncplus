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
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.PeriodicSync
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.vcard4android.GroupMethod
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import de.telekom.dtagsyncpluskit.davx5.syncadapter.SyncAdapterService
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages settings of an account.
 */
@Suppress("unused")
class AccountSettings(
    val context: Context,
    val account: Account,
) {
    companion object {
        const val SYNC_INTERVAL_MANUALLY = -1L
    }

    private val mDB = AppDatabase.getInstance(context)
    private val am = IDMAccountManager(context)

    fun db(): AppDatabase = mDB

    fun isSetupCompleted(): Boolean {
        return am.isSetupCompleted(account)
    }

    fun setSetupCompleted(completed: Boolean) {
        am.setSetupCompleted(account, completed)
    }

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

    fun setSyncWiFiOnly(
        @Suppress("UNUSED_PARAMETER") wiFiOnly: Boolean,
    ) = Unit

    fun getSyncWifiOnlySSIDs(): List<String>? = null

    fun setSyncWifiOnlySSIDs(
        @Suppress("UNUSED_PARAMETER") ssids: List<String>?,
    ) = Unit

    fun getEventColors(): Boolean = false

    fun getManageCalendarColors(): Boolean = false

    fun getTimeRangePastDays(): Int = 90 // 90 days

    fun getDefaultAlarm(): Int? = null // no default alarm

    fun getGroupMethod(): GroupMethod {
        return am.getGroupMethod(account)
    }

    fun setGroupMethod(method: GroupMethod) {
        am.setGroupMethod(account, method)
    }

    fun tryGetSyncInterval(): Long? {
        Logger.log.info("tryGetSyncInterval")
        return am.getSyncInterval(account)
    }

    fun getSyncPeriod(authority: String): Long? {
        return ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period
    }

    fun getPeriodicSyncs(authority: String): List<PeriodicSync> {
        return ContentResolver.getPeriodicSyncs(account, authority)
    }
//
//    fun getSyncInterval(authority: String): Long? {
//        Logger.log.info("getSyncInterval($authority)")
//        if (ContentResolver.getIsSyncable(account, authority) <= 0) {
//            Logger.log.info("getIsSyncable <= 0")
//            return null
//        }
//
//        val syncAutomatically = ContentResolver.getSyncAutomatically(account, authority)
//        Logger.log.info("getSyncInterval | syncAutomatically = $syncAutomatically")
//        if (ContentResolver.getSyncAutomatically(account, authority)) {
//            val syncs = ContentResolver.getPeriodicSyncs(account, authority)
//            Logger.log.info("getSyncInterval | syncs = $syncs")
//        }
//
//        val retVal = if (ContentResolver.getSyncAutomatically(account, authority)) {
//            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period
//                ?: SYNC_INTERVAL_MANUALLY
//        } else {
//            SYNC_INTERVAL_MANUALLY
//        }
//
//        Logger.log.info("getSyncInterval | retVal = $retVal")
//
//        // If 'MANUAL' sync is enabled, we re-enable periodic sync.
//        if (retVal == SYNC_INTERVAL_MANUALLY) {
//            setSyncInterval(authority, DEFAULT_SYNC_INTERVAL)
//            return DEFAULT_SYNC_INTERVAL
//        }
//
//        return retVal
//    }

    fun setSyncInterval(
        authority: String,
        seconds: Long,
    ) {
        Logger.log.info("setSyncInterval($authority, $seconds)")

        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false)
            am.setSyncInterval(account, null)
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true)
            ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)
            am.setSyncInterval(account, seconds)
        }
    }

    fun getCredentials() = Credentials(
        context,
        account,
        ServiceEnvironments.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!),
    )

    fun lastSyncDate(authority: String): Pair<String, String>? {
        val synctime = getLastSyncTime(authority)
        Logger.log.info("lastSyncDate: $synctime")
        if (synctime == 0L) {
            return null
        }

        val date = Date(synctime)
        Logger.log.info("date: $date")
        val dayMonYear = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
        Logger.log.info("dayMonYear: $dayMonYear")
        if (dayMonYear.isNullOrBlank()) return null
        val hourMin = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        Logger.log.info("hourMin: $hourMin")
        if (hourMin.isNullOrBlank()) return null
        return Pair(dayMonYear, hourMin)
    }

    fun resyncCalendars(fullResync: Boolean) {
        resync(CalendarContract.AUTHORITY, fullResync)
    }

    fun resyncContacts(fullResync: Boolean) {
        resync(ContactsContract.AUTHORITY, fullResync)
    }

    private fun resync(
        authority: String,
        fullResync: Boolean,
    ) {
        val args = Bundle(1)
        args.putBoolean(
            if (fullResync) {
                SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC
            } else {
                SyncAdapterService.SYNC_EXTRAS_RESYNC
            },
            true,
        )

        ContentResolver.requestSync(account, authority, args)
    }

    @SuppressLint("PrivateApi")
    fun getLastSyncTime(authority: String): Long {
        var result: Long = 0
        try {
            val getSyncStatus: Method = ContentResolver::class.java.getMethod(
                "getSyncStatus",
                Account::class.java,
                String::class.java,
            )
            val status = getSyncStatus.invoke(null, account, authority)
            val statusClass = Class.forName("android.content.SyncStatusInfo")
            val isStatusObject = statusClass.isInstance(status)
            Logger.log.info("isStatusObject: $isStatusObject")
            if (isStatusObject) {
                val successTime: Field = statusClass.getField("lastSuccessTime")
                result = successTime.getLong(status)
            }
        } catch (e: Exception) {
            CountlyWrapper.recordUnhandledException(e)
            Logger.log.info("Error: Getting last sync time: $e")
            Logger.log.severe("Error: Getting last sync time: $e")
        }

        return result
    }
}
