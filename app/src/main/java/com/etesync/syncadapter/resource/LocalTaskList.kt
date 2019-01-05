/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.RemoteException

import com.etesync.syncadapter.model.CollectionInfo

import org.dmfs.provider.tasks.TaskContract.TaskLists
import org.dmfs.provider.tasks.TaskContract.Tasks

import java.io.FileNotFoundException

import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.AndroidTaskListFactory
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider

class LocalTaskList protected constructor(account: Account, provider: TaskProvider, id: Long) : AndroidTaskList(account, provider, LocalTask.Factory.INSTANCE, id), LocalCollection<LocalTask> {

    override val deleted: Array<LocalTask>
        @Throws(CalendarStorageException::class)
        get() = queryTasks(Tasks._DELETED + "!=0", null) as Array<LocalTask>

    override val withoutFileName: Array<LocalTask>
        @Throws(CalendarStorageException::class)
        get() = queryTasks(Tasks._SYNC_ID + " IS NULL", null) as Array<LocalTask>

    override// sequence has not been assigned yet (i.e. this task was just locally created)
    val dirty: Array<LocalTask>
        @Throws(CalendarStorageException::class, FileNotFoundException::class)
        get() {
            val tasks = queryTasks(Tasks._DIRTY + "!=0 AND " + Tasks._DELETED + "== 0", null) as Array<LocalTask>
            for (task in tasks) {
                if (task.task.sequence == null)
                    task.task.sequence = 0
                else
                    task.task.sequence++
            }
            return tasks
        }


    override fun taskBaseInfoColumns(): Array<String> {
        return BASE_INFO_COLUMNS
    }

    @Throws(CalendarStorageException::class)
    fun update(info: CollectionInfo, updateColor: Boolean) {
        update(valuesFromCollectionInfo(info, updateColor))
    }

    @Throws(CalendarStorageException::class)
    override fun getByUid(uid: String): LocalTask? {
        val ret = queryTasks(Tasks._SYNC_ID + " =? ", arrayOf(uid)) as Array<LocalTask>
        return if (ret != null && ret.size > 0) {
            ret[0]
        } else null
    }

    @Throws(CalendarStorageException::class)
    override fun count(): Long {
        val where = Tasks.LIST_ID + "=?"
        val whereArgs = arrayOf(id.toString())

        try {
            val cursor = provider.client.query(
                    syncAdapterURI(provider.tasksUri()), null,
                    where, whereArgs, null)
            try {
                return cursor.count.toLong()
            } finally {
                cursor.close()
            }
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't query calendar events", e)
        }

    }


    class Factory : AndroidTaskListFactory {

        override fun newInstance(account: Account, provider: TaskProvider, id: Long): AndroidTaskList {
            return LocalTaskList(account, provider, id)
        }

        override fun newArray(size: Int): Array<AndroidTaskList?> {
            return arrayOfNulls<LocalTaskList>(size) as Array<AndroidTaskList?>
        }

        companion object {
            val INSTANCE = Factory()
        }
    }

    companion object {

        val defaultColor = -0x3c1592     // "DAVdroid green"

        val COLUMN_CTAG = TaskLists.SYNC_VERSION

        internal var BASE_INFO_COLUMNS = arrayOf(Tasks._ID, Tasks._SYNC_ID, LocalTask.COLUMN_ETAG)

        @Throws(CalendarStorageException::class)
        fun create(account: Account, provider: TaskProvider, info: CollectionInfo): Uri {
            val values = valuesFromCollectionInfo(info, true)
            values.put(TaskLists.OWNER, account.name)
            values.put(TaskLists.SYNC_ENABLED, 1)
            values.put(TaskLists.VISIBLE, 1)
            return AndroidTaskList.create(account, provider, values)
        }

        private fun valuesFromCollectionInfo(info: CollectionInfo, withColor: Boolean): ContentValues {
            val values = ContentValues()
            values.put(TaskLists._SYNC_ID, info.uid)
            values.put(TaskLists.LIST_NAME, info.displayName)

            if (withColor)
                values.put(TaskLists.LIST_COLOR, if (info.color != null) info.color else defaultColor)

            return values
        }

        // helpers

        fun tasksProviderAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return context.packageManager.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
            else {
                val provider = TaskProvider.acquire(context.contentResolver, TaskProvider.ProviderName.OpenTasks)
                try {
                    return provider != null
                } finally {
                    provider?.close()
                }
            }
        }


        // HELPERS

        @Throws(RemoteException::class)
        fun onRenameAccount(resolver: ContentResolver, oldName: String, newName: String) {
            val client = resolver.acquireContentProviderClient(TaskProvider.ProviderName.OpenTasks.authority)
            if (client != null) {
                val values = ContentValues(1)
                values.put(Tasks.ACCOUNT_NAME, newName)
                client.update(Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), values, Tasks.ACCOUNT_NAME + "=?", arrayOf(oldName))
                client.release()
            }
        }
    }

}
