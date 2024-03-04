package de.telekom.syncplus.ui.main.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.contacts.ContactsFetcher
import de.telekom.dtagsyncpluskit.model.Group
import de.telekom.dtagsyncpluskit.model.Group.Companion.ALL_CONTACTS_GROUP_ID
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddressBookViewModel(application: Application) :
    AndroidViewModel(application),
    AddressBookContract.ViewModel {
    private val contactsFetcher by lazy { ContactsFetcher(getApplication()) }

    private val _state = MutableStateFlow(AddressBookContract.State())
    private val _action = MutableSharedFlow<AddressBookContract.Action>()

    override val state: StateFlow<AddressBookContract.State> = _state.asStateFlow()
    override val action: SharedFlow<AddressBookContract.Action> = _action.asSharedFlow()

    override fun fetchGroups(currentSelection: List<Group>) {
        viewModelScope.launch {
            val fetchedGroups =
                contactsFetcher.allGroups()
                    .groupBy { it.name }
                    .map { (name, groupList) ->
                        val contactsCount = groupList.sumOf { it.numberOfContacts ?: 0 }

                        AddressBookContract.SelectableGroup(
                            name = name,
                            contactsCount = contactsCount,
                            referenceGroups = groupList,
                        )
                    }
                    .toMutableList()

            val allContactsName = getApplication<App>().getString(R.string.all_contacts)
            val allContactsCount = contactsFetcher.allContacts().size

            fetchedGroups.add(
                0,
                AddressBookContract.SelectableGroup(
                    name = allContactsName,
                    contactsCount = allContactsCount,
                    referenceGroups =
                        listOf(
                            Group(
                                ALL_CONTACTS_GROUP_ID,
                                allContactsName,
                                allContactsCount,
                            ),
                        ),
                ),
            )

            val allGroups =
                fetchedGroups
                    .map {
                        val groupSelection =
                            currentSelection.find { selection ->
                                selection.name == it.name && it.referenceGroups.contains(selection)
                            }?.isSelected

                        it.copy(isSelected = groupSelection ?: true)
                    }

            mutateState {
                copy(groupList = allGroups)
            }
        }
    }

    override fun onGroupSelected(group: AddressBookContract.SelectableGroup) {
        viewModelScope.launch {
            val groupList =
                _state.value.groupList
                    .map {
                        if (it == group) {
                            it.copy(isSelected = !group.isSelected)
                        } else {
                            it
                        }
                    }

            mutateState {
                copy(groupList = groupList)
            }
        }
    }

    override fun onGroupClicked(group: AddressBookContract.SelectableGroup) {
        viewModelScope.launch {
            val contacts =
                _state.value.groupList
                    .filter { it == group }
                    .flatMap { selectableGroup -> selectableGroup.referenceGroups }
                    .map { group -> group.groupId }
                    .flatMap { groupId -> contactsFetcher.allContacts(groupId) }

            _action.emit(AddressBookContract.Action.NavigateToGroup(contacts))
        }
    }

    override fun onAccepted() {
        viewModelScope.launch {
            val selectedGroups =
                _state.value.groupList
                    .filter { it.isSelected }
                    .flatMap { it.referenceGroups }

            _action.emit(AddressBookContract.Action.FinishWithSelection(selectedGroups))
        }
    }

    private suspend fun mutateState(mutator: AddressBookContract.State.() -> AddressBookContract.State) {
        _state.emit(_state.value.mutator())
    }
}
