package de.telekom.syncplus.dav

import android.Manifest
import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Context
import android.content.SyncResult
import androidx.core.app.NotificationManagerCompat
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.syncplus.App
import de.telekom.syncplus.extensions.isPermissionGranted

interface SyncAdapterNotificationDelegate {
    fun processSequrityExceprion(
        account: Account,
        syncResult: SyncResult,
    )

    fun processLoginException(
        authority: String,
        account: Account,
    )

    fun processSyncFinished(
        authority: String,
        account: Account,
    )
}

@SuppressLint("MissingPermission")
class SyncAdapterNotificationDelegateImpl(
    private val context: Context,
) : SyncAdapterNotificationDelegate {
    override fun processSequrityExceprion(
        account: Account,
        syncResult: SyncResult,
    ) {
        if ((context.applicationContext as? App)?.inSetup == true) {
            Logger.log.info("Skipping sequrity exception for [${account.name}] - app setup is incomplete")
            return
        }

        // There's no need to display a permission notification if some accounts are not completely set up,
        // as it may interrupt the setup flow.
        if (!IDMAccountManager(context).isSetupCompleted(account)) {
            Logger.log.info("Skipping sequrity exception for [${account.name}] - some of accounts are in setup")
            return
        }

        context.withNotificationPermissions {
            notify(
                NotificationUtils.notificationTag(syncResult.toDebugString(), account),
                NotificationUtils.NOTIFY_PERMISSIONS,
                DavNotificationUtils.buildMissingPermissionNotification(context),
            )
        }
    }

    override fun processLoginException(
        authority: String,
        account: Account,
    ) {
        context.withNotificationPermissions {
            notify(
                NotificationUtils.notificationTag(authority, account),
                NotificationUtils.NOTIFY_SYNC_ERROR,
                DavNotificationUtils.buildReloginNotification(context, account),
            )
        }
    }

    override fun processSyncFinished(
        authority: String,
        account: Account,
    ) {
        context.withNotificationPermissions {
            notify(
                NotificationUtils.notificationTag(authority, account),
                NotificationUtils.NOTIFY_SYNC_ERROR,
                DavNotificationUtils.energySavingNotification(context),
            )
        }
    }

    private inline fun Context.withNotificationPermissions(block: NotificationManagerCompat.() -> Unit) {
        if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            NotificationManagerCompat.from(this).block()
        }
    }
}
