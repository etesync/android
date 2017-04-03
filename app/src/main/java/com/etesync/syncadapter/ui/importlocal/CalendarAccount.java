package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract;
import android.util.Log;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.resource.LocalCalendar;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tal on 27/03/17.
 */

public class CalendarAccount {
    private Account account;
    private List<LocalCalendar> calendars = new ArrayList<>();

    private static final String[] CAL_COLS = new String[]{
            Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.DELETED, Calendars.NAME};

    protected CalendarAccount(Account account) {
        this.account = account;
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
                    CAL_COLS, null, null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " ASC, " + ContactsContract.RawContacts.ACCOUNT_TYPE);
        } catch (Exception except) {
            App.log.warning("Calendar provider is missing columns, continuing anyway");
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
            String accountType = getString(cur, Calendars.ACCOUNT_TYPE);
            if (calendarAccount == null ||
                    !calendarAccount.getAccountName().equals(accountName) ||
                    !calendarAccount.getAccountType().equals(accountType)) {
                calendarAccount = new CalendarAccount(new Account(accountName, accountType));
                calendarAccounts.add(calendarAccount);
            }

            try {
                LocalCalendar localCalendar = LocalCalendar.findByName(calendarAccount.getAccount(),
                        contentProviderClient,
                        LocalCalendar.Factory.INSTANCE, getString(cur, Calendars.NAME));
                if (localCalendar != null) calendarAccount.calendars.add(localCalendar);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        contentProviderClient.release();
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

    public String getAccountName() {
        return account.name;
    }

    public String getAccountType() {
        return account.type;
    }

    public List<LocalCalendar> getCalendars() {
        return calendars;
    }

    public Account getAccount() {
        return account;
    }

    @Override
    public String toString() {
        return account.toString();
    }
}
