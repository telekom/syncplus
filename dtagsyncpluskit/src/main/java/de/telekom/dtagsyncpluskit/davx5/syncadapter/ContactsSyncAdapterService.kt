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
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.closeCompat
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level

abstract class ContactsSyncAdapterService : SyncAdapterService() {
    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun syncAdapter() = ContactsSyncAdapter(this)

    class ContactsSyncAdapter(
        private val service: SyncAdapterService,
    ) : SyncAdapter(service) {
        override fun sync(
            serviceEnvironments: ServiceEnvironments,
            account: Account,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            syncResult: SyncResult,
        ) {
            try {
                syncWillRun(serviceEnvironments, account, extras, authority, provider, syncResult)

                extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)

                val accountSettings = AccountSettings(context, account)

                if (updateLocalAddressBook(account, context, syncResult, true)) {
                    val addressBooks = LocalAddressBook.findAllObsolete(context, provider, account)
                    startSync(
                        accountSettings = accountSettings,
                        provider = provider,
                        extras = extras,
                        authority = authority,
                        syncResult = syncResult,
                        addressBooks = addressBooks
                    )
                    // Delete LocalAddressBook accounts after successfull sync
                    addressBooks.forEach { it.delete() }
                }

                if (updateLocalAddressBook(account, context, syncResult, false)) {
                    startSync(
                        accountSettings = accountSettings,
                        provider = provider,
                        extras = extras,
                        authority = authority,
                        syncResult = syncResult,
                        addressBooks = LocalAddressBook.findAll(context, provider, account)
                    )
                }
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
            }
            syncDidRun(serviceEnvironments, account, extras, authority, provider, syncResult)
            Logger.log.info("Contacts sync complete")
        }

        private fun startSync(
            accountSettings: AccountSettings,
            provider: ContentProviderClient,
            extras: Bundle,
            authority: String,
            syncResult: SyncResult,
            addressBooks: List<LocalAddressBook>
        ) {
            addressBooks
                .map { it.account }
                .forEach { addressBookAccount ->
                    performSync(
                        account = addressBookAccount,
                        accountSettings = accountSettings,
                        extras = extras,
                        authority = authority,
                        provider = provider,
                        syncResult = syncResult,
                    )
                }
        }

        private fun performSync(
            account: Account,
            accountSettings: AccountSettings,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            val addressBook = LocalAddressBook(context, account, provider)
            val accountManager = AccountManager.get(context)

            // handle group method change
            val groupMethod = accountSettings.getGroupMethod().name
            accountManager.getUserData(account, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                provider.updateGroupMethod(addressBook, previousGroupMethod, groupMethod)
            }
            accountManager.setUserData(account, PREVIOUS_GROUP_METHOD, groupMethod)

            // don't run sync if
            // - sync conditions (e.g. "sync only in WiFi") are not met AND
            // - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) &&
                !checkSyncConditions(accountSettings)
            ) {
                return
            }

            Logger.log.info("Synchronizing address book: ${addressBook.url}")
            Logger.log.info("Taking settings from: $account")

            ContactsSyncManager(
                context,
                account,
                accountSettings,
                extras,
                authority,
                syncResult,
                provider,
                addressBook,
            ) {
                loginExceptionOccurred(authority, it)
            }.use {
                it.performSync()
            }
        }

        private fun updateLocalAddressBook(
            account: Account,
            context: Context,
            syncResult: SyncResult,
            useObsoleteAddressbook: Boolean
        ): Boolean {
            val db = AppDatabase.getInstance(context)
            val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)

            val remoteAddressBooks = mutableMapOf<HttpUrl, Collection>()
            if (service != null) {
                for (collection in db.collectionDao().getByServiceAndSync(service.id))
                    remoteAddressBooks[collection.url] = collection
            }

            if (context.isContactsPermissionsGranted()) {
                if (remoteAddressBooks.isEmpty()) {
                    Logger.log.info("No contacts permission, but no address book selected for synchronization")
                } else {
                    Logger.log.warning("No contacts permission, but address books are selected for synchronization")
                }
                return false
            }

            val contactsProvider =
                context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
            try {
                if (contactsProvider == null) {
                    Logger.log.severe("Couldn't access contacts provider")
                    syncResult.databaseError = true
                    return false
                }

                val addressBooks = if (useObsoleteAddressbook) {
                    LocalAddressBook.findAllObsolete(context, contactsProvider, account)
                } else {
                    LocalAddressBook.findAll(context, contactsProvider, account)
                }

                // delete/update local address books
                for (addressBook in addressBooks) {
                    val url = addressBook.url.toHttpUrlOrNull()!!
                    val info = remoteAddressBooks[url]
                    if (info == null) {
                        addressBook.delete()
                    } else {
                        // remote CollectionInfo found for this local collection, update data
                        try {
                            Logger.log.log(Level.FINE, "Updating local address book $url", info)
                            addressBook.update(info)
                        } catch (e: Exception) {
                            CountlyWrapper.recordHandledException(e)
                            Logger.log.log(Level.WARNING, "Couldn't rename address book account", e)
                        }
                        // we already have a local address book for this remote collection, don't take into consideration anymore
                        remoteAddressBooks -= url
                    }
                }

                // Use only already created LocalAddressBook for obsolete accounts
                if (useObsoleteAddressbook) return true

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

        private fun ContentProviderClient.updateGroupMethod(
            addressBook: LocalAddressBook,
            previousGroupMethod: String,
            groupMethod: String
        ) {
            if (previousGroupMethod == groupMethod) return

            Logger.log.info("Group method changed, deleting all local contacts/groups")

            // delete all local contacts and groups so that they will be downloaded again
            this.delete(
                addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI),
                null,
                null
            )
            this.delete(
                addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI),
                null,
                null
            )

            // reset sync state
            addressBook.syncState = null
        }

        private fun Context.isContactsPermissionsGranted(): Boolean {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        }
    }
}