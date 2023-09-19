/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.telekom.dtagsyncpluskit.xdav

import android.accounts.Account
import android.content.*
import android.os.IBinder
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.paging.*
import de.telekom.dtagsyncpluskit.R
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.syncadapter.DavService
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.Closeable
import java.util.concurrent.Executors

@Suppress("unused")
class CollectionFetcher(
    private val context: Context,
    private val account: Account,
    id: Long,
    private val collectionType: String,
    private val onUnauthorized: (account: Account) -> Unit
) : Closeable, DavService.RefreshingStatusListener, SyncStatusObserver {

    private val mDB = AppDatabase.getInstance(context)
    private val mExecutor = Executors.newSingleThreadExecutor()

    private var mSyncStatusHandle: Any? = null
    private val _isRefreshing = MutableLiveData<Boolean>()
    private val _isSyncActive = MutableLiveData<Boolean>()
    private val _isSyncPending = MutableLiveData<Boolean>()
    private val _serviceId = MutableLiveData(id)
    val isRefreshing: LiveData<Boolean> = _isRefreshing
    val isSyncActive: LiveData<Boolean> = _isSyncActive
    val isSyncPending: LiveData<Boolean> = _isSyncPending
    val serviceId: LiveData<Long> = _serviceId
    val collections: LiveData<PagedList<Collection>> =
        serviceId.switchMap { service ->
            mDB.collectionDao().pageByServiceAndType(service, collectionType).toLiveData(25)
        }

    @Volatile
    private var mDavService: DavService.InfoBinder? = null
    private var mDavServiceConnection: ServiceConnection? = null
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mDavService = service as? DavService.InfoBinder
            mDavService?.addRefreshingStatusListener(this@CollectionFetcher, true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mDavService = null
        }
    }

    init {
        val intent = Intent(context, DavService::class.java)
        if (context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE))
            mDavServiceConnection = mServiceConnection

        mExecutor.submit {
            mSyncStatusHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_PENDING + ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE,
                this
            )
        }
    }

    override fun close() {
        mSyncStatusHandle?.let { ContentResolver.removeStatusChangeListener(it) }

        mDavService?.removeRefreshingStatusListener(this)
        mDavServiceConnection?.let {
            context.unbindService(it)
            mDavServiceConnection = null
        }
    }

    fun refresh(serviceEnvironments: ServiceEnvironments, isSyncEnabled: Boolean) {
        serviceId.value?.let { serviceId ->
            DavService.refreshCollections(context, serviceId, serviceEnvironments, isSyncEnabled)
        }
    }

    /* DavService.RefreshingStatusListener */
    @WorkerThread
    override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {
        if (serviceId.value == id)
            _isRefreshing.postValue(refreshing)
    }

    override fun onUnauthorized(
        authority: String,
        account: Account,
        service: Service,
        accountSettings: AccountSettings,
    ) {
        val unauthorizedException: String =
            String.format(
                "Unauthorized Exception(401):\n" +
                        "Account Details: %s,\n" +
                        "Authority:%s\n" +
                        "Service: %s\n" +
                        "Enviroment: %s\n",
                account.toString(),
                authority,
                service.toString(),
                accountSettings.serviceEnvironments.toString(),
            )
        CountlyWrapper.addCrashBreadcrumb(unauthorizedException)
        onUnauthorized(account)
    }

    /* SyncStatusObserver */
    override fun onStatusChanged(which: Int) {
        mExecutor.submit { checkSyncStatus() }
    }

    private fun checkSyncStatus() {
        if (collectionType == Collection.TYPE_ADDRESSBOOK) {
            val mainAuthority = context.getString(R.string.address_books_authority)
            val mainSyncActive = ContentResolver.isSyncActive(account, mainAuthority)
            val mainSyncPending = ContentResolver.isSyncPending(account, mainAuthority)

            val accounts = LocalAddressBook.findAll(context, null, account)
            val syncActive = accounts.any {
                ContentResolver.isSyncActive(
                    it.account,
                    ContactsContract.AUTHORITY
                )
            }
            val syncPending = accounts.any {
                ContentResolver.isSyncPending(
                    it.account,
                    ContactsContract.AUTHORITY
                )
            }

            _isSyncActive.postValue(mainSyncActive || syncActive)
            _isSyncPending.postValue(mainSyncPending || syncPending)
        } else {
            val authorities = mutableListOf(CalendarContract.AUTHORITY)
            _isSyncActive.postValue(authorities.any {
                ContentResolver.isSyncActive(account, it)
            })
            _isSyncPending.postValue(authorities.any {
                ContentResolver.isSyncPending(account, it)
            })
        }
    }
}
