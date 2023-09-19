package de.telekom.dtagsyncpluskit.utils

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

object CountlyWrapper {
    @Volatile
    private var isCountlyEnabled = false

    @Volatile
    private var isCountlyInitialized = false

   data  class Config(
        val isDebug: Boolean,
        val versionName: String,
        val versionCode: Long,
        val deviceId: String?,
        val isEnabled: Boolean
    )

    private fun init(application: Application, config: Config) {
        Logger.log.info("CountlyInitialize | Enabled = $isCountlyEnabled | Initialized = $isCountlyInitialized")

        if (isCountlyInitialized)
            return

        val environ = BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!
        val serverUrl = environ[9]
        val appKey = environ[8]

        Logger.log.info("Countly | Init | DeviceId = ${config.deviceId}")
        val releaseOrCodename =
            if (Build.VERSION.RELEASE != null) Build.VERSION.RELEASE else Build.VERSION.CODENAME

        CountlyConfig(application, appKey.toString(), serverUrl).apply {
            // Don't use explicit ID mode.
            // config.setIdMode(DeviceId.Type.OPEN_UDID)
            config.deviceId?.let {
                setDeviceId(config.deviceId)
            }
            enableCrashReporting()
            setRecordAllThreadsWithCrash()
            setCustomCrashSegment(
                mapOf(
                    "isDebug" to config.isDebug,
                    "appVersion" to "${config.versionName} (${config.versionCode})",
                    "androidVersion" to "$releaseOrCodename (${Build.VERSION.SDK_INT})"
                )
            )
        }.let {
            Countly.sharedInstance().init(it)
            isCountlyInitialized = true
            Logger.log.info("Countly Initialized !!!")
        }
    }

    fun setCountlyEnabled(application: Application, config: Config) {
        Logger.log.info("setCountlyEnabled = ${config.isEnabled}")

        if (config.isEnabled) {
            init(application, config)
        }

        isCountlyEnabled = config.isEnabled
    }

    fun changeDeviceIdWithMerge(deviceId: String) {
        Logger.log.info("changeDeviceIdWithMerge")
        if (!isCountlyEnabled) return
        Countly.sharedInstance().changeDeviceIdWithMerge(deviceId)
    }

    fun onStart(activity: Activity) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().onStart(activity)
    }

    fun onStop() {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().onStop()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().onConfigurationChanged(newConfig)
    }

    fun recordHandledException(e: java.lang.Exception?) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().crashes().recordHandledException(e)
    }

    fun recordHandledException(e: Throwable?) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().crashes().recordHandledException(e)
    }

    fun recordUnhandledException(e: java.lang.Exception?) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().crashes().recordUnhandledException(e)
    }

    fun recordUnhandledException(e: Throwable?) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().crashes().recordUnhandledException(e)
    }

    fun addCrashBreadcrumb(text: String) {
        if (!isCountlyEnabled) return
        Countly.sharedInstance().crashes().addCrashBreadcrumb(text)
    }
}