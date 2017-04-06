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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.etesync.syncadapter.model.CollectionInfo;

import org.dmfs.provider.tasks.TaskContract.TaskLists;
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.AndroidTaskList;
import at.bitfire.ical4android.AndroidTaskListFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class LocalTaskList extends AndroidTaskList implements LocalCollection {

    public static final int defaultColor = 0xFFC3EA6E;     // "DAVdroid green"

    public static final String COLUMN_CTAG = TaskLists.SYNC_VERSION;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Tasks._ID,
            Tasks._SYNC_ID,
            LocalTask.COLUMN_ETAG
    };


    @Override
    protected String[] taskBaseInfoColumns() {
        return BASE_INFO_COLUMNS;
    }


    protected LocalTaskList(Account account, TaskProvider provider, long id) {
        super(account, provider, LocalTask.Factory.INSTANCE, id);
    }

    public static Uri create(Account account, TaskProvider provider, CollectionInfo info) throws CalendarStorageException {
        ContentValues values = valuesFromCollectionInfo(info, true);
        values.put(TaskLists.OWNER, account.name);
        values.put(TaskLists.SYNC_ENABLED, 1);
        values.put(TaskLists.VISIBLE, 1);
        return create(account, provider, values);
    }

    public void update(CollectionInfo info, boolean updateColor) throws CalendarStorageException {
        update(valuesFromCollectionInfo(info, updateColor));
    }

    private static ContentValues valuesFromCollectionInfo(CollectionInfo info, boolean withColor) {
        ContentValues values = new ContentValues();
        values.put(TaskLists._SYNC_ID, info.uid);
        values.put(TaskLists.LIST_NAME, info.displayName);

        if (withColor)
            values.put(TaskLists.LIST_COLOR, info.color != null ? info.color : defaultColor);

        return values;
    }

    @Override
    public LocalTask[] getDeleted() throws CalendarStorageException {
        return (LocalTask[])queryTasks(Tasks._DELETED + "!=0", null);
    }

    @Override
    public LocalTask[] getWithoutFileName() throws CalendarStorageException {
        return (LocalTask[])queryTasks(Tasks._SYNC_ID + " IS NULL", null);
    }

    @Override
    public LocalTask getByUid(String uid) throws CalendarStorageException {
        LocalTask[] ret = (LocalTask[]) queryTasks(Tasks._SYNC_ID + " =? ", new String[]{uid});
        if (ret != null && ret.length > 0) {
            return ret[0];
        }
        return null;
    }

    @Override
    public LocalResource[] getDirty() throws CalendarStorageException, FileNotFoundException {
        LocalTask[] tasks = (LocalTask[])queryTasks(Tasks._DIRTY + "!=0 AND " + Tasks._DELETED + "== 0", null);
        if (tasks != null)
        for (LocalTask task : tasks) {
            if (task.getTask().sequence == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.getTask().sequence = 0;
            else
                task.getTask().sequence++;
        }
        return tasks;
    }

    @Override
    public long count() throws CalendarStorageException {
        String where = Tasks.LIST_ID + "=?";
        String whereArgs[] = {String.valueOf(getId())};

        try {
            @Cleanup Cursor cursor = provider.client.query(
                    syncAdapterURI(provider.tasksUri()),
                    null,
                    where, whereArgs, null);
            return cursor.getCount();
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }
    }

    // helpers

    public static boolean tasksProviderAvailable(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return context.getPackageManager().resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null;
        else {
            @Cleanup TaskProvider provider = TaskProvider.acquire(context.getContentResolver(), TaskProvider.ProviderName.OpenTasks);
            return provider != null;
        }
    }


    public static class Factory implements AndroidTaskListFactory {
        public static final Factory INSTANCE = new Factory();

        @Override
        public AndroidTaskList newInstance(Account account, TaskProvider provider, long id) {
            return new LocalTaskList(account, provider, id);
        }

        @Override
        public AndroidTaskList[] newArray(int size) {
            return new LocalTaskList[size];
        }
    }


    // HELPERS

    public static void onRenameAccount(@NonNull ContentResolver resolver, @NonNull String oldName, @NonNull String newName) throws RemoteException {
        @Cleanup("release") ContentProviderClient client = resolver.acquireContentProviderClient(TaskProvider.ProviderName.OpenTasks.authority);
        if (client != null) {
            ContentValues values = new ContentValues(1);
            values.put(Tasks.ACCOUNT_NAME, newName);
            client.update(Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), values, Tasks.ACCOUNT_NAME + "=?", new String[]{oldName});
        }
    }

}
