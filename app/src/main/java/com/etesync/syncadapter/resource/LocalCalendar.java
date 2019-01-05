/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;

import net.fortuna.ical4j.model.component.VTimeZone;

import org.apache.commons.lang3.StringUtils;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidCalendarFactory;
import at.bitfire.ical4android.BatchOperation;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.DateUtils;

public class LocalCalendar extends AndroidCalendar implements LocalCollection {

    public static final int defaultColor = 0xFF8bc34a;     // light green 500

    public static final String COLUMN_CTAG = Calendars.CAL_SYNC1;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Events._ID,
            Events._SYNC_ID,
            LocalEvent.COLUMN_ETAG
    };

    @Override
    protected String[] eventBaseInfoColumns() {
        return BASE_INFO_COLUMNS;
    }


    protected LocalCalendar(Account account, ContentProviderClient provider, long id) {
        super(account, provider, LocalEvent.Factory.INSTANCE, id);
    }

    public static Uri create(@NonNull Account account, @NonNull ContentProviderClient provider, @NonNull JournalEntity journalEntity) throws CalendarStorageException {
        ContentValues values = valuesFromCollectionInfo(journalEntity, true);

        // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
        values.put(Calendars.ACCOUNT_NAME, account.name);
        values.put(Calendars.ACCOUNT_TYPE, account.type);
        values.put(Calendars.OWNER_ACCOUNT, account.name);

        // flag as visible & synchronizable at creation, might be changed by user at any time
        values.put(Calendars.VISIBLE, 1);
        values.put(Calendars.SYNC_EVENTS, 1);

        return create(account, provider, values);
    }

    public void update(JournalEntity journalEntity, boolean updateColor) throws CalendarStorageException {
        update(valuesFromCollectionInfo(journalEntity, updateColor));
    }

    public static LocalCalendar findByName(Account account, ContentProviderClient provider, AndroidCalendarFactory factory, String name) throws FileNotFoundException, CalendarStorageException {
        AndroidCalendar ret[] = LocalCalendar.find(account, provider, factory, Calendars.NAME + "==?", new String[]{name});
        if (ret.length == 1) {
            return (LocalCalendar) ret[0];
        } else {
            App.Companion.getLog().severe("No calendar found for name " + name);
            return null;
        }
    }

    private static ContentValues valuesFromCollectionInfo(JournalEntity journalEntity, boolean withColor) {
        CollectionInfo info = journalEntity.getInfo();
        ContentValues values = new ContentValues();
        values.put(Calendars.NAME, info.getUid());
        values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getDisplayName());

        if (withColor)
            values.put(Calendars.CALENDAR_COLOR, info.getColor() != null ? info.getColor() : defaultColor);

        if (journalEntity.isReadOnly())
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
        else {
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
            values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1);
            values.put(Calendars.CAN_ORGANIZER_RESPOND, 1);
        }

        if (!TextUtils.isEmpty(info.getTimeZone())) {
            VTimeZone timeZone = DateUtils.parseVTimeZone(info.getTimeZone());
            if (timeZone != null && timeZone.getTimeZoneId() != null)
                values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(timeZone.getTimeZoneId().getValue()));
        }
        values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT);
        values.put(Calendars.ALLOWED_AVAILABILITY, StringUtils.join(new int[] { Reminders.AVAILABILITY_TENTATIVE, Reminders.AVAILABILITY_FREE, Reminders.AVAILABILITY_BUSY }, ","));
        values.put(Calendars.ALLOWED_ATTENDEE_TYPES, StringUtils.join(new int[] { CalendarContract.Attendees.TYPE_OPTIONAL, CalendarContract.Attendees.TYPE_REQUIRED, CalendarContract.Attendees.TYPE_RESOURCE }, ", "));
        return values;
    }

    @Override
    public LocalEvent[] getDeleted() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events.DELETED + "!=0 AND " + Events.ORIGINAL_ID + " IS NULL", null);
    }

    @Override
    public LocalEvent[] getWithoutFileName() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events._SYNC_ID + " IS NULL AND " + Events.ORIGINAL_ID + " IS NULL", null);
    }


    public LocalEvent[] getAll() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(null, null);
    }

    @Override
    public LocalEvent getByUid(String uid) throws CalendarStorageException {
        LocalEvent[] ret = (LocalEvent[]) queryEvents(Events._SYNC_ID + " =? ", new String[]{uid});
        if (ret != null && ret.length > 0) {
            return ret[0];
        }
        return null;
    }

    @Override
    public LocalResource[] getDirty() throws CalendarStorageException, FileNotFoundException {
        List<LocalResource> dirty = new LinkedList<>();

        // get dirty events which are required to have an increased SEQUENCE value
        for (LocalEvent event : (LocalEvent[])queryEvents(Events.DIRTY + "!=0 AND " + Events.DELETED + "==0 AND " + Events.ORIGINAL_ID + " IS NULL", null)) {
            if (event.getEvent().sequence == null)      // sequence has not been assigned yet (i.e. this event was just locally created)
                event.getEvent().sequence = 0;
            else if (event.weAreOrganizer)
                event.getEvent().sequence++;
            dirty.add(event);
        }

        return dirty.toArray(new LocalResource[dirty.size()]);
    }

    @SuppressWarnings("Recycle")
    public void processDirtyExceptions() throws CalendarStorageException {
        // process deleted exceptions
        App.Companion.getLog().info("Processing deleted exceptions");
        try {
            Cursor cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE },
                    Events.DELETED + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null);
            while (cursor != null && cursor.moveToNext()) {
                App.Companion.getLog().fine("Found deleted exception, removing; then re-schuling original event");
                long    id = cursor.getLong(0),             // can't be null (by definition)
                        originalID = cursor.getLong(1);     // can't be null (by query)

                // get original event's SEQUENCE
                Cursor cursor2 = provider.query(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)),
                        new String[] { LocalEvent.COLUMN_SEQUENCE },
                        null, null, null);
                int originalSequence = (cursor2 == null || cursor2.isNull(0)) ? 0 : cursor2.getInt(0);

                cursor2.close();
                BatchOperation batch = new BatchOperation(provider);
                // re-schedule original event and set it to DIRTY
                batch.enqueue(new BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, originalSequence + 1)
                                .withValue(Events.DIRTY, 1)
                ));
                // remove exception
                batch.enqueue(new BatchOperation.Operation(
                        ContentProviderOperation.newDelete(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                ));
                batch.commit();
            }
            cursor.close();
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't process locally modified exception", e);
        }

        // process dirty exceptions
        App.Companion.getLog().info("Processing dirty exceptions");
        try {
            Cursor cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE },
                    Events.DIRTY + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null);
            while (cursor != null && cursor.moveToNext()) {
                App.Companion.getLog().fine("Found dirty exception, increasing SEQUENCE to re-schedule");
                long    id = cursor.getLong(0),             // can't be null (by definition)
                        originalID = cursor.getLong(1);     // can't be null (by query)
                int sequence = cursor.isNull(2) ? 0 : cursor.getInt(2);

                BatchOperation batch = new BatchOperation(provider);
                // original event to DIRTY
                batch.enqueue(new BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                .withValue(Events.DIRTY, 1)
                ));
                // increase SEQUENCE and set DIRTY to 0
                batch.enqueue(new BatchOperation.Operation(
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, sequence + 1)
                                .withValue(Events.DIRTY, 0)
                ));
                batch.commit();
            }
            cursor.close();
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't process locally modified exception", e);
        }
    }

    @Override
    public long count() throws CalendarStorageException {
        String where = Events.CALENDAR_ID + "=?";
        String whereArgs[] = {String.valueOf(id)};

        try {
            Cursor cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    null,
                    where, whereArgs, null);
            try {
                return cursor.getCount();
            } finally {
                cursor.close();
            }
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }
    }

    public static class Factory implements AndroidCalendarFactory {
        public static final Factory INSTANCE = new Factory();

        @Override
        public AndroidCalendar newInstance(Account account, ContentProviderClient provider, long id) {
            return new LocalCalendar(account, provider, id);
        }

        @Override
        public AndroidCalendar[] newArray(int size) {
            return new LocalCalendar[size];
        }
    }

    /** Fix all of the etags of all of the non-dirty events to be non-null.
     * Currently set to all ones.. */
    public void fixEtags() throws CalendarStorageException {
        String newEtag = "1111111111111111111111111111111111111111111111111111111111111111";
        String where = Events.CALENDAR_ID + "=? AND " + Events.DIRTY + "=0 AND " + LocalEvent.COLUMN_ETAG + " IS NULL";
        String whereArgs[] = {String.valueOf(id)};

        ContentValues values = new ContentValues(1);
        values.put(LocalEvent.COLUMN_ETAG, newEtag);
        try {
            int fixed = provider.update(syncAdapterURI(Events.CONTENT_URI),
                    values, where, whereArgs);
            App.Companion.getLog().info("Fixed entries: " + String.valueOf(fixed));
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't fix etags", e);
        }
    }
}
