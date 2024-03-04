package de.telekom.syncplus.ui.setup.contacts.groups

import de.telekom.dtagsyncpluskit.model.Group
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AddressBookContract {
    interface ViewModel {
        val state: StateFlow<State>
        val action: SharedFlow<Action>

        fun fetchGroups(currentSelection: List<Group>?)

        fun onGroupSelected(group: SelectableGroup)

        fun onGroupClicked(group: SelectableGroup)

        fun onAccepted()
    }

    data class State(
        val groupList: List<SelectableGroup> = emptyList(),
    )

    sealed interface Action {
        class NavigateToGroup(val groupIds: LongArray) : Action

        class FinishWithSelection(val groups: List<Group>) : Action
    }

    data class SelectableGroup(
        val name: String?,
        val contactsCount: Int,
        val isSelected: Boolean = false,
        val referenceGroups: List<Group>,
    )
}
