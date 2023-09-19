package de.telekom.syncplus.extensions

import android.view.LayoutInflater
import android.view.View

val View.inflater
    get() = LayoutInflater.from(context)