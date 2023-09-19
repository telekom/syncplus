package de.telekom.syncplus.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.ui.BaseActivity
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import de.telekom.syncplus.ui.main.DataPrivacyDialogActivity.Companion.openSettings
import de.telekom.syncplus.util.Prefs
import de.telekom.syncplus.util.handleUrlClicks
import kotlinx.android.synthetic.main.dialog_data_privacy.view.*
import kotlinx.android.synthetic.main.dialog_data_privacy_info.view.*
import kotlinx.android.synthetic.main.dialog_data_privacy_settings.view.*
import kotlinx.android.synthetic.main.dialog_data_privacy_settings_footer.view.*
import kotlinx.android.synthetic.main.layout_small_topbar_privacy.view.*


class DataPrivacyDialogActivity : BaseActivity() {
    companion object {
        fun newIntent(activity: Activity): Intent {
            return Intent(activity, DataPrivacyDialogActivity::class.java)
        }

        var openSettings: Boolean = false
    }

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_activity)
        val bundle: Bundle? = intent.extras
        openSettings = bundle?.getBoolean("comeFromDeepLink") ?: false
        if (savedInstanceState == null) {
            val fragment = DataPrivacyFragment.newInstance {
                Logger.log.info("Consent Result: $it")
                val prefs = Prefs(this)
                prefs.consentDialogShown = true
                prefs.analyticalToolsEnabled = it.analyticalToolsEnabled
                App.enableCountly(application, it.analyticalToolsEnabled)
                finish()
            }

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.dialog_container, fragment, fragment.TAG)
                .commitNow()
        }
    }

    override fun onBackPressed() {}
}

class DataPrivacyFragment(
    private val onDismiss: (result: Result) -> Unit
) : BaseFragment() {
    data class Result(val analyticalToolsEnabled: Boolean)

    companion object {
        fun newInstance(onDismiss: (result: Result) -> Unit) = DataPrivacyFragment(onDismiss)
    }

    override val TAG: String
        get() = "DataPrivacyFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("SyncPlus", "DataPrivacyFragment: onCreateView")
        val v = inflater.inflate(R.layout.dialog_data_privacy, container, false)

        v.disclaimer_text.handleUrlClicks {
            Log.d("SyncPlus", "onClick: URL: $it")
            if (it == "#dataprivacy") {
                DataPrivacyInfoDialog.show(requireActivity()) { openSettings ->
                    if (openSettings) {
                        val fragment = DataPrivacySettingsFragment.newInstance { result ->
                            onDismiss(result)
                        }

                        push(R.id.dialog_container, fragment, false)
                    }
                }
            } else if (it == "#next") {
                onDismiss(Result(analyticalToolsEnabled = false))
            }
        }

        v.required_button_accept.setOnClickListener {
            onDismiss(Result(analyticalToolsEnabled = false))
        }

        v.disclaimer_button_accept.setOnClickListener {
            onDismiss(Result(analyticalToolsEnabled = true))
        }


        v.disclaimer_button_more.setOnClickListener {
            val fragment = DataPrivacySettingsFragment.newInstance {
                onDismiss(it)
            }

            push(R.id.dialog_container, fragment, false)
        }

        v.data_privacy_button.setOnClickListener {
            DataPrivacyInfoDialog.show(requireActivity()) { openSettings ->
                if (openSettings) {
                    val fragment = DataPrivacySettingsFragment.newInstance { result ->
                        onDismiss(result)
                    }

                    push(R.id.dialog_container, fragment, false)
                }
            }
        }
        if (openSettings) {
            val fragment = DataPrivacySettingsFragment.newInstance {
                onDismiss(it)
            }

            push(R.id.dialog_container, fragment, false)
        }

        return v
    }
}

class DataPrivacyInfoDialog(
    private val parent: FragmentActivity,
    private val onDismiss: (openSettings: Boolean) -> Unit
) : DialogFragment() {

    companion object {
        fun show(activity: FragmentActivity, onDismiss: (openSettings: Boolean) -> Unit) {
            val dialog = DataPrivacyInfoDialog(activity, onDismiss)
            dialog.show(activity.supportFragmentManager, "DATA_PRIVACY_DIALOG")
            dialog.parent.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    @SuppressLint("InflateParams", "SetJavaScriptEnabled")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_data_privacy_info, null)
        val dialog =
            object : Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
                // val dialog = object : Dialog(requireContext(), R.style.fullScreenDialog) {
                override fun onBackPressed() {}
            }

        view.backButtonSmall.setOnClickListener {
            dialog.dismiss()
            onDismiss(false)
        }
        view.closeButtonSmall.setOnClickListener {
            dialog.dismiss()
            onDismiss(false)
        }
        view.disclaimer_button_dismiss.setOnClickListener {
            dialog.dismiss()
            onDismiss(false)
        }

        val url = Uri.parse(getString(R.string.data_privacy_url))
        val settings = view.webview.settings
        settings.javaScriptEnabled = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = 100

        view.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                Log.d("SyncPlus", "shouldOverrideUrlLoading: ${request?.url}")
                if (request?.url.toString() == "syncplus://settings") {
                    dialog.dismiss()
                    onDismiss(true)
                    return true
                }
                if (request?.url.toString() == "syncplus://settings") {
                    startActivity(
                        DataPrivacyDialogActivity.newIntent(requireActivity())
                            .putExtra("comeFromDeepLink", true)
                    )
                    return true
                }
                if (request?.url.toString() == "mailto:datenschutz@telekom.de") {

                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        setDataAndNormalize(Uri.parse("mailto:"))
                        setTypeAndNormalize("text/plain")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("datenschutz@telekom.de"))
                        putExtra(Intent.EXTRA_SUBJECT, "Hello")
                        putExtra(Intent.EXTRA_TEXT, "New message")
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                emailIntent,
                                "Choose Email Client..."
                            )
                        )
                    } catch (e: Exception) {
                        // Show error
                    }
                    return true
                }

                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("SyncPlus", "onReceivedError: $request -> $error")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("SyncPlus", "onReceivedHttpError: $request -> $errorResponse")
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                Log.d("SyncPlus", "onLoadResource: $url")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("SyncPlus", "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("SyncPlus", "onPageFinished: $url")
            }
        }
        Log.d("SyncPlus", "loadUrl: $url")
        view.webview.loadUrl(url.toString())

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setContentView(view)
        return dialog
    }
}

class DataPrivacySettingsFragment(
    private val onDismiss: (DataPrivacyFragment.Result) -> Unit
) : BaseFragment() {
    companion object {
        fun newInstance(onDismiss: (DataPrivacyFragment.Result) -> Unit) =
            DataPrivacySettingsFragment(onDismiss)
    }

    override val TAG: String
        get() = "DataPrivacySettingsFragment"

    @SuppressLint("InflateParams")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.dialog_data_privacy_settings, container, false)

        val ctx = requireContext()
        val prefs = Prefs(context)
        val dataSource: List<SettingsItem> = listOf(
            SettingsItem(
                R.drawable.ic_resource_default,
                ctx.getString(R.string.required_cookies_title),
                ctx.getString(R.string.required_cookies_description),
                ctx.getString(R.string.required_cookies_description_long),
                true
            ),
            SettingsItem(
                R.drawable.ic_analytical_tools,
                ctx.getString(R.string.analytical_cookies_title),
                ctx.getString(R.string.analytical_cookies_description),
                ctx.getString(R.string.analytical_cookies_description_long),
                false,
                isChecked = prefs.analyticalToolsEnabled
            ),
        )
        val headerView =
            layoutInflater.inflate(R.layout.dialog_data_privacy_settings_header, null, false)
        val footerView =
            layoutInflater.inflate(R.layout.dialog_data_privacy_settings_footer, null, false)
        v.list.addHeaderView(headerView)
        v.list.addFooterView(footerView)
        v.list.adapter = ListAdapter(ctx, dataSource) { checked, position ->
            Log.i("ListAdapter", "$position:$checked")
            dataSource[position].isChecked = checked
        }

        footerView.button_accept.setOnClickListener {
            finish()
            onDismiss(DataPrivacyFragment.Result(analyticalToolsEnabled = true))
        }

        footerView.button_more.setOnClickListener {
            finish()
            onDismiss(DataPrivacyFragment.Result(analyticalToolsEnabled = dataSource[1].isChecked))
        }

        footerView.button_data_privacy.setOnClickListener {
            DataPrivacyInfoDialog.show(requireActivity()) {}
        }


        return v
    }

    data class SettingsItem(
        val iconResource: Int,
        val title: String,
        val description: String,
        val descriptionLong: String,
        val required: Boolean,
        var isChecked: Boolean = true,
        var moreOpen: Boolean = false,
    )

    class ListAdapter(
        context: Context,
        dataSource: List<SettingsItem>,
        private val onCheckedChange: (checked: Boolean, position: Int) -> Unit,
    ) : BaseListAdapter<SettingsItem>(context, dataSource) {

        @SuppressLint("UseSwitchCompatOrMaterialCode")
        private class ViewHolder(view: View?) {
            val iconView = view?.findViewById<ImageView>(R.id.icon_view)
            val titleTextView = view?.findViewById<TextView>(R.id.title)
            val descriptionTextView = view?.findViewById<TextView>(R.id.description)
            val descriptionMoreTextView = view?.findViewById<TextView>(R.id.description_more)
            val moreButton = view?.findViewById<Button>(R.id.more_button)
            val toggle = view?.findViewById<Switch>(R.id.toggle)
            val toggleTextView = view?.findViewById<TextView>(R.id.toggle_text)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder?
            val rowView: View?

            if (convertView == null) {
                rowView =
                    inflater.inflate(R.layout.dialog_data_privacy_settings_item, parent, false)
                viewHolder = ViewHolder(rowView)
                rowView.tag = viewHolder
            } else {
                rowView = convertView
                viewHolder = rowView.tag as ViewHolder
            }

            val item = getItem(position)
            viewHolder.iconView?.setImageResource(item.iconResource)
            viewHolder.titleTextView?.text = item.title
            viewHolder.descriptionTextView?.text = item.description
            viewHolder.descriptionMoreTextView?.text = item.descriptionLong
            viewHolder.toggle?.setOnCheckedChangeListener(null)
            if (item.required) {
                viewHolder.toggle?.isChecked = true
                viewHolder.toggle?.isClickable = false
                viewHolder.toggle?.isEnabled = false
                viewHolder.toggleTextView?.text = context.getString(R.string.required)
                viewHolder.toggleTextView?.setTextColor(Color.parseColor("#aeaeae"))
                viewHolder.toggle?.setTextColor(Color.parseColor("#aeaeae"))
            } else {
                viewHolder.toggle?.isChecked = item.isChecked
                viewHolder.toggleTextView?.text = context.getString(R.string.optional)
            }
            if (!item.required) {
                viewHolder.toggle?.setOnCheckedChangeListener { _, checked ->
                    onCheckedChange(checked, position)
                }
            }
            viewHolder.moreButton?.text =
                if (item.moreOpen) context.getString(R.string.read_less)
                else context.getString(R.string.read_more)
            viewHolder.moreButton?.setOnClickListener {
                item.moreOpen = !item.moreOpen
                viewHolder.descriptionMoreTextView?.visibility =
                    if (item.moreOpen) View.VISIBLE else View.GONE
                viewHolder.moreButton.text =
                    if (item.moreOpen) context.getString(R.string.read_less)
                    else context.getString(R.string.read_more)
            }

            return rowView!!
        }

        override fun isEnabled(position: Int): Boolean {
            return dataSource[position].isChecked
        }

        override fun areAllItemsEnabled(): Boolean {
            return dataSource.all { it.isChecked }
        }


    }

}