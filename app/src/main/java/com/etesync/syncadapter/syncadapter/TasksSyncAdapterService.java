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
import com.etesync.syncadapter.resource.LocalTaskList;
import com.etesync.syncadapter.ui.DebugInfoActivity;

import org.dmfs.provider.tasks.TaskContract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import lombok.Cleanup;
import okhttp3.HttpUrl;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
public class TasksSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new SyncAdapter(this);
    }


	private static class SyncAdapter extends SyncAdapterService.SyncAdapter {

        public SyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient providerClient, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, providerClient, syncResult);

            NotificationHelper notificationManager = new NotificationHelper(getContext(), "journals-tasks", Constants.NOTIFICATION_CALENDAR_SYNC);
            notificationManager.cancel();

            try {
                @Cleanup TaskProvider provider = TaskProvider.acquire(getContext().getContentResolver(), TaskProvider.ProviderName.OpenTasks);
                if (provider == null)
                    throw new CalendarStorageException("Couldn't access OpenTasks provider");

                AccountSettings settings = new AccountSettings(getContext(), account);
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                new RefreshCollections(account, CollectionInfo.Type.CALENDAR).run();

                updateLocalTaskLists(provider, account, settings);

                HttpUrl principal = HttpUrl.get(settings.getUri());

                for (LocalTaskList taskList : (LocalTaskList[]) LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, TaskContract.TaskLists.SYNC_ENABLED + "!=0", null)) {
                    App.log.info("Synchronizing task list #" + taskList.getId() + " [" + taskList.getSyncId() + "]");
                    TasksSyncManager syncManager = new TasksSyncManager(getContext(), account, settings, extras, authority, provider, syncResult, taskList, principal);
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

            App.log.info("Task sync complete");
        }

        private void updateLocalTaskLists(TaskProvider provider, Account account, AccountSettings settings) throws CalendarStorageException {
            EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();
            ServiceEntity service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.TASK_LIST);

            Map<String, JournalEntity> remote = new HashMap<>();
            List<JournalEntity> remoteJournals = JournalEntity.getJournals(data, service);
            for (JournalEntity journalEntity : remoteJournals) {
                remote.put(journalEntity.getUid(), journalEntity);
            }

            LocalTaskList[] local = (LocalTaskList[])LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, null, null);

            boolean updateColors = settings.getManageCalendarColors();

            // delete obsolete local task lists
            for (LocalTaskList list : local) {
                String url = list.getSyncId();
                if (!remote.containsKey(url)) {
                    App.log.fine("Deleting obsolete local task list" + url);
                    list.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    JournalEntity journalEntity = remote.get(url);
                    App.log.fine("Updating local task list " + url + " with " + journalEntity);
                    list.update(journalEntity, updateColors);
                    // we already have a local task list for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local task lists
            for (String url : remote.keySet()) {
                JournalEntity journalEntity = remote.get(url);
                App.log.info("Adding local task list " + journalEntity);
                LocalTaskList.create(account, provider, journalEntity);
            }
        }
    }

}
