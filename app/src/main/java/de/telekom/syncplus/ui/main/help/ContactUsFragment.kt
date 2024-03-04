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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import de.telekom.dtagsyncpluskit.ui.BaseFragment
import de.telekom.dtagsyncpluskit.ui.BaseListAdapter
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.FragmentListviewBinding
import de.telekom.syncplus.util.viewbinding.viewBinding

data class ContactModel(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    @StringRes val linkText: Int?,
    @StringRes val disclaimer: Int?,
)

class ContactUsFragment : BaseFragment(R.layout.fragment_listview) {
    override val TAG: String
        get() = "CONTACT_US_FRAGMENT"

    companion object {
        fun newInstance() = ContactUsFragment()
    }

    private val binding by viewBinding(FragmentListviewBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val models =
            listOf(
                ContactModel(
                    R.drawable.ic_chat_outline,
                    R.string.contact_title_row0,
                    R.string.contact_text_row0,
                    R.string.contact_linktext_row0,
                    null,
                ),
            /*ContactModel(
                R.drawable.ic_help_accent,
                R.string.contact_title_row1,
                R.string.contact_text_row1,
                R.string.contact_linktext_row1,
                null
            ),*/
                ContactModel(
                    R.drawable.ic_phone_outline,
                    R.string.contact_title_row2,
                    R.string.contact_text_row2,
                    null,
                    R.string.contact_disclaimer_row2,
                ),
            )

        binding.list.adapter =
            ContactAdapter(requireContext(), models) { position ->
                when (position) {
                    0, 1 -> {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://telekomhilft.telekom.de"),
                            ),
                        )
                    }
                }
            }
    }
}

private class ContactAdapter(
    context: Context,
    dataSource: List<ContactModel>,
    private val onButtonClick: (position: Int) -> Unit,
) : BaseListAdapter<ContactModel>(context, dataSource) {
    private class ViewHolder(view: View?) {
        val title = view?.findViewById<TextView>(R.id.title)
        val text = view?.findViewById<TextView>(R.id.text)
        val icon = view?.findViewById<ImageView>(R.id.icon)
        val button = view?.findViewById<MaterialButton>(R.id.button)
        val disclaimer = view?.findViewById<TextView>(R.id.disclaimer)
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val viewHolder: ViewHolder?
        val rowView: View?

        if (convertView == null) {
            rowView = inflater.inflate(R.layout.contact_row, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            rowView = convertView
            viewHolder = rowView.tag as ViewHolder
        }

        val item = getItem(position)
        viewHolder.title?.text = context.getString(item.title)
        viewHolder.text?.text = context.getString(item.text)
        viewHolder.icon?.setImageResource(item.icon)
        viewHolder.button?.visibility = View.GONE
        viewHolder.disclaimer?.visibility = View.GONE
        if (item.linkText != null) {
            viewHolder.button?.text = context.getString(item.linkText)
            viewHolder.button?.setOnClickListener { onButtonClick(position) }
            viewHolder.button?.visibility = View.VISIBLE
        }
        if (item.disclaimer != null) {
            viewHolder.disclaimer?.text = context.getString(item.disclaimer)
            viewHolder.disclaimer?.visibility = View.VISIBLE
        }

        return rowView!!
    }

    override fun isEnabled(position: Int): Boolean {
        return true
    }
}
