/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.accounts.Account
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.AndroidTaskListFactory
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.model.JournalEntity
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks

class LocalTaskList private constructor(
        account: Account,
        provider: TaskProvider,
        id: Long
): AndroidTaskList<LocalTask>(account, provider, LocalTask.Factory, id), LocalCollection<LocalTask> {
    companion object {
        val defaultColor = -0x743cb6     // light green 500

        fun tasksProviderAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return context.packageManager.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
            else {
                val provider = TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)
                provider?.use { return true }
                return false
            }
        }

        fun create(account: Account, provider: TaskProvider, journalEntity: JournalEntity): Uri {
            val values = valuesFromCollectionInfo(journalEntity, true)
            values.put(TaskLists.OWNER, account.name)
            values.put(TaskLists.SYNC_ENABLED, 1)
            values.put(TaskLists.VISIBLE, 1)
            return create(account, provider, values)
        }

        fun findByName(account: Account, provider: TaskProvider, factory: Factory, name: String): LocalTaskList?
                = AndroidTaskList.find(account, provider, factory, TaskLists._SYNC_ID + "==?", arrayOf(name)).firstOrNull()


        private fun valuesFromCollectionInfo(journalEntity: JournalEntity, withColor: Boolean): ContentValues {
            val info = journalEntity.info
            val values = ContentValues(3)
            values.put(TaskLists._SYNC_ID, info.uid)
            values.put(TaskLists.LIST_NAME, if (info.displayName.isNullOrBlank()) info.uid else info.displayName)

            if (withColor)
                values.put(TaskLists.LIST_COLOR, info.color ?: defaultColor)

            return values
        }

    }

    override val url: String?
        get() = syncId

    fun update(journalEntity: JournalEntity, updateColor: Boolean) =
            update(valuesFromCollectionInfo(journalEntity, updateColor))

    override fun findDeleted() = queryTasks("${Tasks._DELETED}!=0", null)

    override fun findDirty(limit: Int?): List<LocalTask> {
        val sortOrder = if (limit != null) "${Tasks._ID} ASC LIMIT $limit" else null
        val tasks = queryTasks("${Tasks._DIRTY}!=0 AND ${Tasks._DELETED}==0", null, sortOrder)
        for (localTask in tasks) {
            val task = requireNotNull(localTask.task)
            val sequence = task.sequence
            if (sequence == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.sequence = 0
            else
                task.sequence = sequence + 1
        }
        return tasks
    }

    override fun findAll(): List<LocalTask>
            = queryTasks(null, null)

    override fun findWithoutFileName(): List<LocalTask>
        = queryTasks(Tasks._SYNC_ID + " IS NULL", null)

    override fun findByUid(uid: String): LocalTask?
        = queryTasks(Tasks._SYNC_ID + " =? ", arrayOf(uid)).firstOrNull()

    override fun count(): Long {
        try {
            val cursor = provider.client.query(
                    TaskProvider.syncAdapterUri(provider.tasksUri(), account), null,
                    Tasks.LIST_ID + "=?", arrayOf(id.toString()), null)
            try {
                return cursor?.count?.toLong()!!
            } finally {
                cursor?.close()
            }
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't query calendar events", e)
        }

    }

    object Factory: AndroidTaskListFactory<LocalTaskList> {

        override fun newInstance(account: Account, provider: TaskProvider, id: Long) =
                LocalTaskList(account, provider, id)

    }
}
