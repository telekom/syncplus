package de.telekom.syncplus.ui.setup.contacts.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.contacts.ContactsFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactListViewModel(application: Application) : AndroidViewModel(application),
    ContactListContract.ViewModel {

    private val _state = MutableStateFlow(ContactListContract.State())
    override val state: StateFlow<ContactListContract.State> = _state.asStateFlow()

    private val contactsFetcher by lazy {
        ContactsFetcher(getApplication())
    }

    override fun viewEvent(event: ContactListContract.ViewEvent) {
        when (event) {
            is ContactListContract.ViewEvent.ReadGroups -> processReadGroupsEvent(event.groupIds)
        }
    }

    private fun processReadGroupsEvent(groupIds: LongArray) {
        viewModelScope.launch {
            val allContacts = groupIds.flatMap { groupId ->
                contactsFetcher.allContacts(groupId)
            }

            _state.emit(ContactListContract.State(allContacts))
        }
    }
}
