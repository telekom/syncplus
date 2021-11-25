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

package de.telekom.syncplus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.multidex.MultiDexApplication
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.IDMEnv
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.awaitResponse
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.dtagsyncpluskit.model.idm.WellKnownInfo
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Level

@Suppress("unused")
class App : MultiDexApplication() {

    companion object {
        fun serviceEnvironments(context: Context) = ServiceEnvironments.fromBuildConfig(
            Uri.parse(context.getString(R.string.REDIRECT_URI)),
            BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        )

        fun getLauncherBitmap(context: Context): Bitmap? {
            val drawableLogo = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
            return if (drawableLogo is BitmapDrawable)
                drawableLogo.bitmap
            else
                null
        }
    }

    private var _wellKnownInfo: WellKnownInfo? = null
    suspend fun getWellKnownInfo(): WellKnownInfo {
        if (_wellKnownInfo != null) {
            return _wellKnownInfo!!
        }

        val env = IDMEnv.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!)
        val idm = APIFactory.idmAPI(env)
        when (val wellKnownResults = idm.wellKnown().awaitResponse()) {
            is Ok -> _wellKnownInfo = wellKnownResults.value.body();
            is Err -> Logger.log.severe("Error: WellKnownResults: ${wellKnownResults.error}");
        }
        return _wellKnownInfo!!
    }

    private var _originalsLock = Object()
    private var _originals: List<Contact>? = null
    var originals: List<Contact>?
        get() = _originals
        set(value) {
            synchronized(_originalsLock) {
                _originals = value
            }
        }

    private var _duplicatesLock = Object()
    private var _duplicates: List<Duplicate>? = null
    var duplicates: List<Duplicate>?
        get() = _duplicates
        set(value) {
            synchronized(_duplicatesLock) {
                _duplicates = value
            }
        }

    private var _inSetupLock = Object()
    private var _inSetup: Boolean = false
    var inSetup: Boolean
        get() = _inSetup
        set(value) {
            synchronized(_inSetupLock) {
                _inSetup = value
            }
        }


    override fun onCreate() {
        super.onCreate()
        // Always log finest.
        Logger.initialize(this) { logDir, logFile, cancel ->
            loggerNotificationHandler(logDir, logFile, cancel)
        }
        Logger.log.level = Level.FINEST
        GlobalScope.launch { /* Spin this up to warm up the ovens. */ }

        if (Build.VERSION.SDK_INT <= 21)
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        DavNotificationUtils.createChannels(this)

        /*val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(
            "test",
            NotificationUtils.NOTIFY_SYNC_ERROR,
            DavNotificationUtils.energySavingNotification(this)
        )*/

        /*
        // !!! DEBUG !!!
        val serviceEnvironments = serviceEnvironments(this)
        val onUnauthorized = DavNotificationUtils.reloginCallback(this, "authority")
        val accountManager = IDMAccountManager(this, onUnauthorized)
        accountManager.getAccounts().forEach {
            val accountSettings = AccountSettings(this, serviceEnvironments, it, onUnauthorized)
            Log.d("SyncPlus", "Reset Credentials for ${it.name}")
            val credentials = accountSettings.getCredentials()
            credentials.accessToken = "bla"
            credentials.setRefreshToken("blub")
            //accountSettings.resyncCalendars(true)
            //accountSettings.resyncContacts(true)
        }
        // !!! DEBUG !!!
        */

        /*
        thread {
            val env = SpicaEnv.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!)
            val accountManager = IDMAccountManager(this, env)
            val accounts = accountManager.getAccounts()
            accounts.getOrNull(0)?.let { account ->
                val password = accountManager.getPassword(account)
                Log.d("SyncPlus", "Token: $password")
                val authToken = accountManager.getAuthToken(account)
                Log.d("DavResourceFinder", "authToken: $authToken")

                Log.d("DavResourceFinder", "Account Name: ${account.name}")
                val credentials = Credentials(account.name, authToken)
                val finder = DavResourceFinder(this, env.baseUrl, credentials)
                val config = finder.findInitialConfiguration()
                Log.d("SyncPlus", "Config: $config")
            }
        }*/
    }

    private fun loggerNotificationHandler(logDir_: File?, logFile_: File?, cancel: Boolean) {
        val nm = NotificationManagerCompat.from(this)
        if (cancel) {
            nm.cancel(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING)
        } else {
            val logDir = logDir_ ?: return
            val logFile = logFile_ ?: return
            val builder = NotificationUtils.newBuilder(this, NotificationUtils.CHANNEL_DEBUG)
            builder
                .setSmallIcon(R.drawable.ic_sd_card_notify)
                .setContentTitle(getString(R.string.logging_notification_title))

            val prefIntent = Intent(this, WelcomeActivity::class.java)
            prefIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            builder.setContentText(logDir.path)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentText(getString(R.string.logging_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        prefIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .setOngoing(true)


            // add "Share" action
            val logFileUri = FileProvider.getUriForFile(
                this,
                getString(R.string.authority_debug_provider),
                logFile
            )
            Logger.log.fine("Now logging to file: $logFile -> $logFileUri")

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sync-Plus logs")
            shareIntent.putExtra(Intent.EXTRA_STREAM, logFileUri)
            shareIntent.type = "text/plain"
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooserIntent = Intent.createChooser(shareIntent, null)
            val shareAction = NotificationCompat.Action.Builder(
                R.drawable.ic_share_notify,
                getString(R.string.logging_notification_send_log),
                PendingIntent.getActivity(
                    this,
                    0,
                    chooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            builder.addAction(shareAction.build())

            nm.notify(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING, builder.build())
        }
    }
}
