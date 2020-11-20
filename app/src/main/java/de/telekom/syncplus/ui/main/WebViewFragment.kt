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

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import kotlinx.android.synthetic.main.fragment_webview.view.*
import java.net.URI

class WebViewFragment : BaseFragment() {
    override val TAG: String
        get() = "WEBVIEW_FRAGMENT"

    companion object {
        private const val ARG_URL = "ARG_URL"
        fun newInstance(url: Uri): WebViewFragment {
            val args = Bundle(1)
            args.putParcelable(ARG_URL, url)
            val fragment = WebViewFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val mUrl by extraNotNull<Uri>(ARG_URL)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_webview, container, false)
        v.webview.loadUrl(mUrl.toString())
        v.webview.webViewClient = CustomWebViewClient(mUrl.toString())
        v.webview.settings.javaScriptEnabled = true
        return v
    }

    @Suppress("ReplaceCallWithBinaryOperator")
    class CustomWebViewClient(private val allowedUrl: String) : WebViewClient() {
        private fun urlsMatching(lhs: String?, rhs: String?): Boolean {
            if (lhs == null || rhs == null) return false
            val uri1 = URI.create(lhs).normalize()
            val uri2 = URI.create(rhs).normalize()
            return uri1.host == uri2.host
                    && uri1.path.removeSuffix(".html") == uri2.path.removeSuffix(".html")
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            /* TODO: Return true when to avoid loading a website. */
            return false
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            /* TODO: Return true when to avoid loading a website. */
            return false
        }
    }
}
