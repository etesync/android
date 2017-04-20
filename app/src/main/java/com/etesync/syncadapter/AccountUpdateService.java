/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.database.DatabaseUtils;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;

import com.etesync.syncadapter.model.ServiceEntity;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

public class AccountUpdateService extends Service {

    public static final String
            ACTION_ACCOUNTS_UPDATED = "accountsUpdated";

    private final IBinder binder = new InfoBinder();

    private final Set<Long> runningRefresh = new HashSet<>();
    private final List<WeakReference<RefreshingStatusListener>> refreshingStatusListeners = new LinkedList<>();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_ACCOUNTS_UPDATED:
                    cleanupAccounts();
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

        List<String> sqlAccountNames = new LinkedList<>();
        AccountManager am = AccountManager.get(this);
        for (Account account : am.getAccountsByType(Constants.ACCOUNT_TYPE))
            sqlAccountNames.add(account.name);

        EntityDataStore<Persistable> data = ((App) getApplication()).getData();

        if (sqlAccountNames.isEmpty()) {
            data.delete(ServiceEntity.class).get().value();
        } else {
            data.delete(ServiceEntity.class).where(ServiceEntity.ACCOUNT.notIn(sqlAccountNames)).get().value();
        }
    }
}
