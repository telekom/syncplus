package de.telekom.syncplus.ui.main.contacts

import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.spica.Contact
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AddressBookContract {

    interface ViewModel {
        val state: StateFlow<State>
        val action: SharedFlow<Action>

        fun fetchGroups(currentSelection: List<Group>)
        fun onGroupSelected(group: SelectableGroup)
        fun onGroupClicked(group: SelectableGroup)
        fun onAccepted()
    }

    data class State(
        val groupList: List<SelectableGroup> = emptyList()
    )

    sealed interface Action {
        class NavigateToGroup(val contacts: List<Contact>) : Action
        class FinishWithSelection(val groups: List<Group>) : Action
    }

    data class SelectableGroup(
        val name: String?,
        val contactsCount: Int,
        val isSelected: Boolean = false,
        val referenceGroups: List<Group>
    )
}