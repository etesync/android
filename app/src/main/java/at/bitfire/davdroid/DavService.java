/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.davdroid.journalmanager.Exceptions;
import at.bitfire.davdroid.journalmanager.JournalManager;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class DavService extends Service {

    public static final String
            ACTION_ACCOUNTS_UPDATED = "accountsUpdated",
            ACTION_REFRESH_COLLECTIONS = "refreshCollections",
            EXTRA_DAV_SERVICE_ID = "davServiceID";

    private final IBinder binder = new InfoBinder();

    private final Set<Long> runningRefresh = new HashSet<>();
    private final List<WeakReference<RefreshingStatusListener>> refreshingStatusListeners = new LinkedList<>();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            long id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1);

            switch (action) {
                case ACTION_ACCOUNTS_UPDATED:
                    cleanupAccounts();
                    break;
                case ACTION_REFRESH_COLLECTIONS:
                    if (runningRefresh.add(id)) {
                        new Thread(new RefreshCollections(id)).start();
                        for (WeakReference<RefreshingStatusListener> ref : refreshingStatusListeners) {
                            RefreshingStatusListener listener = ref.get();
                            if (listener != null)
                                listener.onDavRefreshStatusChanged(id, true);
                        }
                    }
                    break;
            }
        }

        return START_NOT_STICKY;
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface RefreshingStatusListener {
        void onDavRefreshStatusChanged(long id, boolean refreshing);
    }

    public class InfoBinder extends Binder {
        public boolean isRefreshing(long id) {
            return runningRefresh.contains(id);
        }

        public void addRefreshingStatusListener(@NonNull RefreshingStatusListener listener, boolean callImmediate) {
            refreshingStatusListeners.add(new WeakReference<>(listener));
            if (callImmediate)
                for (long id : runningRefresh)
                    listener.onDavRefreshStatusChanged(id, true);
        }

        public void removeRefreshingStatusListener(@NonNull RefreshingStatusListener listener) {
            for (Iterator<WeakReference<RefreshingStatusListener>> iterator = refreshingStatusListeners.iterator(); iterator.hasNext(); ) {
                RefreshingStatusListener item = iterator.next().get();
                if (listener.equals(item))
                    iterator.remove();
            }
        }
    }


    /* ACTION RUNNABLES
       which actually do the work
     */

    @SuppressLint("MissingPermission")
    void cleanupAccounts() {
        App.log.info("Cleaning up orphaned accounts");

        final OpenHelper dbHelper = new OpenHelper(this);
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            List<String> sqlAccountNames = new LinkedList<>();
            AccountManager am = AccountManager.get(this);
            for (Account account : am.getAccountsByType(Constants.ACCOUNT_TYPE))
                sqlAccountNames.add(DatabaseUtils.sqlEscapeString(account.name));

            if (sqlAccountNames.isEmpty())
                db.delete(Services._TABLE, null, null);
            else
                db.delete(Services._TABLE, Services.ACCOUNT_NAME + " NOT IN (" + TextUtils.join(",", sqlAccountNames) + ")", null);
        } finally {
            dbHelper.close();
        }
    }

    private class RefreshCollections implements Runnable {
        final long service;
        final OpenHelper dbHelper;

        RefreshCollections(long davServiceId) {
            this.service = davServiceId;
            dbHelper = new OpenHelper(DavService.this);
        }

        @Override
        public void run() {
            Account account = null;

            try {
                @Cleanup SQLiteDatabase db = dbHelper.getWritableDatabase();

                String serviceType = dbHelper.getServiceType(db, service);
                App.log.info("Refreshing " + serviceType + " collections of service #" + service);

                // get account
                account = dbHelper.getServiceAccount(db, service);

                OkHttpClient httpClient = HttpClient.create(DavService.this, account);

                AccountSettings settings = new AccountSettings(DavService.this, account);
                JournalManager journalsManager = new JournalManager(httpClient, HttpUrl.get(settings.getUri()));

                List<CollectionInfo> collections = new LinkedList<>();

                for (JournalManager.Journal journal : journalsManager.getJournals(settings.password())) {
                    CollectionInfo info = CollectionInfo.fromJson(journal.getContent(settings.password()));
                    info.url = journal.getUuid();
                    if (info.isOfTypeService(serviceType)) {
                        collections.add(info);
                    }
                }

                // FIXME: handle deletion from server

                if (collections.isEmpty()) {
                    CollectionInfo info = CollectionInfo.defaultForService(serviceType);
                    JournalManager.Journal journal = new JournalManager.Journal(settings.password(), info.toJson());
                    journalsManager.putJournal(journal);
                    info.url = journal.getUuid();
                    collections.add(info);
                }

                db.beginTransactionNonExclusive();
                try {
                    saveCollections(db, collections);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

            } catch (InvalidAccountException e) {
                // FIXME: Do something
                e.printStackTrace();
            } catch (Exceptions.HttpException e) {
                // FIXME: do something
                e.printStackTrace();
            } catch (Exceptions.IntegrityException e) {
                // FIXME: do something
                e.printStackTrace();
            } finally {
                dbHelper.close();

                runningRefresh.remove(service);
                for (WeakReference<RefreshingStatusListener> ref : refreshingStatusListeners) {
                    RefreshingStatusListener listener = ref.get();
                    if (listener != null)
                        listener.onDavRefreshStatusChanged(service, false);
                }
            }
        }

        @NonNull
        private Map<String, CollectionInfo> readCollections(SQLiteDatabase db) {
            Map<String, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(Collections._TABLE, null, Collections.SERVICE_ID + "=?", new String[]{String.valueOf(service)}, null, null, null);
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                collections.put(values.getAsString(Collections.URL), CollectionInfo.fromDB(values));
            }
            return collections;
        }

        private void saveCollections(SQLiteDatabase db, Iterable<CollectionInfo> collections) {
            db.delete(Collections._TABLE, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(service)});
            for (CollectionInfo collection : collections) {
                ContentValues values = collection.toDB();
                App.log.log(Level.FINE, "Saving collection", values);
                values.put(Collections.SERVICE_ID, service);
                db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

}
