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
import android.text.TextUtils
import at.bitfire.ical4android.AndroidTask
import at.bitfire.ical4android.AndroidTaskFactory
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.Task
import com.etesync.syncadapter.App
import org.dmfs.tasks.contract.TaskContract
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.Level

class LocalTask : AndroidTask, LocalResource<Task> {
    companion object {
        internal const val COLUMN_ETAG = TaskContract.Tasks.SYNC1
        internal const val COLUMN_UID = TaskContract.Tasks.SYNC2
        internal const val COLUMN_SEQUENCE = TaskContract.Tasks.SYNC3
    }

    private var fileName: String? = null
    var eTag: String? = null

    override val content: String
        get() {
            App.log.log(Level.FINE, "Preparing upload of task " + fileName!!, task)

            val os = ByteArrayOutputStream()
            task?.write(os)

            return os.toString()
        }

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    override// Now the same
    val uuid: String?
        get() = fileName

    constructor(taskList: AndroidTaskList<*>, task: Task, fileName: String?, eTag: String?, flags: Int)
            : super(taskList, task) {
        this.fileName = fileName
        this.eTag = eTag
    }

    private constructor(taskList: AndroidTaskList<*>, values: ContentValues): super(taskList) {
        id = values.getAsLong(TaskContract.Tasks._ID)
        fileName = values.getAsString(TaskContract.Tasks._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
    }


    /* process LocalTask-specific fields */

    override fun buildTask(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildTask(builder, update)
        builder.withValue(TaskContract.Tasks._SYNC_ID, fileName)
                .withValue(COLUMN_UID, task?.uid)
                .withValue(COLUMN_SEQUENCE, task?.sequence)
                .withValue(COLUMN_ETAG, eTag)
    }


    /* custom queries */

    override fun prepareForUpload() {
        val uid = UUID.randomUUID().toString()

        val values = ContentValues(2)
        values.put(TaskContract.Tasks._SYNC_ID, uid)
        values.put(COLUMN_UID, uid)
        taskList.provider.client.update(taskSyncURI(), values, null, null)

        fileName = uid
        val task = this.task
        if (task != null)
            task.uid = uid
    }

    override fun clearDirty(eTag: String) {
        val values = ContentValues(2)
        values.put(TaskContract.Tasks._DIRTY, 0)
        values.put(COLUMN_ETAG, eTag)
        if (task != null)
            values.put(COLUMN_SEQUENCE, task?.sequence)
        taskList.provider.client.update(taskSyncURI(), values, null, null)

        this.eTag = eTag
    }


    object Factory: AndroidTaskFactory<LocalTask> {
        override fun fromProvider(taskList: AndroidTaskList<*>, values: ContentValues) =
                LocalTask(taskList, values)
    }
}
