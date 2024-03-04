package de.telekom.syncplus.ui.setup.contacts.list

import de.telekom.dtagsyncpluskit.model.spica.Contact
import kotlinx.coroutines.flow.StateFlow

interface ContactListContract {
    interface ViewModel {
        val state: StateFlow<State>

        fun viewEvent(event: ViewEvent)
    }

    data class State(
        val contacts: List<Contact> = emptyList()
    )

    sealed interface ViewEvent {
        class ReadGroups(val groupIds: LongArray) : ViewEvent
    }
}