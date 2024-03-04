package de.telekom.dtagsyncpluskit.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

fun getPendingIntentActivity(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            (PendingIntent.FLAG_IMMUTABLE or flags),
        )
    } else {
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            flags,
        )
    }
}

fun getPendingIntentService(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.getService(
            context,
            requestCode,
            intent,
            (PendingIntent.FLAG_IMMUTABLE or flags),
        )
    } else {
        PendingIntent.getService(
            context,
            requestCode,
            intent,
            flags,
        )
    }
}
