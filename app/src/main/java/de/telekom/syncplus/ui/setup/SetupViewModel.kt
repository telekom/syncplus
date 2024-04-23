package de.telekom.syncplus.ui.setup

import android.accounts.Account
import android.app.Application
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.model.AuthHolder
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.dtagsyncpluskit.xdav.CollectionFetcher
import de.telekom.syncplus.App
import de.telekom.syncplus.R
import de.telekom.syncplus.dav.DavNotificationUtils
import de.telekom.syncplus.ui.main.account.AccountSettingsFragment
import de.telekom.syncplus.ui.setup.contacts.SetupContract
import de.telekom.syncplus.ui.setup.contacts.SetupContract.Action.CopyContacts
import de.telekom.syncplus.ui.setup.contacts.SetupContract.Action.SelectGroups
import de.telekom.syncplus.ui.setup.contacts.SetupContract.Action.ShowError
import de.telekom.syncplus.ui.setup.contacts.SetupContract.Action.TryRequestPermissions
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.CheckPermissions
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.DiscoverServices
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.EnableCalendarSync
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.FetchCollections
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.Init
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetCalendarEnabled
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetContactsEnabled
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetCurrentStep
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetEmailEnabled
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetMaxSteps
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetSelectedGroups
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetupAccount
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.SetupStep
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.TryToCopyContacts
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.TryToGoNextStep
import de.telekom.syncplus.ui.setup.contacts.SetupContract.ViewEvent.TryToSelectGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates.notNull


class SetupViewModel(private val app: Application) : AndroidViewModel(app),
    SetupContract.ViewModel {

    private val db = AppDatabase.getInstance(app)
    private val accountManager by lazy {
        IDMAccountManager(app)
    }

    private val _state = MutableStateFlow(SetupContract.State())
    private val _action = MutableSharedFlow<SetupContract.Action>()
    private val _navigation = Channel<SetupContract.Navigation>(Channel.BUFFERED)

    override val state: StateFlow<SetupContract.State> = _state.asStateFlow()
    override val action: SharedFlow<SetupContract.Action> = _action.asSharedFlow()
    override val navigation: Flow<SetupContract.Navigation> = _navigation.receiveAsFlow()

    private var authHolder: AuthHolder by notNull()
    private var account: Account by notNull()
    private var collectionFetcher: CollectionFetcher? = null

    override fun viewEvent(event: SetupContract.ViewEvent) {
        when (event) {
            is Init -> processInitEvent(event.authHolder)
            is DiscoverServices -> discoverServices(event.collectionType)
            is FetchCollections -> fetchCollection(event.collectionType, event.isSyncEnabled)
            is SetupAccount -> setupAccount()
            is SetupStep -> processSetupStep(event)
            is SetCalendarEnabled -> processSetCalendarEnabled(event)
            is SetContactsEnabled -> processSetContactsEnabled(event)
            is SetCurrentStep -> processSetCurrentStep(event)
            is SetEmailEnabled -> processSetEmailEnabled(event)
            is SetMaxSteps -> processSetMaxSteps(event)
            CheckPermissions -> processCheckPermissions()
            TryToCopyContacts -> processCopyContacts()
            TryToSelectGroups -> processSelectGroups()
            TryToGoNextStep -> processTryToGoNextStep()
            is SetSelectedGroups -> processSetSelectedGroups(event)
            is EnableCalendarSync -> enableCalendarSync(event.collection, event.isSyncEnabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectionFetcher = null
    }

    private fun processInitEvent(authHolder: AuthHolder) {
        this.authHolder = authHolder
        this.account = Account(authHolder.accountName, app.getString(R.string.account_type))
    }

    private fun processSetupStep(event: SetupStep) {
        mutateState {
            copy(
                description = event.description,
                hasBackButton = event.hasBackButton,
                hasHelpButton = event.hasHelpButton,
                largeTopBar = event.large
            )
        }
    }

    private fun processSetCalendarEnabled(event: SetCalendarEnabled) {
        mutateState {
            copy(isCalendarEnabled = event.enabled)
        }
    }

    private fun processSetContactsEnabled(event: SetContactsEnabled) {
        mutateState {
            copy(isContactsEnabled = event.enabled)
        }
    }

    private fun processSetEmailEnabled(event: SetEmailEnabled) {
        mutateState {
            copy(isEmailEnabled = event.enabled)
        }
    }

    private fun processSetCurrentStep(event: SetCurrentStep) {
        mutateState {
            val correctedStep = if (event.step > maxSteps) maxSteps else event.step
            copy(currentStep = correctedStep)
        }
    }

    private fun processSetMaxSteps(event: SetMaxSteps) {
        mutateState {
            copy(maxSteps = event.steps)
        }
    }

    private fun processSetSelectedGroups(event: SetSelectedGroups) {
        mutateState {
            copy(selectedGroups = event.groups)
        }
    }

    private fun processCheckPermissions() {
        viewModelScope.launch {
            with(_state.value) {
                _action.emit(
                    TryRequestPermissions(
                        isCalendarEnabled = isCalendarEnabled,
                        isContactsEnabled = isContactsEnabled,
                        isEmailEnabled = isEmailEnabled,
                    )
                )
            }
        }
    }

    private fun processCopyContacts() {
        viewModelScope.launch {
            with(_state.value) {
                _action.emit(
                    CopyContacts(
                        accountName = authHolder.accountName,
                        currentStep = currentStep,
                        maxSteps = maxSteps,
                        selectedGroups = selectedGroups,
                    )
                )
            }
        }
    }

    private fun processSelectGroups() {
        viewModelScope.launch {
            with(_state.value) {
                _action.emit(
                    SelectGroups(selectedGroups)
                )
            }
        }
    }

    private fun processTryToGoNextStep() {
        viewModelScope.launch {
            with(_state.value) {
                _navigation.send(
                    SetupContract.Navigation.NavigateToNextStep(
                        accountName = authHolder.accountName,
                        isCalendarEnabled = isCalendarEnabled,
                        isContactsEnabled = isContactsEnabled,
                        isEmailEnabled = isEmailEnabled
                    )
                )
            }
        }
    }

    private fun discoverServices(collectionType: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val calendarSyncEnabled = _state.value.isCalendarEnabled
            val contactSyncEnabled = _state.value.isContactsEnabled

            accountManager.discoverServicesConfiguration(
                account,
                App.serviceEnvironments(),
                calendarSyncEnabled,
                contactSyncEnabled,
            )?.let {
                when (collectionType) {
                    Collection.TYPE_CALENDAR -> fetchCollection(Collection.TYPE_CALENDAR, calendarSyncEnabled)
                    Collection.TYPE_ADDRESSBOOK -> fetchCollection(Collection.TYPE_ADDRESSBOOK, contactSyncEnabled)
                    null -> setupSettings()
                }
            } ?: _action.emit(ShowError)
        }
    }

    private fun setupAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check whether services are discovered
            // Othervise settings applying would not have effect
            if (!isServicesDiscovered(authHolder.accountName, Service.TYPE_CALDAV)) {
                // Show error in case services not discovered yet
                _action.emit(ShowError)
                return@launch
            }
            if (!isServicesDiscovered(authHolder.accountName, Service.TYPE_CARDDAV)) {
                _action.emit(ShowError)
                return@launch
            }

            setupSettings()
        }
    }

    private suspend fun setupSettings() {
        val accountSettings = AccountSettings(app.applicationContext, account)
        with(_state.value) {
            // Call setSyncAllCalendars/setSyncAllAddressBooks first!
            accountSettings.setSyncAllCalendars(isCalendarEnabled)
            accountSettings.setCalendarSyncEnabled(isCalendarEnabled)
            accountSettings.setSyncAllAddressBooks(isContactsEnabled)
            accountSettings.setContactSyncEnabled(isContactsEnabled)
            _navigation.send(
                SetupContract.Navigation.NavigateToNextStep(
                    accountName = authHolder.accountName,
                    isCalendarEnabled = isCalendarEnabled,
                    isContactsEnabled = isContactsEnabled,
                    isEmailEnabled = isEmailEnabled
                )
            )
        }
    }

    private fun isServicesDiscovered(
        accountName: String,
        serviceType: String,
    ): Boolean {
        return db.serviceDao().getIdByAccountAndType(accountName, serviceType) != null
    }

    private fun mutateState(mutator: SetupContract.State.() -> SetupContract.State) {
        viewModelScope.launch {
            val newState = _state.value.mutator()
            _state.emit(newState)
        }
    }

    private fun fetchCollection(
        collectionType: String,
        isSyncEnabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val serviceType = when (collectionType) {
                Collection.TYPE_CALENDAR -> Service.TYPE_CALDAV
                Collection.TYPE_ADDRESSBOOK -> Service.TYPE_CARDDAV
                else -> null
            } ?: return@launch
            val authority = when (collectionType) {
                Collection.TYPE_CALENDAR -> CalendarContract.AUTHORITY
                Collection.TYPE_ADDRESSBOOK -> ContactsContract.AUTHORITY
                else -> null
            } ?: return@launch

            val serviceId = db.serviceDao().getIdByAccountAndType(account.name, serviceType)

            if (serviceId == null) {
                _action.emit(ShowError)
                return@launch
            }

            withContext(Dispatchers.Main) {
                collectionFetcher = CollectionFetcher(
                    app,
                    account,
                    serviceId,
                    collectionType,
                ) {
                    DavNotificationUtils.showReloginNotification(app, authority, it)
                }

                collectionFetcher?.refresh(isSyncEnabled)
                observeCollections()
            }
        }
    }

    private fun observeCollections() {
        viewModelScope.launch {
            collectionFetcher
                ?.observeCollections()
                ?.collect {
                    mutateState {
                        this.copy(
                            calendars = AccountSettingsFragment.sortCalendarCollections(it)
                        )
                    }
                }
        }
    }

    private fun enableCalendarSync(
        item: Collection,
        enabled: Boolean,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val newItem = item.copy(sync = enabled)
        db.collectionDao().update(newItem)
    }
}
