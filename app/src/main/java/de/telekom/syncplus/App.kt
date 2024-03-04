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

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.multidex.MultiDexApplication
import com.usabilla.sdk.ubform.Usabilla
import com.usabilla.sdk.ubform.UsabillaReadyCallback
import de.telekom.dtagsyncpluskit.api.APIFactory
import de.telekom.dtagsyncpluskit.api.IDMEnv
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.awaitResponse
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.log.PlainTextFormatter
import de.telekom.dtagsyncpluskit.davx5.ui.NotificationUtils
import de.telekom.dtagsyncpluskit.model.idm.WellKnownInfo
import de.telekom.dtagsyncpluskit.model.spica.Contact
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.dtagsyncpluskit.utils.*
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.util.Prefs
import java.io.File
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

@Suppress("unused")
class App : MultiDexApplication() {
    companion object {
        fun serviceEnvironments(): ServiceEnvironments {
            return ServiceEnvironments.fromBuildConfig(
                BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
            )
        }

        fun getLauncherBitmap(context: Context): Bitmap? {
            val drawableLogo = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
            return if (drawableLogo is BitmapDrawable) {
                drawableLogo.bitmap
            } else {
                null
            }
        }

        fun getAccounts(ctx: Context): Array<Account> {
            val accountManager = IDMAccountManager(ctx)
            return accountManager.getAccounts()
        }

        fun enableCountly(app: Application, enabled: Boolean) {
            val deviceId = getAccounts(app).firstOrNull()?.name?.sha256()
            val config = CountlyWrapper.Config(
                isDebug = BuildConfig.DEBUG,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                deviceId = deviceId,
                isEnabled = enabled
            )
            CountlyWrapper.setCountlyEnabled(app, config)
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
            is Ok -> _wellKnownInfo = wellKnownResults.value.body()
            is Err -> Logger.log.severe("Error: WellKnownResults: ${wellKnownResults.error}")
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

    private var _usabillaInitialized = false
    val usabillaInitialized: Boolean
        get() = _usabillaInitialized

    override fun onCreate() {
        super.onCreate()
        // Initialize countly as early as possible.
        val deviceId = getAccounts(this).firstOrNull()?.name?.sha256()
        val config =
            CountlyWrapper.Config(
                isDebug = BuildConfig.DEBUG,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                deviceId = deviceId,
                isEnabled = Prefs(this).analyticalToolsEnabled,
            )
        CountlyWrapper.setCountlyEnabled(this, config)

        // Always log finest.
        Logger.initialize(this) { logDir, logFile, cancel ->
            loggerNotificationHandler(logDir, logFile, cancel)
        }

        // Add the countly logger.
        Logger.log.addHandler(
            object : Handler() {
                init {
                    formatter = PlainTextFormatter.LOGCAT
                }

                override fun publish(record: LogRecord?) {
                    val text = formatter.format(record)
                    CountlyWrapper.addCrashBreadcrumb(text)
                }

                override fun flush() {}

                override fun close() {}
            },
        )
        val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        val appid = environ[10]

        Logger.log.level = Level.FINEST
        Usabilla.debugEnabled = true
        Usabilla.initialize(
            this,
            appid,
            null,
            object : UsabillaReadyCallback {
                override fun onUsabillaInitialized() {
                    Logger.log.info("Usabilla | Initialized = true")
                    _usabillaInitialized = true
                    Usabilla.preloadFeedbackForms(listOf("61f00d93a4af1614f06adc25"))
                }
            },
        )

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

    @SuppressLint("MissingPermission")
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
                    getPendingIntentActivity(
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
                getPendingIntentActivity(
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
