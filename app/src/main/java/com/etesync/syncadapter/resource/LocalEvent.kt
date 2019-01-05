/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.annotation.TargetApi
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.text.TextUtils

import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants

import net.fortuna.ical4j.model.property.ProdId

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.logging.Level

import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEventFactory
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Event
import at.bitfire.vcard4android.ContactsStorageException

@TargetApi(17)
class LocalEvent : AndroidEvent, LocalResource {

    private var saveAsDirty = false // When true, the resource will be saved as dirty

    private var fileName: String? = null
    var eTag: String? = null

    var weAreOrganizer = true

    override val content: String
        @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class)
        get() {
            App.log.log(Level.FINE, "Preparing upload of event " + fileName!!, getEvent())

            val os = ByteArrayOutputStream()
            getEvent().write(os)

            return os.toString()
        }

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    override// Now the same
    val uuid: String?
        get() = fileName

    constructor(calendar: AndroidCalendar, event: Event, fileName: String?, eTag: String?) : super(calendar, event) {
        this.fileName = fileName
        this.eTag = eTag
    }

    protected constructor(calendar: AndroidCalendar, id: Long, baseInfo: ContentValues?) : super(calendar, id, baseInfo) {
        if (baseInfo != null) {
            fileName = baseInfo.getAsString(Events._SYNC_ID)
            eTag = baseInfo.getAsString(COLUMN_ETAG)
        }
    }

    /* process LocalEvent-specific fields */

    override fun populateEvent(values: ContentValues) {
        super.populateEvent(values)
        fileName = values.getAsString(Events._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        event.uid = values.getAsString(COLUMN_UID)

        event.sequence = values.getAsInteger(COLUMN_SEQUENCE)
        if (Build.VERSION.SDK_INT >= 17) {
            val isOrganizer = values.getAsInteger(Events.IS_ORGANIZER)
            weAreOrganizer = isOrganizer != null && isOrganizer != 0
        } else {
            val organizer = values.getAsString(Events.ORGANIZER)
            weAreOrganizer = organizer == null || organizer == calendar.account.name
        }
    }

    override fun buildEvent(recurrence: Event?, builder: ContentProviderOperation.Builder) {
        super.buildEvent(recurrence, builder)

        val buildException = recurrence != null
        val eventToBuild = if (buildException) recurrence else event

        builder.withValue(COLUMN_UID, event.uid)
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

    @Throws(CalendarStorageException::class)
    override fun prepareForUpload() {
        try {
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
            if (event != null)
                event.uid = uid

        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't update UID", e)
        }

    }

    @Throws(CalendarStorageException::class)
    override fun clearDirty(eTag: String) {
        try {
            val values = ContentValues(2)
            values.put(CalendarContract.Events.DIRTY, 0)
            values.put(COLUMN_ETAG, eTag)
            if (event != null)
                values.put(COLUMN_SEQUENCE, event.sequence)
            calendar.provider.update(eventSyncURI(), values, null, null)

            this.eTag = eTag
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't update UID", e)
        }

    }

    internal class Factory : AndroidEventFactory {

        override fun newInstance(calendar: AndroidCalendar, id: Long, baseInfo: ContentValues): AndroidEvent {
            return LocalEvent(calendar, id, baseInfo)
        }

        override fun newInstance(calendar: AndroidCalendar, event: Event): AndroidEvent {
            return LocalEvent(calendar, event, null, null)
        }

        override fun newArray(size: Int): Array<AndroidEvent?> {
            return arrayOfNulls(size)
        }

        companion object {
            val INSTANCE = Factory()
        }
    }

    companion object {
        init {
            Event.prodId = ProdId(Constants.PRODID_BASE + " ical4j/2.x")
        }

        internal val COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1
        internal val COLUMN_UID = if (Build.VERSION.SDK_INT >= 17) Events.UID_2445 else Events.SYNC_DATA2
        internal val COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3
    }
}
