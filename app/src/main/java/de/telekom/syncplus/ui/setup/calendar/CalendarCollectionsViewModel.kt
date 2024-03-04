package de.telekom.syncplus.ui.setup.calendar

import android.accounts.Account
import android.app.Application
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.utils.IDMAccountManager
import de.telekom.dtagsyncpluskit.xdav.CollectionFetcher
import de.telekom.syncplus.dav.DavNotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CalendarCollectionsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val mDB = AppDatabase.getInstance(app)
    private val accountManager by lazy {
        IDMAccountManager(app)
    }
    private val _fetcher = MutableLiveData<CollectionFetcher?>(null)
    private val _showError = MutableSharedFlow<Unit>()
    val fetcher: LiveData<CollectionFetcher?> = _fetcher
    val showError: SharedFlow<Unit> = _showError.asSharedFlow()

    fun discoverServices(
        account: Account,
        serviceEnvironments: ServiceEnvironments,
        collectionType: String,
        calendarSyncEnabled: Boolean,
        contactSyncEnabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            accountManager.discoverServicesConfiguration(
                account,
                serviceEnvironments,
                calendarSyncEnabled,
                contactSyncEnabled,
            )?.let {
                fetch(account, collectionType, calendarSyncEnabled)
            } ?: _showError.emit(Unit)
        }
    }

    fun fetch(
        account: Account,
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

            val serviceId = mDB.serviceDao().getIdByAccountAndType(account.name, serviceType)

            if (serviceId == null) {
                _showError.emit(Unit)
                return@launch
            }

            val newFetcher = CollectionFetcher(
                app,
                account,
                serviceId,
                collectionType,
            ) {
                DavNotificationUtils.showReloginNotification(app, authority, it)
            }
            newFetcher.refresh(isSyncEnabled)
            _fetcher.postValue(newFetcher)
        }
    }

    fun enableSync(
        item: Collection,
        enabled: Boolean,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val newItem = item.copy(sync = enabled)
        mDB.collectionDao().update(newItem)
        item.sync = enabled // also update the collection item
    }

    fun enableSync(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val serviceId = fetcher.value?.serviceId?.value ?: return@launch
            mDB.collectionDao().getByServiceAndType(serviceId, Collection.TYPE_CALENDAR)
                .forEach { item ->
                    val newItem = item.copy(sync = enabled)
                    mDB.collectionDao().update(newItem)
                    item.sync = enabled // also update the collection item
                }
        }
    }
}