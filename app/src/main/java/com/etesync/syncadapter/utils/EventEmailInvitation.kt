package com.etesync.syncadapter.utils

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import at.bitfire.ical4android.Event
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import net.fortuna.ical4j.model.property.Attendee
import org.acra.attachment.AcraContentProvider
import org.acra.util.IOUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

fun emailSupportsAttachments(context: Context): Boolean {
    return !arrayOf(
            "ch.protonmail.android",
            "de.tutao.tutanota"
    ).any{
        packageInstalled(context, it)
    }
}

class EventEmailInvitation constructor(val context: Context, val account: Account) {
    fun createIntent(event: Event, icsContent: String): Intent? {
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
            Logger.log.severe("Unable to create attachment from calendar event")
            return null
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, uri)

        return intent
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
        val tzName = timezone?.getDisplayName(timezone.inDaylightTime(startDate), TimeZone.SHORT)

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