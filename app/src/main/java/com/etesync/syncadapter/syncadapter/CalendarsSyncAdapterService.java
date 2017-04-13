/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.provider.CalendarContract;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.NotificationHelper;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.ui.DebugInfoActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.ical4android.CalendarStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import okhttp3.HttpUrl;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;

public class CalendarsSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new SyncAdapter(this);
    }


    private static class SyncAdapter extends SyncAdapterService.SyncAdapter {

        public SyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);

            NotificationHelper notificationManager = new NotificationHelper(getContext(), "journals-calendar", Constants.NOTIFICATION_CALENDAR_SYNC);
            notificationManager.cancel();

            try {
                AccountSettings settings = new AccountSettings(getContext(), account);
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                new RefreshCollections(account, CollectionInfo.Type.CALENDAR).run();

                updateLocalCalendars(provider, account, settings);

                HttpUrl principal = HttpUrl.get(settings.getUri());

                for (LocalCalendar calendar : (LocalCalendar[]) LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                    App.log.info("Synchronizing calendar #" + calendar.getId() + ", URL: " + calendar.getName());
                    CalendarSyncManager syncManager = new CalendarSyncManager(getContext(), account, settings, extras, authority, syncResult, calendar, principal);
                    syncManager.performSync();
                }
            } catch (Exceptions.ServiceUnavailableException e) {
                syncResult.stats.numIoExceptions++;
                syncResult.delayUntil = (e.retryAfter > 0) ? e.retryAfter : Constants.DEFAULT_RETRY_DELAY;
            } catch (Exception | OutOfMemoryError e) {
                if (e instanceof CalendarStorageException || e instanceof SQLiteException) {
                    App.log.log(Level.SEVERE, "Couldn't prepare local calendars", e);
                    syncResult.databaseError = true;
                }

                int syncPhase = R.string.sync_phase_journals;
                String title = getContext().getString(R.string.sync_error_calendar, account.name);

                notificationManager.setThrowable(e);

                final Intent detailsIntent = notificationManager.getDetailsIntent();
                detailsIntent.putExtra(KEY_ACCOUNT, account);
                if (!(e instanceof Exceptions.UnauthorizedException)) {
                    detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority);
                    detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase);
                }

                notificationManager.notify(title, getContext().getString(syncPhase));
            }

            App.log.info("Calendar sync complete");
        }

        private void updateLocalCalendars(ContentProviderClient provider, Account account, AccountSettings settings) throws CalendarStorageException {
            EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();
            ServiceEntity service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.CALENDAR);

            Map<String, CollectionInfo> remote = new HashMap<>();
            List<CollectionInfo> remoteCollections = JournalEntity.getCollections(data, service);
            for (CollectionInfo info : remoteCollections) {
                remote.put(info.uid, info);
            }

            LocalCalendar[] local = (LocalCalendar[]) LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, null, null);

            boolean updateColors = settings.getManageCalendarColors();

            // delete obsolete local calendar
            for (LocalCalendar calendar : local) {
                String url = calendar.getName();
                if (!remote.containsKey(url)) {
                    App.log.fine("Deleting obsolete local calendar " + url);
                    calendar.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    CollectionInfo info = remote.get(url);
                    App.log.fine("Updating local calendar " + url + " with " + info);
                    calendar.update(info, updateColors);
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local calendars
            for (String url : remote.keySet()) {
                CollectionInfo info = remote.get(url);
                App.log.info("Adding local calendar list " + info);
                LocalCalendar.create(account, provider, info);
            }
        }
    }

}
