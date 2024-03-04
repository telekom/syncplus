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

package de.telekom.syncplus.dav

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import android.os.Bundle
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.syncadapter.AddressBooksSyncAdapterService
import de.telekom.syncplus.util.Prefs

@Deprecated("AddressBooks sync is performed by ContactsSyncAdapterService now only")
class AddressBooksSyncAdapterService : AddressBooksSyncAdapterService() {
    private val prefs by lazy { Prefs(this.applicationContext) }
    private val notificationDelegate by lazy { SyncAdapterNotificationDelegateImpl(this) }

    override fun onSecurityException(
        account: Account,
        syncResult: SyncResult,
    ) {
        notificationDelegate.processSequrityExceprion(account, syncResult)
    }

    override fun onLoginException(
        authority: String,
        account: Account,
    ) {
        notificationDelegate.processLoginException(authority, account)
    }

    override fun onSyncWillRun(
        serviceEnvironments: ServiceEnvironments,
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        // no-op
    }

    override fun onSyncDidRun(
        serviceEnvironments: ServiceEnvironments,
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        val accountSettings = AccountSettings(this, account)
        val lastSyncs = prefs.lastSyncs
        val currentSync = System.currentTimeMillis()
        val lastSync = lastSyncs.lastSyncs.put("AddressBooks.$authority", currentSync)
        val i = accountSettings.tryGetSyncInterval()
        val interval = if (i == null) Long.MAX_VALUE else i * 1000
        if (lastSync != null && (currentSync - lastSync) > (interval * 2)) {
            notificationDelegate.processSyncFinished(authority, account)
        }
        prefs.lastSyncs = lastSyncs
    }
}
