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
import android.content.SyncResult;
import android.os.Bundle;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.journalmanager.JournalEntryManager;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.SyncEntry;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalEvent;
import com.etesync.syncadapter.resource.LocalResource;

import org.apache.commons.codec.Charsets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
        } else if (events.length > 1)
            App.log.warning("Received multiple VCALs, using first one");

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
}
