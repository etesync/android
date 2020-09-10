/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.etesync.syncadapter.*
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.resource.LocalTaskList
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.dmfs.tasks.contract.TaskContract
import java.util.*

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this, ProviderName.OpenTasks)

	class TasksSyncAdapter(
            context: Context,
            private val name: ProviderName
    ): SyncAdapter(context) {
        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {

            val taskProvider = TaskProvider.fromProviderClient(context, provider, name)

            // make sure account can be seen by OpenTasks
            if (Build.VERSION.SDK_INT >= 26)
                AccountManager.get(context).setAccountVisibility(account, taskProvider.name.packageName, AccountManager.VISIBILITY_VISIBLE)

            val accountSettings = AccountSettings(context, account)
            /* don't run sync if
               - sync conditions (e.g. "sync only in WiFi") are not met AND
               - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
             */
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                return

            RefreshCollections(account, CollectionInfo.Type.TASKS).run()

            if (accountSettings.isLegacy) {
                legacyUpdateLocalTaskLists(taskProvider, account, accountSettings)
            } else {
                updateLocalTaskLists(taskProvider, account, accountSettings)
            }
            val principal = accountSettings.uri?.toHttpUrlOrNull()!!

            for (taskList in AndroidTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)) {
                Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                TasksSyncManager(context, account, accountSettings, extras, authority, syncResult, taskList, principal).use {
                    it.performSync()
                }
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            val remote = HashMap<String, CachedCollection>()
            val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
            val collections: List<CachedCollection>
            synchronized(etebaseLocalCache) {
                val httpClient = HttpClient.Builder(context, settings).setForeground(false).build()
                val etebase = EtebaseLocalCache.getEtebase(context, httpClient.okHttpClient, settings)
                val colMgr = etebase.collectionManager

                collections = etebaseLocalCache.collectionList(colMgr).filter { it.meta.collectionType == Constants.ETEBASE_TYPE_TASKS }
            }

            for (collection in collections) {
                remote[collection.col.uid] = collection
            }

            val local = AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null)

            val updateColors = settings.manageCalendarColors

            // delete obsolete local calendar
            for (taskList in local) {
                val url = taskList.syncId
                val collection = remote[url]
                if (collection == null) {
                    Logger.log.fine("Deleting obsolete local taskList $url")
                    taskList.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local taskList $url")
                    taskList.update(collection, updateColors)
                    // we already have a local taskList for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local calendars
            for (url in remote.keys) {
                val cachedCollection = remote[url]!!
                Logger.log.info("Adding local calendar list $cachedCollection")
                LocalTaskList.create(account, provider, cachedCollection)
            }
        }

        private fun legacyUpdateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            val data = (context.applicationContext as App).data
            var service = JournalModel.Service.fetchOrCreate(data, account.name, CollectionInfo.Type.TASKS)

            val remote = HashMap<String, JournalEntity>()
            val remoteJournals = JournalEntity.getJournals(data, service)
            for (journalEntity in remoteJournals) {
                remote[journalEntity.uid] = journalEntity
            }

            val local = AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null)

            val updateColors = settings.manageCalendarColors

            // delete obsolete local TaskList
            for (taskList in local) {
                val url = taskList.url
                val journalEntity = remote[url]
                if (journalEntity == null) {
                    Logger.log.fine("Deleting obsolete local task list $url")
                    taskList.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local task list $url with $journalEntity")
                    taskList.update(journalEntity, updateColors)
                    // we already have a local tasks for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local taskss
            for (url in remote.keys) {
                val journalEntity = remote[url]!!
                Logger.log.info("Adding local task list $journalEntity")
                LocalTaskList.create(account, provider, journalEntity)
            }
        }

    }

}
