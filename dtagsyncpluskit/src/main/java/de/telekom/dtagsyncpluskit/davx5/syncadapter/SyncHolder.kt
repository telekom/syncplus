package de.telekom.dtagsyncpluskit.davx5.syncadapter

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.syncadapter.SyncHolder.isSyncPaused
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class for tracking syncs and managing their states.
 *
 * Since the Android SDK does not provide a built-in solution for pausing and restoring a sync,
 * this class serves as a workaround to address this limitation.
 *
 * Running syncs can be canceled and restarted. Therefore, this class monitors running syncs
 * and triggers their pausing/restoring when the [isSyncPaused] flag allows it.
 * */
object SyncHolder {
    private var isSyncPaused = AtomicBoolean(false)

    // Keep the track on the running syncs with WeakReferences to handle cases when
    // sync is cancelled due to account deletion or thread interruption
    private val runningSyncs =
        Collections.synchronizedSet(
            mutableSetOf<WeakReference<PendingSync>>(),
        )

    fun isSyncAllowed(): Boolean {
        return !isSyncPaused.get()
    }

    fun setSyncAllowed(isSyncAllowed: Boolean) {
        if (isSyncAllowed) {
            restoreAllSyncs()
        } else {
            pauseAllSyncs()
        }
    }

    fun addSync(sync: PendingSync): Boolean {
        // prevent multiple syncs of the same authority to be run for the same account
        if (runningSyncs
                .mapNotNull { it.get() }
                .any { it.account == sync.account && it.authority == sync.authority }
        ) {
            Logger.log.warning("There's already another ${sync.authority} sync running for ${sync.account}, aborting")

            return false
        }

        runningSyncs += WeakReference(sync)

        return true
    }

    fun removeSync(
        account: Account,
        authority: String,
    ) {
        // Don't delete sync when syncing is forbidden
        // as the sync may be cancelled
        if (!isSyncAllowed()) return

        runningSyncs.removeAll { it.get() == null || it.get()?.account == account && it.get()?.authority == authority }
    }

    private fun pauseAllSyncs() {
        isSyncPaused.set(true)

        Logger.log.info("Pausing all running syncs (syncs count: ${runningSyncs.size})")

        runningSyncs
            .mapNotNull { it.get() }
            .forEach {
                ContentResolver.cancelSync(it.account, it.authority)
            }
    }

    private fun restoreAllSyncs() {
        isSyncPaused.set(false)

        Logger.log.info("Restoring all paused syncs (syncs count: ${runningSyncs.size})")

        runningSyncs
            .mapNotNull { it.get() }
            .forEach {
                ContentResolver.requestSync(it.account, it.authority, it.syncExtras)
            }
    }

    data class PendingSync(
        val account: Account,
        val authority: String,
        val syncExtras: Bundle,
    )
}
