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

package de.telekom.syncplus.ui.main.help

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import de.telekom.dtagsyncpluskit.extra
import de.telekom.dtagsyncpluskit.extraNotNull
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentWebviewBinding
import de.telekom.syncplus.extensions.isPDFUrl
import de.telekom.syncplus.ui.dialog.DataPrivacyDialogActivity
import de.telekom.syncplus.util.viewbinding.viewBinding
import java.net.URI

class WebViewFragment : BaseFragment(R.layout.fragment_webview) {
    override val TAG: String
        get() = "WEBVIEW_FRAGMENT"

    private var webView: WebView? = null

    companion object {
        private const val ARG_URL = "ARG_URL"
        private const val ARG_FALLBACK_URL = "ARG_FALLBACK_URL"

        fun newInstance(
            url: Uri,
            fallback: Uri? = null
        ): WebViewFragment {
            val fragment = WebViewFragment()
            fragment.arguments = bundleOf(
                ARG_URL to url,
                ARG_FALLBACK_URL to fallback
            )
            return fragment
        }
    }

    private val binding by viewBinding(FragmentWebviewBinding::bind)
    private val mUrl by extraNotNull<Uri>(ARG_URL)
    private val fallbackUrl by extra<Uri>(ARG_FALLBACK_URL)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.webview.loadUrl(mUrl.toString())
        binding.webview.webViewClient = CustomWebViewClient(mUrl.toString())
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            startActivity(intent)
        }

        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                // Log.d("SyncPlus-->", "shouldOverrideUrlLoading: ${request?.url}")
                if (request?.url.toString() == "syncplus://settings") {
                    startActivity(
                        DataPrivacyDialogActivity.newIntent(requireActivity())
                            .putExtra("comeFromDeepLink", true),
                    )
                    return true
                }
                if (request?.url.toString() == "mailto:datenschutz@telekom.de") {
                    sendEmail(
                        "datenschutz@telekom.de",
                        "Hello",
                        "New message",
                    )
                    return true
                }

                if (request?.url.toString().startsWith("mailto:")) {
                    sendEmail(
                        request?.url.toString().split("mailto:")[1],
                        "",
                        "",
                    )
                    return true
                }

                if (request?.url?.isPDFUrl() == true) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, request.url!!)
                    startActivity(browserIntent)
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                Log.e("SyncPlus", "onReceivedError: $request -> $error")

                if (request?.url == mUrl && fallbackUrl != null) {
                    view?.loadUrl(fallbackUrl.toString())
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("SyncPlus", "onReceivedHttpError: $request -> $errorResponse")
            }

            override fun onLoadResource(
                view: WebView?,
                url: String?,
            ) {
                super.onLoadResource(view, url)
                Log.d("SyncPlus", "onLoadResource: $url")
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?,
            ) {
                super.onPageStarted(view, url, favicon)
                Log.d("SyncPlus", "onPageStarted: $url")
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                Log.d("SyncPlus", "onPageFinished: $url")
            }
        }
        webView = binding.webview
    }

    class CustomWebViewClient(
        private val allowedUrl: String,
    ) : WebViewClient() {
        private fun urlsMatching(
            lhs: String?,
            rhs: String?,
        ): Boolean {
            if (lhs == null || rhs == null) return false
            val uri1 = URI.create(lhs).normalize()
            val uri2 = URI.create(rhs).normalize()
            return uri1.host == uri2.host &&
                    uri1.path.removeSuffix(".html") == uri2.path.removeSuffix(".html")
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            // TODO: Return true when to avoid loading a website.
            return false
        }
    }

    private fun sendEmail(
        recipient: String,
        subject: String,
        message: String,
    ) {
        val emailIntent =
            Intent(Intent.ACTION_SEND).apply {
                setDataAndNormalize(Uri.parse("mailto:"))
                setTypeAndNormalize("text/plain")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
            }

        try {
            startActivity(
                Intent.createChooser(
                    emailIntent,
                    "Choose Email Client...",
                ),
            )
        } catch (e: Exception) {
            // Show error
        }
    }

    fun goBackInWebView(): Boolean {
        if (webView == null) return false
        if (webView?.canGoBack() == false) return false
        val baseHost: String? = mUrl.host
        val fallbackUrlHost: String? = fallbackUrl?.host
        val currentUrlHost = Uri.parse(webView?.url).host
        if (baseHost == currentUrlHost || fallbackUrlHost == currentUrlHost) return false
        webView?.goBack()
        return true
    }
}
