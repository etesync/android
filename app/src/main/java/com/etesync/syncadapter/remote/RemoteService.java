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
import android.util.Log;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.IEteSyncService;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.EntryEntity;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;
import com.etesync.syncadapter.model.ServiceEntity;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

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

        public Journal[] getJournals(String journalType) throws RemoteException {
            if (!mApiPermissionHelper.isAllowedIgnoreErrors(journalType)) return null;

            EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();
            List<JournalEntity> journals = data.select(JournalEntity.class).where((JournalEntity.DELETED.eq(false))).get().toList();
            Journal ret[] = new Journal[journals.size()];
            int i = 0;
            for (JournalEntity journal : journals) {
                if (!journal.getInfo().isOfTypeService(journalType)) {
                    continue;
                }
                ret[i] = new Journal(journal.getUid());
                i++;
            }

            return Arrays.copyOf(ret, i);
        }

        public JournalEntry[] getJournalEntries(String journalUid, String lastUid) throws RemoteException {
            EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();
            JournalEntity journal = data.select(JournalEntity.class).where((JournalEntity.DELETED.eq(false)).and(JournalEntity.UID.eq(journalUid))).limit(1).get().firstOrNull();
            // FIXME: Should return a proper error
            if (journal == null) return null;
            // FIXME: Should support generic type
            if (!mApiPermissionHelper.isAllowedIgnoreErrors(journal.getInfo().type.toString())) return null;

            List<EntryEntity> entries = data.select(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journal)).orderBy(EntryEntity.ID.desc()).get().toList();

            JournalEntry ret[] = new JournalEntry[entries.size()];
            int i = 0;
            for (EntryEntity entry : entries) {
                ret[i] = new JournalEntry(entry.getUid());
                ret[i].content = entry.getContent().toJson();
                i++;
            }

            return ret;
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
