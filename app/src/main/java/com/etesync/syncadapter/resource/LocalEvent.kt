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
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.text.TextUtils
import at.bitfire.ical4android.*
import at.bitfire.ical4android.Constants.ical4jVersion
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.log.Logger
import net.fortuna.ical4j.model.property.ProdId
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.Level

class LocalEvent : AndroidEvent, LocalResource<Event> {
    companion object {
        init {
            ICalendar.prodId = ProdId(Constants.PRODID_BASE + " ical4j/" + ical4jVersion)
        }

        internal const val COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1
        internal const val COLUMN_UID = Events.UID_2445
        internal const val COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3
    }

    private var saveAsDirty = false // When true, the resource will be saved as dirty

    private var fileName: String? = null
    var eTag: String? = null

    var weAreOrganizer = true

    override val content: String
        get() {
            Logger.log.log(Level.FINE, "Preparing upload of event " + fileName!!, event)

            val os = ByteArrayOutputStream()
            event?.write(os)

            return os.toString()
        }

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    override// Now the same
    val uuid: String?
        get() = fileName

    constructor(calendar: AndroidCalendar<*>, event: Event, fileName: String?, eTag: String?) : super(calendar, event) {
        this.fileName = fileName
        this.eTag = eTag
    }

    protected constructor(calendar: AndroidCalendar<*>, baseInfo: ContentValues) : super(calendar, baseInfo) {
        fileName = baseInfo.getAsString(Events._SYNC_ID)
        eTag = baseInfo.getAsString(COLUMN_ETAG)
    }

    /* process LocalEvent-specific fields */

    override fun populateEvent(row: ContentValues) {
        super.populateEvent(row)
        fileName = row.getAsString(Events._SYNC_ID)
        eTag = row.getAsString(COLUMN_ETAG)
        event?.uid = row.getAsString(COLUMN_UID)

        event?.sequence = row.getAsInteger(COLUMN_SEQUENCE)
        val isOrganizer = row.getAsInteger(Events.IS_ORGANIZER)
        weAreOrganizer = isOrganizer != null && isOrganizer != 0
    }

    override fun buildEvent(recurrence: Event?, builder: ContentProviderOperation.Builder) {
        super.buildEvent(recurrence, builder)

        val buildException = recurrence != null
        val eventToBuild = if (buildException) recurrence else event

        builder.withValue(COLUMN_UID, event?.uid)
                .withValue(COLUMN_SEQUENCE, eventToBuild?.sequence)
                .withValue(CalendarContract.Events.DIRTY, if (saveAsDirty) 1 else 0)
                .withValue(CalendarContract.Events.DELETED, 0)

        if (buildException)
            builder.withValue(Events.ORIGINAL_SYNC_ID, fileName)
        else
            builder.withValue(Events._SYNC_ID, fileName)
                    .withValue(COLUMN_ETAG, eTag)
    }

    @Throws(CalendarStorageException::class)
    fun addAsDirty(): Uri {
        saveAsDirty = true
        return this.add()
    }

    @Throws(CalendarStorageException::class)
    fun updateAsDirty(event: Event): Uri {
        saveAsDirty = true
        return this.update(event)
    }

    /* custom queries */

    override fun prepareForUpload() {
        var uid: String? = null
        val c = calendar.provider.query(eventSyncURI(), arrayOf(COLUMN_UID), null, null, null)
        if (c.moveToNext())
            uid = c.getString(0)
        if (uid == null)
            uid = UUID.randomUUID().toString()

        c.close()
        val newFileName = uid

        val values = ContentValues(2)
        values.put(Events._SYNC_ID, newFileName)
        values.put(COLUMN_UID, uid)
        calendar.provider.update(eventSyncURI(), values, null, null)

        fileName = newFileName

        val event = this.event
        if (event != null)
            event.uid = uid
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(CalendarContract.Events.DELETED, 0)
        calendar.provider.update(eventSyncURI(), values, null, null)
    }

    override fun clearDirty(eTag: String) {
        val values = ContentValues(2)
        values.put(CalendarContract.Events.DIRTY, 0)
        values.put(COLUMN_ETAG, eTag)
        if (event != null)
            values.put(COLUMN_SEQUENCE, event?.sequence)
        calendar.provider.update(eventSyncURI(), values, null, null)

        this.eTag = eTag
    }

    object Factory: AndroidEventFactory<LocalEvent> {
        override fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues): LocalEvent =
                LocalEvent(calendar, values)
    }
}
