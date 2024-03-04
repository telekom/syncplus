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
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SyncStatusObserver
import android.os.IBinder
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.paging.PagedList
import androidx.paging.toLiveData
import de.telekom.dtagsyncpluskit.BuildConfig
import de.telekom.dtagsyncpluskit.api.ServiceEnvironments
import de.telekom.dtagsyncpluskit.davx5.model.AppDatabase
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Service
import de.telekom.dtagsyncpluskit.davx5.settings.AccountSettings
import de.telekom.dtagsyncpluskit.davx5.syncadapter.DavService
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import java.io.Closeable
import java.util.concurrent.Executors

@Suppress("unused")
class CollectionFetcher(
    private val context: Context,
    private val account: Account,
    id: Long,
    private val collectionType: String,
    private val onUnauthorized: (account: Account) -> Unit,
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
    private val mServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                mDavService = service as? DavService.InfoBinder
                mDavService?.addRefreshingStatusListener(this@CollectionFetcher, true)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mDavService = null
            }
        }

    init {
        val intent = Intent(context, DavService::class.java)
        if (context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            mDavServiceConnection = mServiceConnection
        }

        mExecutor.submit {
            mSyncStatusHandle =
                ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_PENDING + ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE,
                    this,
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

    fun refresh(isSyncEnabled: Boolean) {
        serviceId.value?.let { serviceId ->
            DavService.refreshCollections(context, serviceId, isSyncEnabled)
        }
    }

    // DavService.RefreshingStatusListener
    @WorkerThread
    override fun onDavRefreshStatusChanged(
        id: Long,
        refreshing: Boolean,
    ) {
        if (serviceId.value == id) {
            _isRefreshing.postValue(refreshing)
        }
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
                ServiceEnvironments.fromBuildConfig(BuildConfig.ENVIRON[BuildConfig.FLAVOR]!!),
            )
        CountlyWrapper.addCrashBreadcrumb(unauthorizedException)
        onUnauthorized(account)
    }

    // SyncStatusObserver
    override fun onStatusChanged(which: Int) {
        mExecutor.submit { checkSyncStatus() }
    }

    private fun checkSyncStatus() {
        if (collectionType == Collection.TYPE_ADDRESSBOOK) {
            val syncActive = ContentResolver.isSyncActive(
                account,
                ContactsContract.AUTHORITY,
            )
            val syncPending = ContentResolver.isSyncPending(
                account,
                ContactsContract.AUTHORITY,
            )

            _isSyncActive.postValue(syncActive)
            _isSyncPending.postValue(syncPending)
        } else {
            _isSyncActive.postValue(
                ContentResolver.isSyncActive(
                    account,
                    CalendarContract.AUTHORITY
                )
            )
            _isSyncPending.postValue(
                ContentResolver.isSyncPending(
                    account,
                    CalendarContract.AUTHORITY
                )
            )
        }
    }
}
