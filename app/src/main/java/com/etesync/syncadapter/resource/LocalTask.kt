/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.os.RemoteException
import android.provider.CalendarContract.Events

import com.etesync.syncadapter.Constants

import net.fortuna.ical4j.model.property.ProdId

import org.dmfs.provider.tasks.TaskContract.Tasks

import java.io.FileNotFoundException
import java.io.IOException
import java.text.ParseException
import java.util.UUID

import at.bitfire.ical4android.AndroidTask
import at.bitfire.ical4android.AndroidTaskFactory
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Task
import at.bitfire.vcard4android.ContactsStorageException

class LocalTask : AndroidTask, LocalResource {
    private var fileName: String? = null
    var eTag: String? = null

    override val content: String
        @Throws(IOException::class, ContactsStorageException::class)
        get() = ""

    override val isLocalOnly: Boolean
        get() = false

    override// Now the same
    val uuid: String?
        get() = fileName

    constructor(taskList: AndroidTaskList, task: Task, fileName: String?, eTag: String?) : super(taskList, task) {
        this.fileName = fileName
        this.eTag = eTag
    }

    protected constructor(taskList: AndroidTaskList, id: Long, baseInfo: ContentValues?) : super(taskList, id) {
        if (baseInfo != null) {
            fileName = baseInfo.getAsString(Events._SYNC_ID)
            eTag = baseInfo.getAsString(COLUMN_ETAG)
        }
    }

    /* process LocalTask-specific fields */

    @Throws(FileNotFoundException::class, RemoteException::class, ParseException::class)
    override fun populateTask(values: ContentValues) {
        super.populateTask(values)

        fileName = values.getAsString(Events._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        task.uid = values.getAsString(COLUMN_UID)

        task.sequence = values.getAsInteger(COLUMN_SEQUENCE)
    }

    override fun buildTask(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildTask(builder, update)
        builder.withValue(Tasks._SYNC_ID, fileName)
                .withValue(COLUMN_UID, task.uid)
                .withValue(COLUMN_SEQUENCE, task.sequence)
                .withValue(COLUMN_ETAG, eTag)
    }


    /* custom queries */

    @Throws(CalendarStorageException::class)
    override fun prepareForUpload() {
        try {
            val uid = UUID.randomUUID().toString()
            val newFileName = "$uid.ics"

            val values = ContentValues(2)
            values.put(Tasks._SYNC_ID, newFileName)
            values.put(COLUMN_UID, uid)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            fileName = newFileName
            if (task != null)
                task.uid = uid

        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't update UID", e)
        }

    }

    @Throws(CalendarStorageException::class)
    override fun clearDirty(eTag: String) {
        try {
            val values = ContentValues(2)
            values.put(Tasks._DIRTY, 0)
            values.put(COLUMN_ETAG, eTag)
            if (task != null)
                values.put(COLUMN_SEQUENCE, task.sequence)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            this.eTag = eTag
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't update _DIRTY/ETag/SEQUENCE", e)
        }

    }


    internal class Factory : AndroidTaskFactory {

        override fun newInstance(taskList: AndroidTaskList, id: Long, baseInfo: ContentValues): LocalTask {
            return LocalTask(taskList, id, baseInfo)
        }

        override fun newInstance(taskList: AndroidTaskList, task: Task): LocalTask {
            return LocalTask(taskList, task, null, null)
        }

        override fun newArray(size: Int): Array<LocalTask?> {
            return arrayOfNulls(size)
        }

        companion object {
            val INSTANCE = Factory()
        }
    }

    companion object {
        init {
            Task.prodId = ProdId(Constants.PRODID_BASE + " ical4j/2.x")
        }

        internal val COLUMN_ETAG = Tasks.SYNC1
        internal val COLUMN_UID = Tasks.SYNC2
        internal val COLUMN_SEQUENCE = Tasks.SYNC3
    }
}
