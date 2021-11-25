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
import androidx.core.app.NotificationManagerCompat
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.syncadapter.ContactsSyncAdapterService
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.syncplus.App
import de.telekom.syncplus.util.Prefs

class ContactsSyncAdapterService : ContactsSyncAdapterService() {
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val prefs by lazy { Prefs(this.applicationContext) }

    override fun onSecurityException(account: Account, syncResult: SyncResult) {
        if ((this.applicationContext as? App)?.inSetup == true) return
        notificationManager.notify(
            NotificationUtils.notificationTag(syncResult.toDebugString(), account),
            NotificationUtils.NOTIFY_PERMISSIONS,
            DavNotificationUtils.buildMissingPermissionNotification(this)
        )
    }

    override fun onLoginException(authority: String, account: Account) {
        notificationManager.notify(
            NotificationUtils.notificationTag(authority, account),
            NotificationUtils.NOTIFY_SYNC_ERROR,
            DavNotificationUtils.buildReloginNotification(this, account)
        )
    }

    override fun onSyncWillRun(
        serviceEnvironments: ServiceEnvironments,
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
    }

    override fun onSyncDidRun(
        serviceEnvironments: ServiceEnvironments,
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        val accountSettings = AccountSettings(this, serviceEnvironments, account) {
            onLoginException(authority, it)
        }
        val lastSyncs = prefs.lastSyncs
        val currentSync = System.currentTimeMillis()
        val lastSync = lastSyncs.lastSyncs.put("Contacts.$authority", currentSync)
        val i = accountSettings.getSyncInterval(authority)
        val interval = if (i == null) Long.MAX_VALUE else i * 1000
        if (lastSync != null && (currentSync - lastSync) > (interval * 2)) {
            notificationManager.notify(
                NotificationUtils.notificationTag(authority, account),
                NotificationUtils.NOTIFY_SYNC_ERROR,
                DavNotificationUtils.energySavingNotification(this)
            )
        }
        prefs.lastSyncs = lastSyncs
    }
}
