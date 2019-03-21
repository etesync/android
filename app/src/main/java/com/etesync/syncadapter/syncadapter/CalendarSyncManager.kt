/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalEntryManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.SyncEntry
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalEvent
import com.etesync.syncadapter.utils.EventEmailInvitation
import okhttp3.HttpUrl
import java.io.IOException
import java.io.StringReader

/**
 *
 * Synchronization manager for CardDAV collections; handles contacts and groups.
 */
class CalendarSyncManager @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
constructor(context: Context, account: Account, settings: AccountSettings, extras: Bundle, authority: String, result: SyncResult, calendar: LocalCalendar, private val remote: HttpUrl) : SyncManager<LocalEvent>(context, account, settings, extras, authority, result, calendar.name!!, CollectionInfo.Type.CALENDAR, account.name) {

    override val syncErrorTitle: String
        get() = context.getString(R.string.sync_error_calendar, account.name)

    override val syncSuccessfullyTitle: String
        get() = context.getString(R.string.sync_successfully_calendar, info.displayName,
                account.name)

    init {
        localCollection = calendar
    }

    override fun notificationId(): Int {
        return Constants.NOTIFICATION_CALENDAR_SYNC
    }

    @Throws(ContactsStorageException::class, CalendarStorageException::class)
    override fun prepare(): Boolean {
        if (!super.prepare())
            return false

        journal = JournalEntryManager(httpClient.okHttpClient, remote, localCalendar().name!!)
        return true
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    override fun prepareDirty() {
        super.prepareDirty()

        localCalendar().processDirtyExceptions()
    }


    // helpers

    private fun localCalendar(): LocalCalendar {
        return localCollection as LocalCalendar
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class, InvalidCalendarException::class)
    override fun processSyncEntry(cEntry: SyncEntry) {
        val inputReader = StringReader(cEntry.content)

        val events = Event.fromReader(inputReader)
        if (events.size == 0) {
            Logger.log.warning("Received VCard without data, ignoring")
            return
        } else if (events.size > 1) {
            Logger.log.warning("Received multiple VCALs, using first one")
        }

        val event = events[0]
        val local = localCollection!!.findByUid(event.uid!!)

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processEvent(event, local)
        } else {
            if (local != null) {
                Logger.log.info("Removing local record #" + local.id + " which has been deleted on the server")
                local.delete()
            } else {
                Logger.log.warning("Tried deleting a non-existent record: " + event.uid)
            }
        }
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class, IOException::class)
    override fun createLocalEntries() {
        super.createLocalEntries()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            createInviteAttendeesNotification()
        }
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class, IOException::class)
    private fun createInviteAttendeesNotification() {
        for (local in localDirty) {
            val event = local.event

            if (event?.attendees?.isEmpty()!!) {
                return
            }
            createInviteAttendeesNotification(event, local.content)
        }
    }

    private fun createInviteAttendeesNotification(event: Event, icsContent: String) {
        val intent = EventEmailInvitation(context, account).createIntent(event, icsContent)
        if (intent != null) {
            val notificationHelper = SyncNotification(context, event.uid!!, event.uid!!.hashCode())
            notificationHelper.notify(
                    context.getString(
                            R.string.sync_calendar_attendees_notification_title, event.summary),
                    context.getString(R.string.sync_calendar_attendees_notification_content), null,
                    intent,
                    R.drawable.ic_email_black)
        }
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class)
    private fun processEvent(newData: Event, _localEvent: LocalEvent?): LocalEvent {
        var localEvent = _localEvent
        // delete local event, if it exists
        if (localEvent != null) {
            Logger.log.info("Updating " + newData.uid + " in local calendar")
            localEvent.eTag = newData.uid
            localEvent.update(newData)
            syncResult.stats.numUpdates++
        } else {
            Logger.log.info("Adding " + newData.uid + " to local calendar")
            localEvent = LocalEvent(localCalendar(), newData, newData.uid, newData.uid)
            localEvent.add()
            syncResult.stats.numInserts++
        }

        return localEvent
    }
}
