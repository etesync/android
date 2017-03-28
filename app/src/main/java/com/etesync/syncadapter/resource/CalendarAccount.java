package com.etesync.syncadapter.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidEvent;
import at.bitfire.ical4android.AndroidEventFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import lombok.Cleanup;

/**
 * Created by tal on 27/03/17.
 */

public class CalendarAccount {
    private static final String TAG = "CalendarAccount";

    public String accountName;
    public List<LocalCalendar> calendars = new ArrayList<>();

    private static final String[] CAL_COLS = new String[] {
            Calendars._ID, Calendars.DELETED, Calendars.NAME, Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.OWNER_ACCOUNT,
            Calendars.VISIBLE, Calendars.CALENDAR_TIME_ZONE };

    private static final String[] CAL_ID_COLS = new String[] { Events._ID };
    private static final String CAL_ID_WHERE = Events.CALENDAR_ID + "=?";

    protected CalendarAccount(String accountName) {
        this.accountName = accountName;
    }

    // Load all available calendars.
    // If an empty list is returned the caller probably needs to enable calendar
    // read permissions in App Ops/XPrivacy etc.
    public static List<CalendarAccount> loadAll(ContentResolver resolver) {

        if (missing(resolver, Calendars.CONTENT_URI) || missing(resolver, Events.CONTENT_URI))
            return new ArrayList<>();

        Cursor cur;
        try {
            cur = resolver.query(Calendars.CONTENT_URI,
                    CAL_COLS, null, null, Calendars.ACCOUNT_NAME + " ASC");
        } catch (Exception except) {
            Log.w(TAG, "Calendar provider is missing columns, continuing anyway");
            cur = resolver.query(Calendars.CONTENT_URI, null, null, null, null);
            except.printStackTrace();
        }
        List<CalendarAccount> calendarAccounts = new ArrayList<>(cur.getCount());

        CalendarAccount calendarAccount = null;
        ContentProviderClient contentProviderClient = resolver.acquireContentProviderClient(CalendarContract.CONTENT_URI);
        while (cur.moveToNext()) {
            if (getLong(cur, Calendars.DELETED) != 0)
                continue;

            String accountName = getString(cur, Calendars.ACCOUNT_NAME);
            if (calendarAccount == null || !calendarAccount.accountName.equals(accountName)) {
                calendarAccount = new CalendarAccount(accountName);
                calendarAccounts.add(calendarAccount);
            }

            long id = getLong(cur, Calendars._ID);
            if (id == -1) {
                continue;
            }

            final String[] args = new String[] { String.valueOf(id) };
            Cursor eventsCur = resolver.query(Events.CONTENT_URI, CAL_ID_COLS, CAL_ID_WHERE, args, null);
            Account account = new Account(accountName, getString(cur, Calendars.ACCOUNT_TYPE));

            try {
                LocalCalendar localCalendar = LocalCalendar.findByName(account, contentProviderClient,
                        LocalCalendar.Factory.INSTANCE, getString(cur, Calendars.NAME));
                if (localCalendar != null) calendarAccount.calendars.add(localCalendar);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            eventsCur.close();
        }
        contentProviderClient.close();
        cur.close();
        return calendarAccounts;
    }

    private static int getColumnIndex(Cursor cur, String dbName) {
        return dbName == null ? -1 : cur.getColumnIndex(dbName);
    }

    private static long getLong(Cursor cur, String dbName) {
        int i = getColumnIndex(cur, dbName);
        return i == -1 ? -1 : cur.getLong(i);
    }

    private static String getString(Cursor cur, String dbName) {
        int i = getColumnIndex(cur, dbName);
        return i == -1 ? null : cur.getString(i);
    }

    private static boolean missing(ContentResolver resolver, Uri uri) {
        // Determine if a provider is missing
        ContentProviderClient provider = resolver.acquireContentProviderClient(uri);
        if (provider != null)
            provider.release();
        return provider == null;
    }

    @Override
    public String toString() {
        return accountName + " calendars:" + calendars.size();
    }

    static class Factory implements AndroidEventFactory {
        static final LocalEvent.Factory INSTANCE = new LocalEvent.Factory();

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, long id, ContentValues baseInfo) {
            return new LocalEvent(calendar, id, baseInfo);
        }

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, Event event) {
            return new LocalEvent(calendar, event, null, null);
        }

        @Override
        public AndroidEvent[] newArray(int size) {
            return new LocalEvent[size];
        }
    }
}
