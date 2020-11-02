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
import com.etebase.client.*
import com.etebase.client.exceptions.ConnectionException
import com.etebase.client.exceptions.HttpException
import com.etebase.client.exceptions.TemporaryServerErrorException
import com.etebase.client.exceptions.UnauthorizedException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.journalmanager.Crypto
import com.etesync.journalmanager.Exceptions
import com.etesync.journalmanager.JournalEntryManager
import com.etesync.journalmanager.model.SyncEntry
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.*
import com.etesync.journalmanager.model.SyncEntry.Actions.ADD
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.*
import com.etesync.syncadapter.ui.AccountsActivity
import com.etesync.syncadapter.ui.DebugInfoActivity
import com.etesync.syncadapter.ui.ViewCollectionActivity
import com.etesync.syncadapter.ui.etebase.CollectionActivity
import org.jetbrains.anko.defaultSharedPreferences
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException
import kotlin.concurrent.withLock

abstract class SyncManager<T: LocalResource<*>> @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
constructor(protected val context: Context, protected val account: Account, protected val settings: AccountSettings, protected val extras: Bundle, protected val authority: String, protected val syncResult: SyncResult, journalUid: String, protected val serviceType: CollectionInfo.Type, accountName: String): Closeable {
    // FIXME: remove all of the lateinit once we remove legacy (and make immutable)
    // RemoteEntries and the likes are probably also just relevant for legacy
    protected val isLegacy: Boolean = settings.isLegacy

    protected val notificationManager: SyncNotification
    protected lateinit var info: CollectionInfo
    protected var localCollection: LocalCollection<T>? = null

    protected var httpClient: HttpClient

    protected lateinit var etebaseLocalCache: EtebaseLocalCache
    protected lateinit var etebase: com.etebase.client.Account
    protected lateinit var colMgr: CollectionManager
    protected lateinit var itemMgr: ItemManager
    protected lateinit var cachedCollection: CachedCollection

    // Sync counters
    private var syncItemsTotal = 0
    private var syncItemsDeleted = 0
    private var syncItemsChanged = 0

    protected var journal: JournalEntryManager? = null
    private var _journalEntity: JournalEntity? = null

    private var numDiscarded = 0

    private lateinit var crypto: Crypto.CryptoManager

    private lateinit var data: MyEntityDataStore

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

        if (isLegacy) {
            data = (context.applicationContext as App).data
            val serviceEntity = JournalModel.Service.fetchOrCreate(data, accountName, serviceType)
            info = JournalEntity.fetch(data, serviceEntity, journalUid)!!.info

            Logger.log.info(String.format(Locale.getDefault(), "Syncing collection %s (version: %d)", journalUid, info.version))

            if (journalEntity.encryptedKey != null) {
                crypto = Crypto.CryptoManager(info.version, settings.keyPair!!, journalEntity.encryptedKey)
            } else {
                crypto = Crypto.CryptoManager(info.version, settings.password(), info.uid!!)
            }
        } else {
            etebaseLocalCache = EtebaseLocalCache.getInstance(context, accountName)
            etebase = EtebaseLocalCache.getEtebase(context, httpClient.okHttpClient, settings)
            colMgr = etebase.collectionManager
            synchronized(etebaseLocalCache) {
                cachedCollection = etebaseLocalCache.collectionGet(colMgr, journalUid)!!
            }
            itemMgr = colMgr.getItemManager(cachedCollection.col)
        }

        // dismiss previous error notifications
        notificationManager = SyncNotification(context, journalUid, notificationId())
        notificationManager.cancel()
    }

    protected abstract fun notificationId(): Int

    override fun close() {
        httpClient.close()
    }

    @TargetApi(21)
    fun performSync() {
        syncItemsTotal = 0
        syncItemsDeleted = 0
        syncItemsChanged = 0

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

            if (isLegacy) {
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
            } else {
                var itemList: ItemListResponse?
                var stoken = synchronized(etebaseLocalCache) {
                    etebaseLocalCache.collectionLoadStoken(cachedCollection.col.uid)
                }
                // Push local changes
                var chunkPushItems: List<Item>
                do {
                    if (Thread.interrupted())
                        throw InterruptedException()
                    syncPhase = R.string.sync_phase_prepare_local
                    Logger.log.info("Sync phase: " + context.getString(syncPhase))
                    prepareLocal()

                    /* Create push items out of local changes. */
                    if (Thread.interrupted())
                        throw InterruptedException()
                    syncPhase = R.string.sync_phase_create_local_entries
                    Logger.log.info("Sync phase: " + context.getString(syncPhase))
                    chunkPushItems = createPushItems()

                    if (Thread.interrupted())
                        throw InterruptedException()
                    syncPhase = R.string.sync_phase_push_entries
                    Logger.log.info("Sync phase: " + context.getString(syncPhase))
                    pushItems(chunkPushItems)
                } while (chunkPushItems.size == MAX_PUSH)

                do {
                    if (Thread.interrupted())
                        throw InterruptedException()
                    syncPhase = R.string.sync_phase_fetch_entries
                    Logger.log.info("Sync phase: " + context.getString(syncPhase))
                    itemList = fetchItems(stoken)
                    if (itemList == null) {
                        break
                    }

                    if (Thread.interrupted())
                        throw InterruptedException()
                    syncPhase = R.string.sync_phase_apply_remote_entries
                    Logger.log.info("Sync phase: " + context.getString(syncPhase))
                    applyRemoteItems(itemList)

                    stoken = itemList.stoken
                    if (stoken != null) {
                        synchronized(etebaseLocalCache) {
                            etebaseLocalCache.collectionSaveStoken(cachedCollection.col.uid, stoken)
                        }
                    }
                } while (!itemList!!.isDone)
            }

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
        } catch (e: FileNotFoundException) {
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
        } catch (e: TemporaryServerErrorException) {
            syncResult.stats.numIoExceptions++
            syncResult.delayUntil = Constants.DEFAULT_RETRY_DELAY
        } catch (e: ConnectionException) {
            syncResult.stats.numIoExceptions++
            syncResult.delayUntil = Constants.DEFAULT_RETRY_DELAY
        } catch (e: InterruptedException) {
            // Restart sync if interrupted
            syncResult.fullSyncRequested = true
        } catch (e: Exceptions.IgnorableHttpException) {
            // Ignore
        } catch (e: Exception) {
            if (e is Exceptions.UnauthorizedException || e is UnauthorizedException) {
                syncResult.stats.numAuthExceptions++
            } else if (e is Exceptions.HttpException || e is HttpException) {
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

        if (!changeNotification || (syncItemsTotal == 0)) {
            return
        }

        val notificationHelper = SyncNotification(context,
                System.currentTimeMillis().toString(), notificationId())
        val resources = context.resources
        val intent = if (isLegacy) {
            ViewCollectionActivity.newIntent(context, account, info)
        } else {
            CollectionActivity.newIntent(context, account, cachedCollection.col.uid)
        }
        notificationHelper.notify(syncSuccessfullyTitle,
                String.format(context.getString(R.string.sync_successfully_modified),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                syncItemsTotal, syncItemsTotal)),
                String.format(context.getString(R.string.sync_successfully_modified_full),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                syncItemsChanged, syncItemsChanged),
                        resources.getQuantityString(R.plurals.sync_successfully,
                                syncItemsDeleted, syncItemsDeleted)),
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

    protected abstract fun processItem(item: Item)

    private fun persistItem(item: Item) {
        synchronized(etebaseLocalCache) {
            // FIXME: it's terrible that we are fetching and decrypting the item here - we really don't have to
            val cached = etebaseLocalCache.itemGet(itemMgr, cachedCollection.col.uid, item.uid)
            if (cached?.item?.etag != item.etag) {
                syncItemsTotal++

                if (item.isDeleted) {
                    syncItemsDeleted++
                } else {
                    syncItemsChanged++
                }
                etebaseLocalCache.itemSet(itemMgr, cachedCollection.col.uid, item)
            }
        }
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, InvalidCalendarException::class)
    protected abstract fun processSyncEntryImpl(cEntry: SyncEntry)

    protected fun processSyncEntry(cEntry: SyncEntry) {
        try {
            processSyncEntryImpl(cEntry)
        } catch (e: Exception) {
            Logger.log.warning("Failed processing entry: ${cEntry.content}")
            throw e
        }
    }

    private fun persistSyncEntry(uid: String?, syncEntry: SyncEntry, error: String?) {
        val entry = EntryEntity()
        entry.uid = uid
        entry.content = syncEntry
        entry.journal = journalEntity
        try {
            data.insert(entry)
            val entryError = EntryErrorEntity()
            entryError.entry = entry
            entryError.error = error
            data.insert(entryError)
        } catch (e: io.requery.sql.StatementExecutionException) {
            if (e.cause is java.sql.SQLIntegrityConstraintViolationException) {
                Logger.log.warning("Tried inserting an existing entry ${uid}")
            } else {
                throw e
            }
        }

        when (syncEntry.action) {
            ADD -> syncItemsChanged++
            SyncEntry.Actions.DELETE -> syncItemsDeleted++
            SyncEntry.Actions.CHANGE -> syncItemsChanged++
        }
    }

    @Throws(IOException::class, CalendarStorageException::class, ContactsStorageException::class)
    protected fun prepareFetch() {
        if (isLegacy) {
            remoteCTag = journalEntity.getLastUid(data)
        } else {
            remoteCTag = cachedCollection.col.stoken
        }
    }

    private fun fetchItems(stoken: String?): ItemListResponse? {
        if (remoteCTag != stoken) {
            val ret = itemMgr.list(FetchOptions().stoken(stoken))
            Logger.log.info("Fetched items. Done=${ret.isDone}")
            return ret
        } else {
            Logger.log.info("Skipping fetch because local stoken == lastStoken (${remoteCTag})")
            return null
        }
    }

    private fun applyRemoteItems(itemList: ItemListResponse) {
        val items = itemList.data
        // Process new vcards from server
        val size = items.size
        var i = 0

        for (item in items) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            i++
            Logger.log.info("Processing (${i}/${size}) UID=${item.uid} Etag=${item.etag}")

            processItem(item)
            persistItem(item)
        }
    }

    @Throws(Exceptions.HttpException::class, ContactsStorageException::class, CalendarStorageException::class, Exceptions.IntegrityException::class)
    private fun fetchEntries() {
        val count = data.count(EntryEntity::class.java).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value()
        if (remoteCTag != null && count == 0) {
            // If we are updating an existing installation with no saved journal, we need to add
            remoteEntries = journal!!.list(crypto, null, MAX_FETCH)
            var i = 0
            for (entry in remoteEntries!!) {
                val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
                persistSyncEntry(entry.uid, cEntry, null)
                i++
                if (remoteCTag == entry.uid) {
                    remoteEntries = remoteEntries?.drop(i)
                    break
                }
            }
        } else {
            if ((remoteCTag != null) && (journalEntity.remoteLastUid == remoteCTag)) {
                Logger.log.info("Skipping fetch because local lastUid == remoteLastUid (${remoteCTag})")
                remoteEntries = LinkedList()
                return
            }

            remoteEntries = journal!!.list(crypto, remoteCTag, MAX_FETCH)
        }

        Logger.log.info("Fetched " + remoteEntries!!.size.toString() + " entries")
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, InvalidCalendarException::class, InterruptedException::class)
    private fun applyRemoteEntries() {
        // Process new vcards from server
        val strTotal = remoteEntries!!.size.toString()
        var i = 0

        syncItemsTotal += remoteEntries!!.size

        for (entry in remoteEntries!!) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            i++
            Logger.log.info("Processing (" + i.toString() + "/" + strTotal + ") " + entry.toString())

            val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
            Logger.log.info("Processing resource for journal entry")

            var error: String? = null
            try {
                processSyncEntry(cEntry)
            } catch (e: Exception) {
                error = e.toString()
            }

            persistSyncEntry(entry.uid, cEntry, error)

            remoteCTag = entry.uid
        }
    }

    @Throws(Exceptions.HttpException::class, IOException::class, ContactsStorageException::class, CalendarStorageException::class)
    private fun pushEntries() {
        // upload dirty contacts
        var pushed = 0
        // FIXME: Deal with failure (someone else uploaded before we go here)
        try {
            if (!localEntries!!.isEmpty()) {
                val entries = localEntries!!
                pushLock.withLock {
                    journal!!.create(entries, remoteCTag)
                }

                // Persist the entries after they've been pushed
                for (entry in entries) {
                    val cEntry = SyncEntry.fromJournalEntry(crypto, entry)
                    persistSyncEntry(entry.uid, cEntry, null)
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
                local.clearDirty(local.uuid)
            }
            if (left > 0) {
                localDirty = localDirty.drop(left)
            }

            if (pushed > 0) {
                Logger.log.severe("Unprocessed localentries left, this should never happen!")
            }
        }
    }

    private fun pushItems(chunkPushItems_: List<Item>) {
        var chunkPushItems = chunkPushItems_
        // upload dirty contacts
        var pushed = 0
        try {
            if (!chunkPushItems.isEmpty()) {
                val items = chunkPushItems
                itemMgr.batch(items.toTypedArray())

                // Persist the items
                synchronized(etebaseLocalCache) {
                    val colUid = cachedCollection.col.uid

                    for (item in items) {
                        etebaseLocalCache.itemSet(itemMgr, colUid, item)
                    }
                }

                pushed += items.size
            }
        } finally {
            // FIXME: A bit fragile, we assume the order in createPushItems
            var left = pushed
            for (local in localDeleted!!) {
                if (pushed-- <= 0) {
                    break
                }
                local.delete()
            }
            if (left > 0) {
                localDeleted = localDeleted?.drop(left)
                chunkPushItems = chunkPushItems.drop(left - pushed)
            }

            left = pushed
            var i = 0
            for (local in localDirty) {
                if (pushed-- <= 0) {
                    break
                }
                Logger.log.info("Added/changed resource with filename: " + local.fileName)
                local.clearDirty(chunkPushItems[i].etag)
                i++
            }
            if (left > 0) {
                localDirty = localDirty.drop(left)
                chunkPushItems.drop(left)
            }

            if (pushed > 0) {
                Logger.log.severe("Unprocessed localentries left, this should never happen!")
            }
        }
    }

    private fun itemUpdateMtime(item: Item) {
        val meta = item.meta
        meta.setMtime(System.currentTimeMillis())
        item.meta = meta
    }

    private fun prepareLocalItemForUpload(colUid: String, local: T): Item {
        val cacheItem = if (local.fileName != null) etebaseLocalCache.itemGet(itemMgr, colUid, local.fileName!!) else null
        val item: Item
        if (cacheItem != null) {
            item = cacheItem.item
            itemUpdateMtime(item)
        } else {
            val uid = UUID.randomUUID().toString()
            val meta = ItemMetadata()
            meta.name = uid
            meta.mtime = System.currentTimeMillis()
            item = itemMgr.create(meta, "")

            local.prepareForUpload(item.uid, uid)
        }

        try {
            item.setContent(local.content)
        } catch (e: Exception) {
            Logger.log.warning("Failed creating local entry ${local.uuid}")
            if (local is LocalContact) {
                Logger.log.warning("Contact with title ${local.contact?.displayName}")
            } else if (local is LocalEvent) {
                Logger.log.warning("Event with title ${local.event?.summary}")
            } else if (local is LocalTask) {
                Logger.log.warning("Task with title ${local.task?.summary}")
            }
            throw e
        }

        return item
    }

    private fun createPushItems(): List<Item> {
        val ret = LinkedList<Item>()
        val colUid = cachedCollection.col.uid

        synchronized(etebaseLocalCache) {
            for (local in localDeleted!!) {
                val item = prepareLocalItemForUpload(colUid, local)
                item.delete()

                ret.add(item)

                if (ret.size == MAX_PUSH) {
                    return ret
                }
            }
        }

        synchronized(etebaseLocalCache) {
            for (local in localDirty) {
                val item = prepareLocalItemForUpload(colUid, local)

                ret.add(item)

                if (ret.size == MAX_PUSH) {
                    return ret
                }
            }
        }

        return ret
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class, IOException::class)
    private fun createLocalEntries() {
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

            try {
                val entry = SyncEntry(local.content, action)
                val tmp = JournalEntryManager.Entry()
                tmp.update(crypto, entry.toJson(), previousEntry)
                previousEntry = tmp
                localEntries!!.add(previousEntry)

                if (localEntries!!.size == MAX_PUSH) {
                    return
                }
            } catch (e: Exception) {
                Logger.log.warning("Failed creating local entry ${local.uuid}")
                if (local is LocalContact) {
                    Logger.log.warning("Contact with title ${local.contact?.displayName}")
                } else if (local is LocalEvent) {
                    Logger.log.warning("Event with title ${local.event?.summary}")
                } else if (local is LocalTask) {
                    Logger.log.warning("Task with title ${local.task?.summary}")
                }
                throw e
            }
        }
    }

    /**
     */
    @Throws(CalendarStorageException::class, ContactsStorageException::class, FileNotFoundException::class)
    protected open fun prepareLocal() {
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

        val readOnly = (isLegacy && journalEntity.isReadOnly) || (!isLegacy && (cachedCollection.col.accessLevel == CollectionAccessLevel.ReadOnly))
        if (readOnly) {
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
                    if (isLegacy) {
                        // It's done later for non-legacy
                        Logger.log.fine("Entry deleted before ever syncing - genarting a UUID")
                        local.legacyPrepareForUpload(null)
                    }
                }

                ret.add(local)

                syncResult.stats.numDeletes++
            }
        }

        return ret
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    protected open fun prepareDirty() {
        val readOnly = (isLegacy && journalEntity.isReadOnly) || (!isLegacy && (cachedCollection.col.accessLevel == CollectionAccessLevel.ReadOnly))
        if (readOnly) {
            for (local in localDirty) {
                Logger.log.info("Restoring locally modified resource on a read only collection: ${local.uuid}")
                if (local.uuid == null) {
                    // If it was only local, delete.
                    local.delete()
                } else {
                    local.clearDirty(null)
                }
                numDiscarded++
            }

            localDirty = LinkedList()
        } else if (isLegacy) {
            // It's done later for non-legacy
            // assign file names and UIDs to new entries
            Logger.log.info("Looking for local entries without a uuid")
            for (local in localDirty) {
                if (local.uuid != null) {
                    continue
                }

                Logger.log.fine("Found local record without file name; generating file name/UID if necessary")
                local.legacyPrepareForUpload(null)
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

        private val pushLock = ReentrantLock()
    }
}
