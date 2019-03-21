/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.ical4android.Task
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.JournalEntryManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.SyncEntry
import com.etesync.syncadapter.resource.LocalTask
import com.etesync.syncadapter.resource.LocalTaskList
import okhttp3.HttpUrl
import java.io.StringReader

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager(
        context: Context,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        taskList: LocalTaskList,
        private val remote: HttpUrl
): SyncManager<LocalTask>(context, account, accountSettings, extras, authority, syncResult, taskList.url!!, CollectionInfo.Type.TASKS, account.name) {

    override val syncErrorTitle: String
        get() = context.getString(R.string.sync_error_tasks, account.name)

    override val syncSuccessfullyTitle: String
        get() = context.getString(R.string.sync_successfully_tasks, info.displayName,
                account.name)

    init {
        localCollection = taskList
    }

    override fun notificationId(): Int {
        return Constants.NOTIFICATION_TASK_SYNC
    }

    override fun prepare(): Boolean {
        if (!super.prepare())
            return false

        journal = JournalEntryManager(httpClient.okHttpClient, remote, localTaskList().url!!)
        return true
    }

    // helpers

    private fun localTaskList(): LocalTaskList {
        return localCollection as LocalTaskList
    }

    override fun processSyncEntry(cEntry: SyncEntry) {
        val inputReader = StringReader(cEntry.content)

        val tasks = Task.fromReader(inputReader)
        if (tasks.size == 0) {
            Logger.log.warning("Received VCard without data, ignoring")
            return
        } else if (tasks.size > 1) {
            Logger.log.warning("Received multiple VCALs, using first one")
        }

        val event = tasks[0]
        val local = localCollection!!.findByUid(event.uid!!)

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processTask(event, local)
        } else {
            if (local != null) {
                Logger.log.info("Removing local record #" + local.id + " which has been deleted on the server")
                local.delete()
            } else {
                Logger.log.warning("Tried deleting a non-existent record: " + event.uid)
            }
        }
    }

    private fun processTask(newData: Task, _localTask: LocalTask?): LocalTask {
        var localTask = _localTask
        // delete local Task, if it exists
        if (localTask != null) {
            Logger.log.info("Updating " + newData.uid + " in local calendar")
            localTask.eTag = newData.uid
            localTask.update(newData)
            syncResult.stats.numUpdates++
        } else {
            Logger.log.info("Adding " + newData.uid + " to local calendar")
            localTask = LocalTask(localTaskList(), newData, newData.uid, newData.uid)
            localTask.add()
            syncResult.stats.numInserts++
        }

        return localTask
    }
}