/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.syncadapter;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.NotificationHelper;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.journalmanager.JournalEntryManager;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.SyncEntry;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalEvent;
import com.etesync.syncadapter.resource.LocalResource;

import net.fortuna.ical4j.model.property.Attendee;

import org.acra.attachment.AcraContentProvider;
import org.acra.util.IOUtils;
import org.apache.commons.codec.Charsets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.ContactsStorageException;
import okhttp3.HttpUrl;

/**
 * <p>Synchronization manager for CardDAV collections; handles contacts and groups.</p>
 */
public class CalendarSyncManager extends SyncManager {
    final private HttpUrl remote;

    public CalendarSyncManager(Context context, Account account, AccountSettings settings, Bundle extras, String authority, SyncResult result, LocalCalendar calendar, HttpUrl remote) throws Exceptions.IntegrityException, Exceptions.GenericCryptoException {
        super(context, account, settings, extras, authority, result, calendar.getName(), CollectionInfo.Type.CALENDAR, account.name);
        localCollection = calendar;
        this.remote = remote;
    }

    @Override
    protected int notificationId() {
        return Constants.NOTIFICATION_CALENDAR_SYNC;
    }

    @Override
    protected String getSyncErrorTitle() {
        return context.getString(R.string.sync_error_calendar, account.name);
    }

    @Override
    protected String getSyncSuccessfullyTitle() {
        return context.getString(R.string.sync_successfully_calendar, info.displayName,
                account.name);
    }

    @Override
    protected boolean prepare() throws ContactsStorageException, CalendarStorageException {
        if (!super.prepare())
            return false;

        journal = new JournalEntryManager(httpClient, remote, localCalendar().getName());
        return true;
    }

    @Override
    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        super.prepareDirty();

        localCalendar().processDirtyExceptions();
    }


    // helpers

    private LocalCalendar localCalendar() {
        return (LocalCalendar) localCollection;
    }

    protected void processSyncEntry(SyncEntry cEntry) throws IOException, ContactsStorageException, CalendarStorageException, InvalidCalendarException {
        InputStream is = new ByteArrayInputStream(cEntry.getContent().getBytes(Charsets.UTF_8));

        Event[] events = Event.fromStream(is, Charsets.UTF_8);
        if (events.length == 0) {
            App.log.warning("Received VCard without data, ignoring");
            return;
        } else if (events.length > 1) {
            App.log.warning("Received multiple VCALs, using first one");
        }

        Event event = events[0];
        LocalEvent local = (LocalEvent) localCollection.getByUid(event.uid);

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processEvent(event, local);
        } else {
            if (local != null) {
                App.log.info("Removing local record #" + local.getId() + " which has been deleted on the server");
                local.delete();
            } else {
                App.log.warning("Tried deleting a non-existent record: " + event.uid);
            }
        }
    }

    protected void createLocalEntries() throws CalendarStorageException, ContactsStorageException, IOException {
        super.createLocalEntries();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            createInviteAttendeesNotification();
        }
    }

    private void createInviteAttendeesNotification() throws CalendarStorageException, ContactsStorageException, IOException {
        for (LocalResource local : localDirty) {
            Event event = ((LocalEvent) local).getEvent();

            if (event.attendees.isEmpty()) {
                return;
            }
            createInviteAttendeesNotification(event, local.getContent());
        }
    }

    private void createInviteAttendeesNotification(Event event, String icsContent) {
        NotificationHelper notificationHelper = new NotificationHelper(context, event.uid, event.uid.hashCode());
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_EMAIL, getEmailAddresses(event.attendees ,false));
        final DateFormat dateFormatDate =
                new SimpleDateFormat("EEEE, MMM dd", Locale.US);
        intent.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.sync_calendar_attendees_email_subject,
                        event.summary,
                        dateFormatDate.format(event.dtStart.getDate())));
        intent.putExtra(Intent.EXTRA_TEXT,
                context.getString(R.string.sync_calendar_attendees_email_content,
                        event.summary,
                        formatEventDates(event),
                        (event.location != null) ? event.location : "",
                        formatAttendees(event.attendees)));
        Uri uri = createAttachmentFromString(context, event.uid, icsContent);
        if (uri == null) {
            App.log.severe("Unable to create attachment from calendar event");
            return;
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        notificationHelper.notify(
                context.getString(
                        R.string.sync_calendar_attendees_notification_title, event.summary),
                context.getString(R.string.sync_calendar_attendees_notification_content),
                null,
                intent,
                R.drawable.ic_email_black);
    }

    private LocalResource processEvent(final Event newData, LocalEvent localEvent) throws IOException, ContactsStorageException, CalendarStorageException {
        // delete local event, if it exists
        if (localEvent != null) {
            App.log.info("Updating " + newData.uid + " in local calendar");
            localEvent.setETag(newData.uid);
            localEvent.update(newData);
            syncResult.stats.numUpdates++;
        } else {
            App.log.info("Adding " + newData.uid + " to local calendar");
            localEvent = new LocalEvent(localCalendar(), newData, newData.uid, newData.uid);
            localEvent.add();
            syncResult.stats.numInserts++;
        }

        return localEvent;
    }

    private String[] getEmailAddresses(List<Attendee> attendees,
                                              boolean shouldIncludeAccount) {
        List<String> attendeesEmails = new ArrayList<>(attendees.size());
        for (Attendee attendee : attendees) {
            String attendeeEmail = attendee.getValue().replace("mailto:", "");
            if (!shouldIncludeAccount && attendeeEmail.equals(account.name)) {
                continue;
            }
            attendeesEmails.add(attendeeEmail);
        }
        return attendeesEmails.toArray(new String[0]);
    }

    private String formatAttendees(List<Attendee> attendeesList) {
        StringBuilder stringBuilder = new StringBuilder();
        String[] attendees = getEmailAddresses(attendeesList, true);
        for (String attendee : attendees) {
            stringBuilder.append("\n    ").append(attendee);
        }
        return stringBuilder.toString();
    }

    private static String formatEventDates(Event event) {
        final Locale locale = Locale.US;
        final String dateFormatString =
                event.isAllDay() ? "EEEE, MMM dd" : "EEEE, MMM dd @ hh:mm a";
        final DateFormat dateFormat =
                new SimpleDateFormat(dateFormatString, locale);
        final TimeZone timezone = event.dtStart.getTimeZone();
        Date startDate = event.dtStart.getDate();
        Date endDate = event.getEndDate(true).getDate();
        final String tzName = (timezone != null) ?
                timezone.getDisplayName(timezone.inDaylightTime(startDate), TimeZone.SHORT)
                : "UTC";
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(startDate);
        cal2.setTime(endDate);
        boolean sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        if (sameDay && event.isAllDay()) {
            return dateFormat.format(startDate);
        }
        return sameDay ?
                String.format("%s - %s (%s)",
                        dateFormat.format(startDate),
                        new SimpleDateFormat("hh:mm a", Locale.US).format(endDate),
                        tzName) :
                String.format("%s - %s (%s)", dateFormat.format(startDate), dateFormat.format(endDate), tzName);
    }

    private Uri createAttachmentFromString(Context context, String name, String content) {
        final File parentDir = new File (context.getCacheDir(), name);
        parentDir.mkdirs();
        final File cache = new File(parentDir, "invite.ics");
        try {
            IOUtils.writeStringToFile(cache, content);
            return AcraContentProvider.getUriForFile(context, cache);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
