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
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils.CHANNEL_DEBUG
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils.CHANNEL_GENERAL
import de.telekom.syncplus.AccountsActivity
import de.telekom.syncplus.App
import de.telekom.syncplus.LoginActivity
import de.telekom.syncplus.R

object DavNotificationUtils {

    private const val CHANNEL_SYNC = "sync"
    private const val CHANNEL_SYNC_ERRORS = NotificationUtils.CHANNEL_SYNC_ERRORS
    private const val CHANNEL_SYNC_WARNINGS = NotificationUtils.CHANNEL_SYNC_WARNINGS
    private const val CHANNEL_SYNC_IO_ERRORS = NotificationUtils.CHANNEL_SYNC_IO_ERRORS

    /*
    private fun buildRetryAction(
        context: Context,
        authority: String,
        account: Account
    ): NotificationCompat.Action {
        val retryIntent = Intent(context, DavService::class.java)
        retryIntent.action = DavService.ACTION_FORCE_SYNC
        // TODO: Missing 'serviceEnvironments' action.

        val syncAuthority = if (authority == ContactsContract.AUTHORITY) {
            // if this is a contacts sync, retry syncing all address books of the main account
            context.getString(de.telekom.dtagsyncpluskit.R.string.address_books_authority)
        } else {
            authority
        }

        retryIntent.data = Uri.parse("sync://").buildUpon()
            .authority(syncAuthority)
            .appendPath(account.type)
            .appendPath(account.name)
            .build()

        return NotificationCompat.Action(
            android.R.drawable.ic_menu_rotate,
            context.getString(de.telekom.dtagsyncpluskit.R.string.sync_error_retry),
            PendingIntent.getService(context, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }
    */

    /*
    fun buildSyncErrorNotification(
        context: Context,
        authority: String,
        account: Account
    ): Notification {
        val message = context.getString(R.string.sync_error_authentication_failed)
        val contentIntent = AccountsActivity.newIntent(context, false)
        val builder =
            newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
        builder
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle(builder).bigText(message))
            .setSubText(account.name)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
        builder.addAction(buildRetryAction(context, authority, account))

        return builder.build()
    }
    */

    // Requires the notification to be send with:
    //   NotificationUtils.NOTIFY_PERMISSIONS
    fun buildMissingPermissionNotification(
        context: Context
    ): Notification {
        Logger.log.finest("BUILD MISSING PERMISSION NOTIFICATION")
        val intent = AccountsActivity.newIntent(context, false)
        val builder = newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(context.getString(R.string.sync_error_permissions))
            .setContentText(context.getString(R.string.sync_error_permissions_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        val notif = builder.build()
        notif.flags = notif.flags or Notification.FLAG_ONLY_ALERT_ONCE
        return notif
    }

    // Requires the notification to be send with:
    //   NotificationUtils.NOTIFY_SYNC_ERROR
    fun buildReloginNotification(context: Context, account: Account): Notification {
        Logger.log.finest("BUILD RELOGIN NOTIFICATION")
        val message = context.getString(R.string.sync_error_authentication_failed)
        val contentIntent = LoginActivity.newIntent(context, true, account)
        val builder =
            newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
        builder
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle(builder).bigText(message))
            .setSubText(account.name)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        val notif = builder.build()
        notif.flags = notif.flags or Notification.FLAG_ONLY_ALERT_ONCE
        return notif
    }

    fun showReloginNotification(context: Context, authority: String, account: Account) {
        Logger.log.finest("SHOW RELOGIN NOTIFICATION")
        val notificationManager = NotificationManagerCompat.from(context)

        notificationManager.notify(
            NotificationUtils.notificationTag(authority, account),
            NotificationUtils.NOTIFY_SYNC_ERROR,
            buildReloginNotification(context, account)
        )
    }

    fun reloginCallback(context: Context, authority: String): (account: Account) -> Unit {
        return { showReloginNotification(context, authority, it) }
    }

    fun energySavingNotification(context: Context): Notification {
        Logger.log.finest("BUILD ENERGY SAVING NOTIFICATION")
        val message = context.getString(de.telekom.dtagsyncpluskit.R.string.energy_saving_notification_title)
        val contentIntent = AccountsActivity.newIntent(context, false)
        val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)

        builder
            .setSmallIcon(de.telekom.dtagsyncpluskit.R.drawable.ic_sync_problem_notify)
            .setContentTitle(context.getString(de.telekom.dtagsyncpluskit.R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle(builder).bigText(message))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        val notif = builder.build()
        notif.flags = notif.flags or Notification.FLAG_ONLY_ALERT_ONCE
        return notif
    }

    fun createChannels(context: Context) {
        @TargetApi(Build.VERSION_CODES.O)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannelGroup(
                NotificationChannelGroup(
                    CHANNEL_SYNC,
                    context.getString(R.string.notification_channel_sync)
                )
            )

            nm.createNotificationChannels(listOf(
                NotificationChannel(
                    CHANNEL_DEBUG,
                    context.getString(R.string.notification_channel_debugging),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_DEFAULT
                ),

                NotificationChannel(
                    CHANNEL_SYNC_ERRORS,
                    context.getString(R.string.notification_channel_sync_errors),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_sync_errors_desc)
                    group = CHANNEL_SYNC
                },
                NotificationChannel(
                    CHANNEL_SYNC_WARNINGS,
                    context.getString(R.string.notification_channel_sync_warnings),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        context.getString(R.string.notification_channel_sync_warnings_desc)
                    group = CHANNEL_SYNC
                },
                NotificationChannel(
                    CHANNEL_SYNC_IO_ERRORS,
                    context.getString(R.string.notification_channel_sync_io_errors),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description =
                        context.getString(R.string.notification_channel_sync_io_errors_desc)
                    group = CHANNEL_SYNC
                }
            ))
        }
    }

    private fun newBuilder(context: Context, channel: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
            .setColor(context.resources.getColor(R.color.colorPrimary))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            builder.setLargeIcon(App.getLauncherBitmap(context))

        return builder
    }
}
