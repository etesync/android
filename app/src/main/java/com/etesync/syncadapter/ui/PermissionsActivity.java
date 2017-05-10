/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;

import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.resource.LocalTaskList;

public class PermissionsActivity extends BaseActivity {
    final static private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    public static final String
            PERMISSION_READ_TASKS = "org.dmfs.permission.READ_TASKS",
            PERMISSION_WRITE_TASKS = "org.dmfs.permission.WRITE_TASKS";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
}

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    protected void refresh() {
        boolean noCalendarPermissions =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED;
        findViewById(R.id.calendar_permissions).setVisibility(noCalendarPermissions ? View.VISIBLE : View.GONE);

        boolean noContactsPermissions =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED;
        findViewById(R.id.contacts_permissions).setVisibility(noContactsPermissions ? View.VISIBLE : View.GONE);

        boolean noTaskPermissions;
        if (LocalTaskList.tasksProviderAvailable(this)) {
            noTaskPermissions =
                    ActivityCompat.checkSelfPermission(this, PERMISSION_READ_TASKS) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, PERMISSION_WRITE_TASKS) != PackageManager.PERMISSION_GRANTED;
            findViewById(R.id.opentasks_permissions).setVisibility(noTaskPermissions ? View.VISIBLE : View.GONE);
        } else {
            findViewById(R.id.opentasks_permissions).setVisibility(View.GONE);
            noTaskPermissions = false;
        }

        if (!noCalendarPermissions && !noContactsPermissions && !noTaskPermissions) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(this);
            nm.cancel(Constants.NOTIFICATION_PERMISSIONS);

            finish();
        }
    }

    public void requestCalendarPermissions(View v) {
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, 0);
    }

    public void requestContactsPermissions(View v) {
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
        }, 0);
    }

    public void requestOpenTasksPermissions(View v) {
        ActivityCompat.requestPermissions(this, new String[] {
                PERMISSION_READ_TASKS,
                PERMISSION_WRITE_TASKS
        }, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    public static void requestAllPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[] {
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
        }, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
    }
}
