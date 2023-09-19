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

package de.telekom.dtagsyncpluskit.utils

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.Constants
import de.telekom.dtagsyncpluskit.davx5.InvalidAccountException
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.*
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.syncadapter.DavService
import de.telekom.dtagsyncpluskit.davx5.ui.DavResourceFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.logging.Level
import kotlin.collections.forEach
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("unused")
class IDMAccountManager(
    val context: Context,
    private val onUnauthorized: ((account: Account) -> Unit)?
) : DavService.RefreshingStatusListener {
    companion object {
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_CONTACT_GROUP_METHOD = "group_method"
        const val KEY_CAL_ENABLED = "calEnabled"
        const val KEY_CARD_ENABLED = "cardEnabled"
        const val KEY_SETUP_COMPLETED = "setupCompleted"
    }

    private val accountManager: AccountManager = AccountManager.get(context)
    private val accountType: String = context.getString(R.string.account_type)

    suspend fun createAccount(
        app: Application,
        serviceEnvironments: ServiceEnvironments,
        email: String,
        password: String,
        loginHint: String,
        authToken: String,
        calEnabled: Boolean,
        cardEnabled: Boolean
    ) = withContext(Dispatchers.IO) func@{
        val account = Account(email, accountType)
        val bundle = Bundle(5)
        bundle.putString(KEY_ID_TOKEN, loginHint)
        bundle.putString(KEY_REFRESH_TOKEN, password)
        bundle.putString(KEY_CAL_ENABLED, calEnabled.toString())
        bundle.putString(KEY_CARD_ENABLED, cardEnabled.toString())
        bundle.putString(KEY_SETUP_COMPLETED, false.toString())
        // Did SWAP password and authToken
        if (!accountManager.addAccountExplicitly(account, authToken, bundle))
            return@func null

        //accountManager.setAuthToken(account, "full_access", password)
        val credentials = Credentials(context, account, serviceEnvironments)
        discoverServicesConfiguration(account, serviceEnvironments, calEnabled, cardEnabled)
        return@func credentials
    }

    suspend fun discoverServicesConfiguration(
        account: Account,
        serviceEnvironments: ServiceEnvironments,
        calendarSyncEnabled: Boolean,
        contactSyncEnabled: Boolean
    ): DavResourceFinder.Configuration? {
        val credentials = Credentials(context, account, serviceEnvironments)
        val configuration = detectConfiguration(context, credentials) ?: return null
        if (configuration.calDAV == null || configuration.cardDAV == null) return null
        addToServiceDB(
            account,
            serviceEnvironments,
            configuration,
            calendarSyncEnabled,
            contactSyncEnabled
        )
        setCalendarSyncEnabled(account, calendarSyncEnabled)
        setContactSyncEnabled(account, contactSyncEnabled)
        return configuration
    }

    @Suppress("DEPRECATION")
    fun removeAccount(account: Account, activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            accountManager.removeAccount(account, activity, {}, null)
        } else {
            accountManager.removeAccount(account, {}, null)
        }
        return true
    }

    fun removeAccountAsync(account: Account, activity: Activity): AccountManagerFuture<Bundle> {
        return accountManager.removeAccount(account, activity, null, null)
    }

    fun getAccounts(): Array<Account> {
        return accountManager.getAccountsByType(accountType)
    }

    fun setGroupMethod(account: Account, method: GroupMethod) {
        accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }

    fun getGroupMethod(account: Account): GroupMethod {
        val name = accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null) {
            try {
                return GroupMethod.valueOf(name)
            } catch (e: IllegalArgumentException) {
                CountlyWrapper.recordHandledException(e)
            }
        }

        return GroupMethod.GROUP_VCARDS
    }

    fun isCalendarSyncEnabled(account: Account): Boolean {
        return when (accountManager.getUserData(account, KEY_CAL_ENABLED)) {
            "true" -> true
            "false" -> false
            else -> false
        }
    }

    fun setCalendarSyncEnabled(account: Account, enabled: Boolean) {
        accountManager.setUserData(account, KEY_CAL_ENABLED, enabled.toString())
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, if (enabled) 1 else 0)
    }

    fun isContactSyncEnabled(account: Account): Boolean {
        return when (accountManager.getUserData(account, KEY_CARD_ENABLED)) {
            "true" -> true
            "false" -> false
            else -> false
        }
    }

    fun setContactSyncEnabled(account: Account, enabled: Boolean) {
        accountManager.setUserData(account, KEY_CARD_ENABLED, enabled.toString())
        ContentResolver.setIsSyncable(
            account,
            context.getString(R.string.address_books_authority),
            if (enabled) 1 else 0
        )
    }

    fun setSetupCompleted(account: Account, completed: Boolean) {
        accountManager.setUserData(account, KEY_SETUP_COMPLETED, completed.toString())
    }

    fun isSetupCompleted(account: Account): Boolean {
        return when (accountManager.getUserData(account, KEY_SETUP_COMPLETED)) {
            "true" -> true
            "false" -> false
            // Default to "true", rather than "false" to prevent already created accounts from
            // being tagged as "incomplete".
            else -> true
        }
    }

    suspend fun setAllCalendarsSyncEnabled(account: Account, enabled: Boolean) =
        withContext<Unit>(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CALDAV)
                ?.let { serviceId ->
                    db.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_CALENDAR)
                        .forEach { collection ->
                            val newCollection = collection.copy(sync = enabled)
                            db.collectionDao().update(newCollection)
                        }
                }
        }

    suspend fun setAllAddressBookSyncEnabled(account: Account, enabled: Boolean) =
        withContext<Unit>(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CARDDAV)
                ?.let { serviceId ->
                    db.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_ADDRESSBOOK)
                        .forEach { collection ->
                            val newCollection = collection.copy(sync = enabled)
                            db.collectionDao().update(newCollection)
                        }
                }
        }

    private suspend fun detectConfiguration(context: Context, credentials: Credentials) =
        suspendCoroutine<DavResourceFinder.Configuration?> { cont ->
            DavResourceFinder(context, credentials).use {
                cont.resume(it.findInitialConfiguration())
            }
        }

    private fun addToServiceDB(
        account: Account,
        serviceEnvironments: ServiceEnvironments,
        config: DavResourceFinder.Configuration,
        calendarSyncEnabled: Boolean,
        contactSyncEnabled: Boolean
    ) {
        val db = AppDatabase.getInstance(context)
        try {
            val name = account.name
            val accountSettings = AccountSettings(context, serviceEnvironments, account, onUnauthorized)
            if (config.cardDAV != null) {
                // insert CardDAV service

                val id = insertService(db, name, Service.TYPE_CARDDAV, config.cardDAV)

                // initial CardDAV account settings
                accountSettings.setGroupMethod(GroupMethod.CATEGORIES) // TODO: Other Group Method?

                // start CardDAV service detection (refresh collections)
                DavService.refreshCollections(context, id, serviceEnvironments, contactSyncEnabled)

                // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_address_books.xml
                accountSettings.setSyncInterval(
                    context.getString(R.string.address_books_authority),
                    Constants.DEFAULT_SYNC_INTERVAL
                )
            } else {
                ContentResolver.setIsSyncable(
                    account,
                    context.getString(R.string.address_books_authority),
                    0
                )
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                val id = insertService(db, name, Service.TYPE_CALDAV, config.calDAV)

                // start CalDAV service detection (refresh collections)
                DavService.refreshCollections(context, id, serviceEnvironments, calendarSyncEnabled)

                // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_calendars.xml
                accountSettings.setSyncInterval(
                    CalendarContract.AUTHORITY,
                    Constants.DEFAULT_SYNC_INTERVAL
                )
            } else {
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)
                ContentResolver.setIsSyncable(
                    account,
                    TaskProvider.ProviderName.OpenTasks.authority,
                    0
                )
            }

        } catch (e: InvalidAccountException) {
            CountlyWrapper.recordHandledException(e)
            Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
        }
    }

    private fun insertService(
        db: AppDatabase,
        accountName: String,
        type: String,
        info: DavResourceFinder.Configuration.ServiceInfo
    ): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = db.serviceDao().insertOrReplace(service)

        // insert home sets
        val homeSetDao = db.homeSetDao()
        for (homeSet in info.homeSets) {
            homeSetDao.insertOrReplace(HomeSet(0, serviceId, true, homeSet))
        }

        // insert collections
        val collectionDao = db.collectionDao()
        for (collection in info.collections.values) {
            collection.serviceId = serviceId
            collectionDao.insertOrReplace(collection)
        }

        return serviceId
    }

    /* DavService.RefreshingStatusListener */
    override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {}

    override fun onUnauthorized(
        authority: String,
        account: Account,
        service: Service,
        accountSettings: AccountSettings,
    ) {
        val unauthorizedException: String =
            String.format(
                "Unauthorized Exception(401):\n" +
                        "Account Details: %s,\n" +
                        "Authority:%s\n" +
                        "Service: %s\n" +
                        "Enviroment: %s\n",
                account.toString(),
                authority,
                service.toString(),
                accountSettings.serviceEnvironments.toString(),
            )
        CountlyWrapper.addCrashBreadcrumb(unauthorizedException)
        onUnauthorized?.invoke(account)
    }
}