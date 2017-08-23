/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.remote;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.etesync.syncadapter.IEteSyncService;
import com.etesync.syncadapter.model.CollectionInfo;

public class RemoteService extends Service {

    private ApiPermissionHelper mApiPermissionHelper;

    private final IEteSyncService.Stub mBinder = new IEteSyncService.Stub() {
        @Override
        public boolean hasPermission(String journalType) throws RemoteException {
            if (journalType == null || journalType.isEmpty()) return false;
            return mApiPermissionHelper.isAllowedIgnoreErrors(journalType);
        }

        @Override
        public void requestPermission(String journalType) throws RemoteException {
            if (journalType == null || journalType.isEmpty()) return;
            if (mApiPermissionHelper.isAllowedIgnoreErrors(journalType)) return;

            RemoteRegisterActivity.startActivity(RemoteService.this,
                    mApiPermissionHelper.getCurrentCallingPackage(), journalType);
        }

        public CollectionInfo[] getJournalEntries(String journalType) throws RemoteException {
            if (!mApiPermissionHelper.isAllowedIgnoreErrors(journalType)) return null;

            return new CollectionInfo[0]; //todo implement
        }

        //todo - query journals?
        //todo - add insert. - returns uid
        //todo add update?
        //todo create journal
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mApiPermissionHelper = new ApiPermissionHelper(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
