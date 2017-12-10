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
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.journalmanager.JournalEntryManager;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.SyncEntry;
import com.etesync.syncadapter.resource.LocalResource;
import com.etesync.syncadapter.resource.LocalTask;
import com.etesync.syncadapter.resource.LocalTaskList;

import org.apache.commons.codec.Charsets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.ical4android.Task;
import at.bitfire.ical4android.TaskProvider;
import at.bitfire.vcard4android.ContactsStorageException;
import okhttp3.HttpUrl;

public class TasksSyncManager extends SyncManager {
    final private HttpUrl remote;

    protected static final int MAX_MULTIGET = 30;

    final protected TaskProvider provider;


    public TasksSyncManager(Context context, Account account, AccountSettings settings, Bundle extras, String authority, TaskProvider provider, SyncResult result, LocalTaskList taskList, HttpUrl remote) throws InvalidAccountException, Exceptions.IntegrityException, Exceptions.GenericCryptoException {
        super(context, account, settings, extras, authority, result, taskList.getName(), CollectionInfo.Type.TASK_LIST, account.name);
        this.provider = provider;
        localCollection = taskList;
        this.remote = remote;
    }

    @Override
    protected int notificationId() {
        return Constants.NOTIFICATION_TASK_SYNC;
    }

    @Override
    protected String getSyncErrorTitle() {
        return context.getString(R.string.sync_error_tasks, account.name);
    }

    @Override
    protected String getSyncSuccessfullyTitle() {
        return context.getString(R.string.sync_successfully_tasks, info.displayName,
                account.name);
    }

    @Override
    protected boolean prepare() throws CalendarStorageException, ContactsStorageException {
        if (!super.prepare())
            return false;

        journal = new JournalEntryManager(httpClient, remote, localTaskList().getName());
        return true;
    }

    @Override
    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        super.prepareDirty();
    }

    // helpers

    private LocalTaskList localTaskList() { return ((LocalTaskList)localCollection); }

    protected void processSyncEntry(SyncEntry cEntry) throws IOException, ContactsStorageException, CalendarStorageException, InvalidCalendarException {
        InputStream is = new ByteArrayInputStream(cEntry.getContent().getBytes(Charsets.UTF_8));

        Task[] tasks = Task.fromStream(is, Charsets.UTF_8);
        if (tasks.length == 0) {
            App.log.warning("Received VCard without data, ignoring");
            return;
        } else if (tasks.length > 1)
            App.log.warning("Received multiple VCALs, using first one");

        Task task = tasks[0];
        LocalTask local = (LocalTask) localCollection.getByUid(task.uid);

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processTask(task, local);
        } else {
            App.log.info("Removing local record #" + local.getId() + " which has been deleted on the server");
            local.delete();
        }
    }

    private LocalResource processTask(final Task newData, LocalTask localTask) throws IOException, ContactsStorageException, CalendarStorageException {
        // delete local event, if it exists
        if (localTask != null) {
            App.log.info("Updating " + newData.uid + " in local calendar");
            localTask.setETag(newData.uid);
            localTask.update(newData);
            syncResult.stats.numUpdates++;
        } else {
            App.log.info("Adding " + newData.uid + " to local calendar");
            localTask = new LocalTask(localTaskList(), newData, newData.uid, newData.uid);
            localTask.add();
            syncResult.stats.numInserts++;
        }

        return localTask;
    }
}
