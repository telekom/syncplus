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

package de.telekom.dtagsyncpluskit.davx5.ui

import android.accounts.Account
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.davx5.log.Logger

object NotificationUtils {
    // notification IDs
    const val NOTIFY_EXTERNAL_FILE_LOGGING = 1
    const val NOTIFY_REFRESH_COLLECTIONS = 2
    const val NOTIFY_SYNC_ERROR = 10
    const val NOTIFY_INVALID_RESOURCE = 11
    const val NOTIFY_OPENTASKS = 20
    const val NOTIFY_PERMISSIONS = 21
    const val NOTIFY_LICENSE = 100

    // notification channels
    const val CHANNEL_GENERAL = "general"
    const val CHANNEL_DEBUG = "debug"

    private const val CHANNEL_SYNC = "sync"
    const val CHANNEL_SYNC_ERRORS = "syncProblems"
    const val CHANNEL_SYNC_WARNINGS = "syncWarnings"
    const val CHANNEL_SYNC_IO_ERRORS = "syncIoErrors"

    fun newBuilder(context: Context, channel: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
            .setColor(context.resources.getColor(R.color.colorPrimary))

        //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
        //    builder.setLargeIcon(App.getLauncherBitmap(context))

        return builder
    }

    // AGE: Remove 'authority', to prevent several similar notifications.
    fun notificationTag(authority: String, account: Account) =
        account.name.hashCode().toString()
        //"$authority-${account.name}".hashCode().toString()
}
