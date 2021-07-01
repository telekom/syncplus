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

import android.Manifest
import android.accounts.Account
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.closeCompat
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level

abstract class AddressBooksSyncAdapterService : SyncAdapterService() {

    override fun syncAdapter() = AddressBooksSyncAdapter(this)


    class AddressBooksSyncAdapter(
        private val service: SyncAdapterService
    ) : SyncAdapter(service) {

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

                if (updateLocalAddressBooks(account, syncResult))
                    for (addressBookAccount in LocalAddressBook.findAll(context, null, account).map { it.account }) {
                        Logger.log.log(Level.INFO, "Running sync for address book", addressBookAccount)
                        val syncExtras = Bundle(extras)
                        syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                        syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                        ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, syncExtras)
                    }
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync address books", e)
            }

            Logger.log.info("Address book sync complete")
        }

        private fun updateLocalAddressBooks(account: Account, syncResult: SyncResult): Boolean {
            val db = AppDatabase.getInstance(context)
            val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)

            val remoteAddressBooks = mutableMapOf<HttpUrl, Collection>()
            if (service != null)
                for (collection in db.collectionDao().getByServiceAndSync(service.id))
                    remoteAddressBooks[collection.url] = collection

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                if (remoteAddressBooks.isEmpty())
                    Logger.log.info("No contacts permission, but no address book selected for synchronization")
                else
                    Logger.log.warning("No contacts permission, but address books are selected for synchronization")
                return false
            }

            val contactsProvider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
            try {
                if (contactsProvider == null) {
                    Logger.log.severe("Couldn't access contacts provider")
                    syncResult.databaseError = true
                    return false
                }

                // delete/update local address books
                for (addressBook in LocalAddressBook.findAll(context, contactsProvider, account)) {
                    val url = addressBook.url.toHttpUrlOrNull()!!
                    val info = remoteAddressBooks[url]
                    if (info == null) {
                        Logger.log.log(Level.INFO, "Deleting obsolete local address book", url)
                        addressBook.delete()
                    } else {
                        // remote CollectionInfo found for this local collection, update data
                        try {
                            Logger.log.log(Level.FINE, "Updating local address book $url", info)
                            addressBook.update(info)
                        } catch (e: Exception) {
                            Logger.log.log(Level.WARNING, "Couldn't rename address book account", e)
                        }
                        // we already have a local address book for this remote collection, don't take into consideration anymore
                        remoteAddressBooks -= url
                    }
                }

                // create new local address books
                for ((_, info) in remoteAddressBooks) {
                    Logger.log.log(Level.INFO, "Adding local address book", info)
                    LocalAddressBook.create(context, contactsProvider, account, info)
                }
            } finally {
                contactsProvider?.closeCompat()
            }

            return true
        }

    }

}
