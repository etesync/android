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
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.*
import at.bitfire.ical4android.*
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.JournalEntity
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.logging.Level

class LocalCalendar private constructor(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): AndroidCalendar<LocalEvent>(account, provider, LocalEvent.Factory, id), LocalCollection<LocalEvent> {

    companion object {
        val defaultColor = -0x743cb6     // light green 500

        val COLUMN_CTAG = Calendars.CAL_SYNC1

        fun create(account: Account, provider: ContentProviderClient, journalEntity: JournalEntity): Uri {
            val values = valuesFromCollectionInfo(journalEntity, true)

            // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
            values.put(Calendars.ACCOUNT_NAME, account.name)
            values.put(Calendars.ACCOUNT_TYPE, account.type)
            values.put(Calendars.OWNER_ACCOUNT, account.name)

            // flag as visible & synchronizable at creation, might be changed by user at any time
            values.put(Calendars.VISIBLE, 1)
            values.put(Calendars.SYNC_EVENTS, 1)

            return AndroidCalendar.create(account, provider, values)
        }

        fun findByName(account: Account, provider: ContentProviderClient, factory: Factory, name: String): LocalCalendar?
            = AndroidCalendar.find(account, provider, factory, Calendars.NAME + "==?", arrayOf(name)).firstOrNull()

        private fun valuesFromCollectionInfo(journalEntity: JournalEntity, withColor: Boolean): ContentValues {
            val info = journalEntity.info
            val values = ContentValues()
            values.put(Calendars.NAME, info.uid)
            values.put(Calendars.CALENDAR_DISPLAY_NAME, info.displayName)

            if (withColor)
                values.put(Calendars.CALENDAR_COLOR, if (info.color != null) info.color else defaultColor)

            if (journalEntity.isReadOnly)
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
            else {
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1)
                values.put(Calendars.CAN_ORGANIZER_RESPOND, 1)
            }

            info.timeZone?.let { tzData ->
                try {
                    val timeZone = DateUtils.parseVTimeZone(tzData)
                    timeZone.timeZoneId?.let { tzId ->
                        values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(tzId.value))
                    }
                } catch(e: IllegalArgumentException) {
                    Logger.log.log(Level.WARNING, "Couldn't parse calendar default time zone", e)
                }
            }
            values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT)
            values.put(Calendars.ALLOWED_AVAILABILITY, StringUtils.join(intArrayOf(Reminders.AVAILABILITY_TENTATIVE, Reminders.AVAILABILITY_FREE, Reminders.AVAILABILITY_BUSY), ","))
            values.put(Calendars.ALLOWED_ATTENDEE_TYPES, StringUtils.join(intArrayOf(CalendarContract.Attendees.TYPE_OPTIONAL, CalendarContract.Attendees.TYPE_REQUIRED, CalendarContract.Attendees.TYPE_RESOURCE), ", "))
            return values
        }
    }

    override val url: String?
        get() = name

    fun update(journalEntity: JournalEntity, updateColor: Boolean) =
            update(valuesFromCollectionInfo(journalEntity, updateColor))


    override fun findDeleted() =
            queryEvents("${Events.DELETED}!=0 AND ${Events.ORIGINAL_ID} IS NULL", null)

    override fun findDirty(limit: Int?): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()
        val sortOrder = if (limit != null) "${Events._ID} ASC LIMIT $limit" else null

        // get dirty events which are required to have an increased SEQUENCE value
        for (localEvent in queryEvents("${Events.DIRTY}!=0 AND ${Events.DELETED}==0 AND ${Events.ORIGINAL_ID} IS NULL", null, sortOrder)) {
            val event = localEvent.event!!
            val sequence = event.sequence
            if (event.sequence == null)      // sequence has not been assigned yet (i.e. this event was just locally created)
                event.sequence = 0
            else if (localEvent.weAreOrganizer)
                event.sequence = sequence!! + 1
            dirty += localEvent
        }

        return dirty
    }

    override fun findWithoutFileName(): List<LocalEvent>
            = queryEvents(Events._SYNC_ID + " IS NULL AND " + Events.ORIGINAL_ID + " IS NULL", null)

    override fun findAll(): List<LocalEvent>
            = queryEvents(null, null)

    override fun findByUid(uid: String): LocalEvent?
        = queryEvents(Events._SYNC_ID + " =? ", arrayOf(uid)).firstOrNull()

    fun processDirtyExceptions() {
        // process deleted exceptions
        Logger.log.info("Processing deleted exceptions")
        try {
            val cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                    Events.DELETED + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null)
            while (cursor != null && cursor.moveToNext()) {
                Logger.log.fine("Found deleted exception, removing; then re-schuling original event")
                val id = cursor.getLong(0)
                // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)

                // get original event's SEQUENCE
                val cursor2 = provider.query(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)),
                        arrayOf(LocalEvent.COLUMN_SEQUENCE), null, null, null)
                val originalSequence = if (cursor2 == null || cursor2.isNull(0)) 0 else cursor2.getInt(0)

                cursor2!!.close()
                val batch = BatchOperation(provider)
                // re-schedule original event and set it to DIRTY
                batch.enqueue(BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, originalSequence + 1)
                                .withValue(Events.DIRTY, 1)
                ))
                // remove exception
                batch.enqueue(BatchOperation.Operation(
                        ContentProviderOperation.newDelete(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                ))
                batch.commit()
            }
            cursor!!.close()
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't process locally modified exception", e)
        }

        // process dirty exceptions
        Logger.log.info("Processing dirty exceptions")
        try {
            val cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                    Events.DIRTY + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null)
            while (cursor != null && cursor.moveToNext()) {
                Logger.log.fine("Found dirty exception, increasing SEQUENCE to re-schedule")
                val id = cursor.getLong(0)
                // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)
                val sequence = if (cursor.isNull(2)) 0 else cursor.getInt(2)

                val batch = BatchOperation(provider)
                // original event to DIRTY
                batch.enqueue(BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                .withValue(Events.DIRTY, 1)
                ))
                // increase SEQUENCE and set DIRTY to 0
                batch.enqueue(BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, sequence + 1)
                                .withValue(Events.DIRTY, 0)
                ))
                batch.commit()
            }
            cursor!!.close()
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't process locally modified exception", e)
        }

    }

    override fun count(): Long {
        try {
            val cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI), null,
                    Events.CALENDAR_ID + "=?", arrayOf(id.toString()), null)
            try {
                return cursor?.count?.toLong()!!
            } finally {
                cursor?.close()
            }
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't query calendar events", e)
        }

    }


    /** Fix all of the etags of all of the non-dirty events to be non-null.
     * Currently set to all ones..  */
    @Throws(CalendarStorageException::class)
    fun fixEtags() {
        val newEtag = "1111111111111111111111111111111111111111111111111111111111111111"
        val where = Events.CALENDAR_ID + "=? AND " + Events.DIRTY + "=0 AND " + LocalEvent.COLUMN_ETAG + " IS NULL"
        val whereArgs = arrayOf(id.toString())

        val values = ContentValues(1)
        values.put(LocalEvent.COLUMN_ETAG, newEtag)
        try {
            val fixed = provider.update(syncAdapterURI(Events.CONTENT_URI),
                    values, where, whereArgs)
            Logger.log.info("Fixed entries: " + fixed.toString())
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't fix etags", e)
        }

    }

    object Factory: AndroidCalendarFactory<LocalCalendar> {

        override fun newInstance(account: Account, provider: ContentProviderClient, id: Long) =
                LocalCalendar(account, provider, id)
    }
}
