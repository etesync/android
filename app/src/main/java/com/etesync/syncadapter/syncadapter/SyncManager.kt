/*
* Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
*/
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalEntryManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.*
import com.etesync.syncadapter.model.SyncEntry.Actions.ADD
import com.etesync.syncadapter.resource.LocalCollection
import com.etesync.syncadapter.resource.LocalResource
import com.etesync.syncadapter.ui.AccountsActivity
import com.etesync.syncadapter.ui.DebugInfoActivity
import com.etesync.syncadapter.ui.ViewCollectionActivity
import io.requery.Persistable
import io.requery.sql.EntityDataStore
import org.jetbrains.anko.defaultSharedPreferences
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException

abstract class SyncManager<T: LocalResource<*>> @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
constructor(protected val context: Context, protected val account: Account, protected val settings: AccountSettings, protected val extras: Bundle, protected val authority: String, protected val syncResult: SyncResult, journalUid: String, protected val serviceType: CollectionInfo.Type, accountName: String): Closeable {

    protected val notificationManager: SyncNotification
    protected val info: CollectionInfo
    protected var localCollection: LocalCollection<T>? = null

    protected var httpClient: HttpClient

    protected var journal: JournalEntryManager? = null
    private var _journalEntity: JournalEntity? = null

    private var numDiscarded = 0

    private val crypto: Crypto.CryptoManager

    private val data: EntityDataStore<Persistable>

    /**
     * remote CTag (uuid of the last entry on the server). We update it when we fetch/push and save when everything works.
     */
    private var remoteCTag: String? = null

    /**
     * Syncable local journal entries.
     */
    private var localEntries: MutableList<JournalEntryManager.Entry>? = null

    /**
     * Syncable remote journal entries (fetch from server).
     */
    private var remoteEntries: List<JournalEntryManager.Entry>? = null

    /**
     * Dirty and deleted resources. We need to save them so we safely ignore ones that were added after we started.
     */
    private var localDeleted: List<T>? = null
    protected var localDirty: List<T> = LinkedList()

    protected abstract val syncErrorTitle: String

    protected abstract val syncSuccessfullyTitle: String

    private val journalEntity: JournalEntity
        get() = JournalModel.Journal.fetch(data, info.getServiceEntity(data), info.uid)

    init {
        // create HttpClient with given logger
        httpClient = HttpClient.Builder(context, settings).setForeground(false).build()

        data = (context.applicationContext as App).data
        val serviceEntity = JournalModel.Service.fetch(data, accountName, serviceType)
        info = JournalEntity.fetch(data, serviceEntity, journalUid)!!.info

        // dismiss previous error notifications
        notificationManager = SyncNotification(context, journalUid, notificationId())
        notificationManager.cancel()

        Logger.log.info(String.format(Locale.getDefault(), "Syncing collection %s (version: %d)", journalUid, info.version))

        if (journalEntity.encryptedKey != null) {
            crypto = Crypto.CryptoManager(info.version, settings.keyPair!!, journalEntity.encryptedKey)
        } else {
            crypto = Crypto.CryptoManager(info.version, settings.password(), info.uid!!)
        }
    }

    protected abstract fun notificationId(): Int

    override fun close() {
        httpClient.close()
    }

    @TargetApi(21)
    fun performSync() {
        var syncPhase = R.string.sync_phase_prepare
        try {
            Logger.log.info("Sync phase: " + context.getString(syncPhase))
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return
            }

            if (Thread.interrupted())
                throw InterruptedException()
            syncPhase = R.string.sync_phase_prepare_fetch
            Logger.log.info("Sync phase: " + context.getString(syncPhase))
            prepareFetch()

            do {
                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_fetch_entries
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                fetchEntries()

                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_apply_remote_entries
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                applyRemoteEntries()
            } while (remoteEntries!!.size == MAX_FETCH)

            do {
                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_prepare_local
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                prepareLocal()

                /* Create journal entries out of local changes. */
                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_create_local_entries
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                createLocalEntries()

                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_apply_local_entries
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                /* FIXME: Skipping this now, because we already override with remote.
                applyLocalEntries();
                */

                if (Thread.interrupted())
                    throw InterruptedException()
                syncPhase = R.string.sync_phase_push_entries
                Logger.log.info("Sync phase: " + context.getString(syncPhase))
                pushEntries()
            } while (localEntries!!.size == MAX_PUSH)

            /* Cleanup and finalize changes */
            if (Thread.interrupted())
                throw InterruptedException()
            syncPhase = R.string.sync_phase_post_processing
            Logger.log.info("Sync phase: " + context.getString(syncPhase))
            postProcess()

            if (numDiscarded > 0) {
                notifyDiscardedChange()
            }
            notifyUserOnSync()

            Logger.log.info("Finished sync with CTag=$remoteCTag")
        } catch (e: SSLHandshakeException) {
            syncResult.stats.numIoExceptions++

            notificationManager.setThrowable(e)
            val detailsIntent = notificationManager.detailsIntent
            detailsIntent.putExtra(KEY_ACCOUNT, account)
            notificationManager.notify(syncErrorTitle, context.getString(syncPhase))
        } catch (e: IOException) {
            Logger.log.log(Level.WARNING, "I/O exception during sync, trying again later", e)
            syncResult.stats.numIoExceptions++
        } catch (e: Exceptions.ServiceUnavailableException) {
            syncResult.stats.numIoExceptions++
            syncResult.delayUntil = if (e.retryAfter > 0) e.retryAfter else Constants.DEFAULT_RETRY_DELAY
        } catch (e: InterruptedException) {
            // Restart sync if interrupted
            syncResult.fullSyncRequested = true
        } catch (e: Exceptions.IgnorableHttpException) {
            // Ignore
        } catch (e: Exception) {
            if (e is Exceptions.UnauthorizedException) {
                syncResult.stats.numAuthExceptions++
            } else if (e is Exceptions.HttpException) {
                syncResult.stats.numParseExceptions++
            } else if (e is CalendarStorageException || e is ContactsStorageException) {
                syncResult.databaseError = true
            } else if (e is Exceptions.IntegrityException) {
                syncResult.stats.numParseExceptions++
            } else {
                syncResult.stats.numParseExceptions++
            }

            notificationManager.setThrowable(e)

            val detailsIntent = notificationManager.detailsIntent
            detailsIntent.putExtra(KEY_ACCOUNT, account)
            if (e !is Exceptions.UnauthorizedException) {
                detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
                detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase)
            }

            notificationManager.notify(syncErrorTitle, context.getString(syncPhase))
        } catch (e: OutOfMemoryError) {
            if (e is Exceptions.HttpException) {
                syncResult.stats.numParseExceptions++
            } else {
                syncResult.stats.numParseExceptions++
            }
            notificationManager.setThrowable(e)
            val detailsIntent = notificationManager.detailsIntent
            detailsIntent.putExtra(KEY_ACCOUNT, account)
            notificationManager.notify(syncErrorTitle, context.getString(syncPhase))
        }

    }

    private fun notifyUserOnSync() {
        val changeNotification = context.defaultSharedPreferences.getBoolean(App.CHANGE_NOTIFICATION, true)
        if (remoteEntries!!.isEmpty() || !changeNotification) {
            return
        }
        val notificationHelper = SyncNotification(context,
                System.currentTimeMillis().toString(), notificationId())

        var deleted = 0
        var added = 0
        var changed = 0
        for (entry in remoteEntries!!) {
            val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
            val action = cEntry.action
            when (action) {
                ADD -> added++
                SyncEntry.Actions.DELETE -> deleted++
                SyncEntry.Actions.CHANGE -> changed++
            }
        }

        val resources = context.resources
        val intent = ViewCollectionActivity.newIntent(context, account, info)
        notificationHelper.notify(syncSuccessfullyTitle,
                String.format(context.getString(R.string.sync_successfully_modified),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                remoteEntries!!.size, remoteEntries!!.size)),
                String.format(context.getString(R.string.sync_successfully_modified_full),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                added, added),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                changed, changed),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                deleted, deleted)),
                intent)
    }

    /**
     * Prepares synchronization (for instance, allocates necessary resources).
     *
     * @return whether actual synchronization is required / can be made. true = synchronization
     * shall be continued, false = synchronization can be skipped
     */
    @Throws(ContactsStorageException::class, CalendarStorageException::class)
    protected open fun prepare(): Boolean {
        return true
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, InvalidCalendarException::class)
    protected abstract fun processSyncEntry(cEntry: SyncEntry)

    private fun persistSyncEntry(uid: String?, syncEntry: SyncEntry) {
        val entry = EntryEntity()
        entry.uid = uid
        entry.content = syncEntry
        entry.journal = journalEntity
        data.insert(entry)
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, Exceptions.HttpException::class, InvalidCalendarException::class, InterruptedException::class)
    protected fun applyLocalEntries() {
        // FIXME: Need a better strategy
        // We re-apply local entries so our changes override whatever was written in the remote.
        val strTotal = localEntries!!.size.toString()
        var i = 0

        for (entry in localEntries!!) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            i++
            Logger.log.info("Processing (" + i.toString() + "/" + strTotal + ") " + entry.toString())

            val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
            if (cEntry.isAction(SyncEntry.Actions.DELETE)) {
                continue
            }
            Logger.log.info("Processing resource for journal entry")
            processSyncEntry(cEntry)
        }
    }

    @Throws(IOException::class, CalendarStorageException::class, ContactsStorageException::class)
    protected fun prepareFetch() {
        remoteCTag = journalEntity.getLastUid(data)
    }

    @Throws(Exceptions.HttpException::class, ContactsStorageException::class, CalendarStorageException::class, Exceptions.IntegrityException::class)
    protected fun fetchEntries() {
        val count = data.count(EntryEntity::class.java).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value()
        if (remoteCTag != null && count == 0) {
            // If we are updating an existing installation with no saved journal, we need to add
            remoteEntries = journal!!.list(crypto, null, MAX_FETCH)
            var i = 0
            for (entry in remoteEntries!!) {
                val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
                persistSyncEntry(entry.uid, cEntry)
                i++
                if (remoteCTag == entry.uid) {
                    remoteEntries = remoteEntries?.drop(i)
                    break
                }
            }
        } else {
            remoteEntries = journal!!.list(crypto, remoteCTag, MAX_FETCH)
        }

        Logger.log.info("Fetched " + remoteEntries!!.size.toString() + " entries")
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, InvalidCalendarException::class, InterruptedException::class)
    protected fun applyRemoteEntries() {
        // Process new vcards from server
        val strTotal = remoteEntries!!.size.toString()
        var i = 0

        for (entry in remoteEntries!!) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            i++
            Logger.log.info("Processing (" + i.toString() + "/" + strTotal + ") " + entry.toString())

            val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
            Logger.log.info("Processing resource for journal entry")
            processSyncEntry(cEntry)

            persistSyncEntry(entry.uid, cEntry)

            remoteCTag = entry.uid
        }
    }

    @Throws(Exceptions.HttpException::class, IOException::class, ContactsStorageException::class, CalendarStorageException::class)
    protected fun pushEntries() {
        // upload dirty contacts
        var pushed = 0
        // FIXME: Deal with failure (someone else uploaded before we go here)
        try {
            if (!localEntries!!.isEmpty()) {
                val entries = localEntries
                journal!!.create(entries!!, remoteCTag)
                // Persist the entries after they've been pushed
                for (entry in entries) {
                    val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
                    persistSyncEntry(entry.uid, cEntry)
                }
                remoteCTag = entries[entries.size - 1].uid
                pushed += entries.size
            }
        } finally {
            // FIXME: A bit fragile, we assume the order in createLocalEntries
            var left = pushed
            for (local in localDeleted!!) {
                if (pushed-- <= 0) {
                    break
                }
                local.delete()
            }
            if (left > 0) {
                localDeleted = localDeleted?.drop(left)
            }

            left = pushed
            for (local in localDirty) {
                if (pushed-- <= 0) {
                    break
                }
                Logger.log.info("Added/changed resource with UUID: " + local.uuid)
                local.clearDirty(local.uuid!!)
            }
            if (left > 0) {
                localDirty = localDirty.drop(left)
            }

            if (pushed > 0) {
                Logger.log.severe("Unprocessed localentries left, this should never happen!")
            }
        }
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class, IOException::class)
    protected open fun createLocalEntries() {
        localEntries = LinkedList()

        // Not saving, just creating a fake one until we load it from a local db
        var previousEntry: JournalEntryManager.Entry? = if (remoteCTag != null) JournalEntryManager.Entry.getFakeWithUid(remoteCTag!!) else null

        for (local in localDeleted!!) {
            val entry = SyncEntry(local.content, SyncEntry.Actions.DELETE)
            val tmp = JournalEntryManager.Entry()
            tmp.update(crypto, entry.toJson(), previousEntry)
            previousEntry = tmp
            localEntries!!.add(previousEntry)

            if (localEntries!!.size == MAX_PUSH) {
                return
            }
        }

        for (local in localDirty) {
            val action: SyncEntry.Actions
            if (local.isLocalOnly) {
                action = ADD
            } else {
                action = SyncEntry.Actions.CHANGE
            }

            val entry = SyncEntry(local.content, action)
            val tmp = JournalEntryManager.Entry()
            tmp.update(crypto, entry.toJson(), previousEntry)
            previousEntry = tmp
            localEntries!!.add(previousEntry)

            if (localEntries!!.size == MAX_PUSH) {
                return
            }
        }
    }

    /**
     */
    @Throws(CalendarStorageException::class, ContactsStorageException::class, FileNotFoundException::class)
    protected fun prepareLocal() {
        localDeleted = processLocallyDeleted()
        localDirty = localCollection!!.findDirty(MAX_PUSH)
        // This is done after fetching the local dirty so all the ones we are using will be prepared
        prepareDirty()
    }


    /**
     * Delete unpublished locally deleted, and return the rest.
     * Checks Thread.interrupted() before each request to allow quick sync cancellation.
     */
    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    private fun processLocallyDeleted(): List<T> {
        val localList = localCollection!!.findDeleted()
        val ret = ArrayList<T>(localList.size)

        if (journalEntity.isReadOnly) {
            for (local in localList) {
                Logger.log.info("Restoring locally deleted resource on a read only collection: ${local.uuid}")
                local.resetDeleted()
                numDiscarded++
            }
        } else {
            for (local in localList) {
                if (Thread.interrupted())
                    return ret

                if (local.uuid != null) {
                    Logger.log.info(local.uuid + " has been deleted locally -> deleting from server")
                } else {
                    Logger.log.fine("Entry deleted before ever syncing - genarting a UUID")
                    local.prepareForUpload()
                }

                ret.add(local)

                syncResult.stats.numDeletes++
            }
        }

        return ret
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    protected open fun prepareDirty() {
        if (journalEntity.isReadOnly) {
            for (local in localDirty) {
                Logger.log.info("Restoring locally modified resource on a read only collection: ${local.uuid}")
                if (local.uuid == null) {
                    // If it was only local, delete.
                    local.delete()
                } else {
                    local.clearDirty(local.uuid!!)
                }
                numDiscarded++
            }

            localDirty = LinkedList()
        } else {
            // assign file names and UIDs to new entries
            Logger.log.info("Looking for local entries without a uuid")
            for (local in localDirty) {
                if (local.uuid != null) {
                    continue
                }

                Logger.log.fine("Found local record without file name; generating file name/UID if necessary")
                local.prepareForUpload()
            }
        }
    }

    /**
     * For post-processing of entries, for instance assigning groups.
     */
    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    protected open fun postProcess() {
    }

    private fun notifyDiscardedChange() {
        val notification = SyncNotification(context, "discarded_${info.uid}", notificationId())
        val intent = Intent(context, AccountsActivity::class.java)
        notification.notify(context.getString(R.string.sync_journal_readonly, info.displayName), context.getString(R.string.sync_journal_readonly_message, numDiscarded), null, intent, R.drawable.ic_error_light)
    }

    companion object {
        private val MAX_FETCH = 50
        private val MAX_PUSH = 30
    }
}
