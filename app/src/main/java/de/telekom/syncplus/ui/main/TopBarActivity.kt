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

package de.telekom.syncplus.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.MainActivityBinding
import de.telekom.syncplus.ui.widget.TopBar
import de.telekom.syncplus.util.viewbinding.viewBinding

@SuppressLint("Registered")
open class TopBarActivity : BaseActivity(R.layout.main_activity) {
    // TODO Refactor in favor of using the Fragment Result API
    lateinit var topBar: TopBar
        private set

    private val binding by viewBinding(R.id.root) { MainActivityBinding.bind(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        topBar = binding.topbar
    }
}
