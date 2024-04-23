package de.telekom.syncplus.ui.setup.contacts

import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.model.Group
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SetupContract {
    interface ViewModel {
        val state: StateFlow<State>
        val action: SharedFlow<Action>
        val navigation: Flow<Navigation>

        fun viewEvent(event: ViewEvent)
    }

    data class State(
        val largeTopBar: Boolean = false,
        val currentStep: Int = 0,
        val maxSteps: Int = 0,
        val description: String = "",
        val hasBackButton: Boolean = false,
        val hasHelpButton: Boolean = false,
        val isCalendarEnabled: Boolean = true,
        val isContactsEnabled: Boolean = true,
        val isEmailEnabled: Boolean = true,
        val selectedGroups: List<Group>? = null,
        val calendars: List<Collection>? = null,
    )

    sealed interface Action {
        data object ShowError : Action
        class TryRequestPermissions(
            val isCalendarEnabled: Boolean,
            val isContactsEnabled: Boolean,
            val isEmailEnabled: Boolean
        ) : Action

        class CopyContacts(
            val accountName: String,
            val currentStep: Int,
            val maxSteps: Int,
            val selectedGroups: List<Group>?,
        ) : Action

        class SelectGroups(
            val selectedGroups: List<Group>?,
        ) : Action
    }

    sealed interface Navigation {
        class NavigateToNextStep(
            val accountName: String,
            val isCalendarEnabled: Boolean,
            val isContactsEnabled: Boolean,
            val isEmailEnabled: Boolean
        ) : Navigation
    }

    sealed interface ViewEvent {
        class Init(val authHolder: AuthHolder) : ViewEvent
        data object CheckPermissions : ViewEvent
        data object TryToGoNextStep : ViewEvent
        data object TryToCopyContacts : ViewEvent
        data object TryToSelectGroups : ViewEvent
        class DiscoverServices(val collectionType: String? = null) : ViewEvent
        class FetchCollections(
            val collectionType: String,
            val isSyncEnabled: Boolean
        ) : ViewEvent

        data object SetupAccount : ViewEvent
        class SetupStep(
            val description: String,
            val hasBackButton: Boolean,
            val hasHelpButton: Boolean,
            val large: Boolean = false
        ) : ViewEvent

        class SetCalendarEnabled(val enabled: Boolean) : ViewEvent
        class SetContactsEnabled(val enabled: Boolean) : ViewEvent
        class SetEmailEnabled(val enabled: Boolean) : ViewEvent
        class SetCurrentStep(val step: Int) : ViewEvent
        class SetMaxSteps(val steps: Int) : ViewEvent
        class SetSelectedGroups(val groups: List<Group>) : ViewEvent
        class EnableCalendarSync(val collection: Collection, val isSyncEnabled: Boolean) : ViewEvent
    }
}