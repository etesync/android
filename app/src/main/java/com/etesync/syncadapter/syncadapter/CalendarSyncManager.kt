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
import android.content.Intent
import android.content.SyncResult
import android.net.Uri
import android.os.Build
import android.os.Bundle
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalEntryManager
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.SyncEntry
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalEvent
import net.fortuna.ical4j.model.property.Attendee
import okhttp3.HttpUrl
import org.acra.attachment.AcraContentProvider
import org.acra.util.IOUtils
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

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

        journal = JournalEntryManager(httpClient, remote, localCalendar().name!!)
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
            App.log.warning("Received VCard without data, ignoring")
            return
        } else if (events.size > 1) {
            App.log.warning("Received multiple VCALs, using first one")
        }

        val event = events[0]
        val local = localCollection!!.findByUid(event.uid!!)

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processEvent(event, local)
        } else {
            if (local != null) {
                App.log.info("Removing local record #" + local.id + " which has been deleted on the server")
                local.delete()
            } else {
                App.log.warning("Tried deleting a non-existent record: " + event.uid)
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
            val event = (local as LocalEvent).event

            if (event?.attendees?.isEmpty()!!) {
                return
            }
            createInviteAttendeesNotification(event, local.content)
        }
    }

    private fun createInviteAttendeesNotification(event: Event, icsContent: String) {
        val notificationHelper = NotificationHelper(context, event.uid!!, event.uid!!.hashCode())
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL, getEmailAddresses(event.attendees, false))
        val dateFormatDate = SimpleDateFormat("EEEE, MMM dd", Locale.US)
        intent.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.sync_calendar_attendees_email_subject,
                        event.summary,
                        dateFormatDate.format(event.dtStart?.date)))
        intent.putExtra(Intent.EXTRA_TEXT,
                context.getString(R.string.sync_calendar_attendees_email_content,
                        event.summary,
                        formatEventDates(event),
                        if (event.location != null) event.location else "",
                        formatAttendees(event.attendees)))
        val uri = createAttachmentFromString(context, icsContent)
        if (uri == null) {
            App.log.severe("Unable to create attachment from calendar event")
            return
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        notificationHelper.notify(
                context.getString(
                        R.string.sync_calendar_attendees_notification_title, event.summary),
                context.getString(R.string.sync_calendar_attendees_notification_content), null,
                intent,
                R.drawable.ic_email_black)
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class)
    private fun processEvent(newData: Event, localEvent: LocalEvent?): LocalEvent {
        var localEvent = localEvent
        // delete local event, if it exists
        if (localEvent != null) {
            App.log.info("Updating " + newData.uid + " in local calendar")
            localEvent.eTag = newData.uid
            localEvent.update(newData)
            syncResult.stats.numUpdates++
        } else {
            App.log.info("Adding " + newData.uid + " to local calendar")
            localEvent = LocalEvent(localCalendar(), newData, newData.uid, newData.uid)
            localEvent.add()
            syncResult.stats.numInserts++
        }

        return localEvent
    }

    private fun getEmailAddresses(attendees: List<Attendee>,
                                  shouldIncludeAccount: Boolean): Array<String> {
        val attendeesEmails = ArrayList<String>(attendees.size)
        for (attendee in attendees) {
            val attendeeEmail = attendee.value.replace("mailto:", "")
            if (!shouldIncludeAccount && attendeeEmail == account.name) {
                continue
            }
            attendeesEmails.add(attendeeEmail)
        }
        return attendeesEmails.toTypedArray()
    }

    private fun formatAttendees(attendeesList: List<Attendee>): String {
        val stringBuilder = StringBuilder()
        val attendees = getEmailAddresses(attendeesList, true)
        for (attendee in attendees) {
            stringBuilder.append("\n    ").append(attendee)
        }
        return stringBuilder.toString()
    }

    private fun formatEventDates(event: Event): String {
        val locale = Locale.getDefault()
        val timezone = if (event.dtStart?.timeZone != null) event.dtStart?.timeZone else TimeZone.getTimeZone("UTC")
        val dateFormatString = if (event.isAllDay()) "EEEE, MMM dd" else "EEEE, MMM dd @ hh:mm a"
        val longDateFormat = SimpleDateFormat(dateFormatString, locale)
        longDateFormat.timeZone = timezone
        val shortDateFormat = SimpleDateFormat("hh:mm a", locale)
        shortDateFormat.timeZone = timezone

        val startDate = event.dtStart?.date
        val endDate = event.getEndDate(true)!!.date
        val tzName = timezone?.getDisplayName(timezone?.inDaylightTime(startDate)!!, TimeZone.SHORT)

        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = startDate
        cal2.time = endDate
        val sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        if (sameDay && event.isAllDay()) {
            return longDateFormat.format(startDate)
        }
        return if (sameDay)
            String.format("%s - %s (%s)",
                    longDateFormat.format(startDate),
                    shortDateFormat.format(endDate),
                    tzName)
        else
            String.format("%s - %s (%s)", longDateFormat.format(startDate), longDateFormat.format(endDate), tzName)
    }

    private fun createAttachmentFromString(context: Context, content: String): Uri? {
        val name = UUID.randomUUID().toString()
        val parentDir = File(context.cacheDir, name)
        parentDir.mkdirs()
        val cache = File(parentDir, "invite.ics")
        try {
            IOUtils.writeStringToFile(cache, content)
            return AcraContentProvider.getUriForFile(context, cache)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }
}
