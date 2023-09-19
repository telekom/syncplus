package de.telekom.syncplus.extensions

import android.net.Uri

fun Uri.isPDFUrl(): Boolean {
    val url = toString()
    return url.endsWith(".pdf?") || url.endsWith(".pdf")
}

