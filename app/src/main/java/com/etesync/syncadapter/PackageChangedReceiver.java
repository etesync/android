/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.etesync.syncadapter.resource.LocalTaskList;

import at.bitfire.ical4android.TaskProvider;

public class PackageChangedReceiver extends BroadcastReceiver {

    @Override
    @SuppressLint("MissingPermission")
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) ||
            Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction()))
            updateTaskSync(context);
    }

    static void updateTaskSync(@NonNull Context context) {
        boolean tasksInstalled = LocalTaskList.tasksProviderAvailable(context);
        App.log.info("Package (un)installed; OpenTasks provider now available = " + tasksInstalled);

        // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
        AccountManager am = AccountManager.get(context);
        for (Account account : am.getAccountsByType(App.getAccountType())) {
            if (tasksInstalled) {
                if (ContentResolver.getIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority) <= 0) {
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1);
                    ContentResolver.setSyncAutomatically(account, TaskProvider.ProviderName.OpenTasks.authority, true);
                    ContentResolver.addPeriodicSync(account, TaskProvider.ProviderName.OpenTasks.authority, new Bundle(), Constants.DEFAULT_SYNC_INTERVAL);
                }
            } else {
                ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0);
            }
        }
    }
}
