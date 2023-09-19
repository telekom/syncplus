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
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.SyncResult
import android.os.Bundle
import android.provider.ContactsContract
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import java.util.logging.Level

abstract class ContactsSyncAdapterService: SyncAdapterService() {

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun syncAdapter() = ContactsSyncAdapter(this)

    class ContactsSyncAdapter(
        private val service: SyncAdapterService
    ): SyncAdapter(service) {

        override fun sync(serviceEnvironments: ServiceEnvironments, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                syncWillRun(serviceEnvironments, account, extras, authority, provider, syncResult)
                val addressBook = LocalAddressBook(context, account, provider)
                val accountSettings = AccountSettings(context, serviceEnvironments, addressBook.mainAccount) {
                    service.onLoginException(authority, it)
                }
                val accountManager = AccountManager.get(context)

                // handle group method change
                val groupMethod = accountSettings.getGroupMethod().name
                accountManager.getUserData(account, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                    if (previousGroupMethod != groupMethod) {
                        Logger.log.info("Group method changed, deleting all local contacts/groups")

                        // delete all local contacts and groups so that they will be downloaded again
                        provider.delete(addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI), null, null)
                        provider.delete(addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI), null, null)

                        // reset sync state
                        addressBook.syncState = null
                    }
                }
                accountManager.setUserData(account, PREVIOUS_GROUP_METHOD, groupMethod)

                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                Logger.log.info("Synchronizing address book: ${addressBook.url}")
                Logger.log.info("Taking settings from: ${addressBook.mainAccount}")

                ContactsSyncManager(
                    service.application,
                    serviceEnvironments,
                    account,
                    accountSettings,
                    extras,
                    authority,
                    syncResult,
                    provider,
                    addressBook
                ) {
                    loginExceptionOccurred(authority, it)
                }.use {
                    it.performSync()
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
            }

            syncDidRun(serviceEnvironments, account, extras, authority, provider, syncResult)
            Logger.log.info("Contacts sync complete")
        }

    }

}
