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
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentListviewBinding
import de.telekom.syncplus.util.viewbinding.viewBinding
import kotlinx.parcelize.Parcelize

@Parcelize
data class HelpModel(
    val type: Int,
    val icon: Int? = null,
    val text: String? = null,
) : Parcelable {
    companion object {
        const val SECTION = 0
        const val ITEM = 1
        const val INFO = 2
    }
}

class HelpFragment : BaseFragment(R.layout.fragment_listview) {
    override val TAG: String
        get() = "HELP_FRAGMENT"

    var currentWebView: WebViewFragment? = null

    companion object {
        fun newInstance() = HelpFragment()

        @SuppressLint("StaticFieldLeak")
        var instance: HelpFragment? = null
    }

    private var mTitleView: TextView? = null
    private val binding by viewBinding(FragmentListviewBinding::bind)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        mTitleView?.text = "No title"
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        mTitleView = activity?.findViewById(R.id.topbarTitle)
        binding.list.dividerHeight = 0
        binding.list.divider =
            AppCompatResources.getDrawable(
                requireContext(),
                android.R.color.transparent,
            )

        fun helpSection(text: String) = HelpModel(HelpModel.SECTION, null, text)

        fun helpItem(
            icon: Int,
            text: String,
        ) = HelpModel(HelpModel.ITEM, icon, text)

        fun info() = HelpModel(HelpModel.INFO, null, null)

        val model =
            listOf(
                helpSection(getString(R.string.help_and_answers)),
                helpItem(R.drawable.ic_help_accent, getString(R.string.help_for_syncplus)),
                helpItem(R.drawable.ic_chat_outline, getString(R.string.reach_us)),
                helpItem(R.drawable.ic_feedback, getString(R.string.feedback)),
                helpSection(getString(R.string.disclaimer)),
                helpItem(R.drawable.ic_data_privacy_outline, getString(R.string.datasecurity)),
                helpItem(R.drawable.ic_privacy_settings, getString(R.string.datasecurity_settings)),
                helpItem(R.drawable.ic_external_link_outline, getString(R.string.imprint)),
                helpItem(R.drawable.ic_contract_outline, getString(R.string.legal)),
                helpItem(R.drawable.ic_information_icon, getString(R.string.licenses)),
                info(),
            )

        binding.list.adapter = HelpAdapter(requireContext(), model, parentFragmentManager)
        binding.list.setOnItemClickListener { _, _, position, _ ->
            val item = (binding.list.adapter as HelpAdapter).getItem(position)
            if (item.type == HelpModel.ITEM) {
                when (position) {
                    1 -> {
                        val url =
                            Uri.parse("https://kommunikationsdienste.t-online.de/redirect/syncplus/help")
                        mTitleView?.text = getString(R.string.help_for_syncplus)

                        push(R.id.container, WebViewFragment.newInstance(url, this))
                    }
                    2 -> {
                        // So erreichen Sie uns
                        mTitleView?.text = getString(R.string.contact)
                        // mTitleView?.text = getString(R.string.contact)
                        push(R.id.container, ContactUsFragment.newInstance())
                    }
                    3 -> {
                        // Feedback
                        // mTitleView?.text = getString(R.string.feedback)
                        mTitleView?.text = getString(R.string.feedback)
                        push(R.id.container, FeedbackFragment.newInstance())
                    }
                    5 -> {
                        // Datenschutz
                        val url =
                            Uri.parse("https://kommunikationsdienste.t-online.de/redirect/syncplus-aos/dataprivacy")
                        mTitleView?.text = getString(R.string.datasecurity)
                        push(R.id.container, WebViewFragment.newInstance(url, this))
                    }
                    6 -> {
                        // Datenschutz-Einstellungen
                        startActivity(
                            DataPrivacyDialogActivity.newIntent(requireActivity())
                                .putExtra("comeFromDeepLink", true),
                        )
                    }
                    7 -> {
                        // Impressum
                        val url =
                            Uri.parse("https://kommunikationsdienste.t-online.de/redirect/syncplus/imprint")

                        mTitleView?.text = getString(R.string.imprint)

                        push(R.id.container, WebViewFragment.newInstance(url, this))
                    }
                    8 -> {
                        // Rechtliches
                        val url =
                            Uri.parse("https://kommunikationsdienste.t-online.de/redirect/syncplus/legal")
                        mTitleView?.text = getString(R.string.legal)
                        push(R.id.container, WebViewFragment.newInstance(url, this))
                    }
                    9 -> {
                        // Lizenzen
                        val url = Uri.parse("file:///android_asset/osdf-git.html")
                        mTitleView?.text = getString(R.string.licenses)
                        push(R.id.container, WebViewFragment.newInstance(url, this))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mTitleView?.text = getString(R.string.information)
    }
}

class HelpAdapter(
    context: Context,
    dataSource: List<HelpModel>,
    private val fragmentManager: FragmentManager,
) : BaseListAdapter<HelpModel>(context, dataSource) {
    private class ViewHolder(view: View?, val viewType: Int) {
        val title = view?.findViewById<TextView>(R.id.title)
        val version = view?.findViewById<TextView>(R.id.version)
        val iconView = view?.findViewById<ImageView>(R.id.icon)
        val hiddenButton = view?.findViewById<ImageView>(R.id.hiddenButton)
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val viewHolder: ViewHolder?
        val rowView: View?

        val viewType = getItemViewType(position)
        val layout =
            when (viewType) {
                0 -> R.layout.help_list_header
                1 -> R.layout.help_list_item
                2 -> R.layout.help_info_row
                else -> throw IllegalStateException("Unsupported viewType")
            }

        if (convertView == null) {
            rowView = inflater.inflate(layout, parent, false)
            viewHolder = ViewHolder(rowView, viewType)
            rowView.tag = viewHolder
        } else {
            rowView = convertView
            viewHolder = rowView.tag as ViewHolder
        }

        assert(viewHolder.viewType == viewType)
        val item = getItem(position)
        when (viewType) {
            HelpModel.SECTION -> {
                viewHolder.title?.text = item.text
            }
            HelpModel.ITEM -> {
                viewHolder.title?.text = item.text
                viewHolder.iconView?.setImageResource(item.icon!!)
            }
            HelpModel.INFO -> {
                val appVersion = getAppVersion()
                viewHolder.version?.text =
                    context.getString(R.string.version_text, appVersion.first, appVersion.second)

                var clicked = 0
                viewHolder.hiddenButton?.setOnClickListener {
                    Logger.log.fine("DEBUG CLICK $clicked")
                    clicked += 1
                    if (clicked > 4) {
                        clicked = 0
                        val dialog = DebugDialog()
                        dialog.show(fragmentManager, null)
                    }
                }
            }
        }

        return rowView!!
    }

    override fun getItemViewType(position: Int): Int {
        return dataSource[position].type
    }

    override fun getViewTypeCount(): Int {
        return 3
    }

    override fun isEnabled(position: Int): Boolean {
        return dataSource[position].type == HelpModel.ITEM
    }

    @Suppress("DEPRECATION")
    private fun getAppVersion(): Pair<String, Int> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            Pair(packageInfo.versionName, packageInfo.versionCode)
        } catch (ex: Exception) {
            Pair("1.0.0", 0)
        }
    }
}
