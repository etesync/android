/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.LocalTaskList

class PermissionsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    protected fun refresh() {
        val noCalendarPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.calendar_permissions).visibility = if (noCalendarPermissions) View.VISIBLE else View.GONE

        val noContactsPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.contacts_permissions).visibility = if (noContactsPermissions) View.VISIBLE else View.GONE

        val noTaskPermissions: Boolean
        if (LocalTaskList.tasksProviderAvailable(this)) {
            noTaskPermissions = ActivityCompat.checkSelfPermission(this, PERMISSION_READ_TASKS) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, PERMISSION_WRITE_TASKS) != PackageManager.PERMISSION_GRANTED
            findViewById<View>(R.id.opentasks_permissions).visibility = if (noTaskPermissions) View.VISIBLE else View.GONE
        } else {
            findViewById<View>(R.id.opentasks_permissions).visibility = View.GONE
            noTaskPermissions = false
        }

        if (!noCalendarPermissions && !noContactsPermissions && !noTaskPermissions) {
            val nm = NotificationManagerCompat.from(this)
            nm.cancel(Constants.NOTIFICATION_PERMISSIONS)

            finish()
        }
    }

    fun requestCalendarPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 0)
    }

    fun requestContactsPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), 0)
    }

    fun requestOpenTasksPermissions(v: View) {
        ActivityCompat.requestPermissions(this, arrayOf(PERMISSION_READ_TASKS, PERMISSION_WRITE_TASKS), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    companion object {
        private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124

        val PERMISSION_READ_TASKS = "org.dmfs.permission.READ_TASKS"
        val PERMISSION_WRITE_TASKS = "org.dmfs.permission.WRITE_TASKS"

        fun requestAllPermissions(activity: Activity) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, PERMISSION_READ_TASKS, PERMISSION_WRITE_TASKS), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)
        }
    }
}
