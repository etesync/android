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
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.resource.LocalTaskList
import com.etesync.syncadapter.ui.DebugInfoActivity
import okhttp3.HttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.*
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this)


	class TasksSyncAdapter(
            context: Context
    ): SyncAdapter(context) {
        override val syncErrorTitle = R.string.sync_error_tasks
        override val notificationManager = SyncNotification(context, "journals-tasks", Constants.NOTIFICATION_TASK_SYNC)

        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {

            val taskProvider = TaskProvider.fromProviderClient(context, provider)

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

            updateLocalTaskLists(taskProvider, account, accountSettings)

            val principal = HttpUrl.get(accountSettings.uri!!)!!

            for (taskList in AndroidTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)) {
                Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                TasksSyncManager(context, account, accountSettings, extras, authority, syncResult, taskList, principal).use {
                    it.performSync()
                }
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            val data = (context.applicationContext as App).data
            var service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.TASKS)

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
