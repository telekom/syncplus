package de.telekom.syncplus.ui.setup.contacts.groups

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.telekom.syncplus.R
import de.telekom.syncplus.databinding.ContactsListItemBinding
import de.telekom.syncplus.extensions.inflater
import de.telekom.syncplus.ui.setup.contacts.groups.AddressBookContract.SelectableGroup

class AddressBookAdapter(
    private val onClicked: (group: SelectableGroup) -> Unit,
    private val onSelectionChanged: (group: SelectableGroup) -> Unit,
) : ListAdapter<SelectableGroup, AddressBookAdapter.GroupViewHolder>(GroupDiffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): GroupViewHolder {
        return GroupViewHolder(ContactsListItemBinding.inflate(parent.inflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: GroupViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(
        private val binding: ContactsListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: SelectableGroup) {
            with(binding) {
                text.text = "${item.name} (${item.contactsCount})"

                contactCheckbox.setOnCheckedChangeListener(null)
                contactCheckbox.isChecked = item.isSelected

                text.typeface =
                    if (bindingAdapterPosition == 0) {
                        ResourcesCompat.getFont(text.context, R.font.telegrotesknext_bold)
                    } else {
                        ResourcesCompat.getFont(text.context, R.font.telegrotesknext_regular)
                    }

                arrowButton.setOnClickListener { onClicked(item) }
                contactCheckbox.setOnCheckedChangeListener { _, _ -> onSelectionChanged(item) }
                rowContainer.setOnClickListener { onClicked(item) }
            }
        }
    }

    companion object {
        object GroupDiffCallback : DiffUtil.ItemCallback<SelectableGroup>() {
            override fun areItemsTheSame(
                oldItem: SelectableGroup,
                newItem: SelectableGroup,
            ): Boolean {
                // Considering that groups are the same if they have the same name.
                // Such way we will combine the same groups from several accounts into single.
                // In case of selection such a "combined" group - groups from all accounts will be selected
                return oldItem.name == newItem.name
            }

            override fun areContentsTheSame(
                oldItem: SelectableGroup,
                newItem: SelectableGroup,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
