package de.telekom.syncplus.ui.setup.contacts.duplicates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.model.spica.Duplicate
import de.telekom.syncplus.App
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DuplicatesListViewModel(private val app: Application) : AndroidViewModel(app) {
    private val actionFlow = MutableSharedFlow<Action>()
    private val stateFlow = MutableStateFlow(State())

    val action: SharedFlow<Action> = actionFlow.asSharedFlow()
    val state: StateFlow<State> = stateFlow.asStateFlow()

    init {
        setupState()
    }

    fun onEvent(event: ViewEvent) {
        when (event) {
            is ViewEvent.NavigateToDetails -> handleNavigateToDetails(event)
        }
    }

    private fun handleNavigateToDetails(event: ViewEvent.NavigateToDetails) {
        viewModelScope.launch {
            actionFlow.emit(Action.NavigateToDetails(event.duplicate))
        }
    }

    private fun setupState() {
        viewModelScope.launch {
            val duplicates = (app as App).duplicates
                ?.mapNotNull { duplicate ->
                    val similarContacts = duplicate.similarContacts?.filter { it.fromRequest == false }

                    if (similarContacts.isNullOrEmpty()) return@mapNotNull null

                    duplicate.copy(similarContacts = similarContacts)
                } ?: emptyList()

            stateFlow.emit(State(duplicates))
        }
    }

    data class State(
        val duplicates: List<Duplicate> = listOf()
    )

    sealed interface Action {
        data class NavigateToDetails(val duplicate: Duplicate) : Action
    }

    sealed interface ViewEvent {
        data class NavigateToDetails(val duplicate: Duplicate) : ViewEvent
    }
}