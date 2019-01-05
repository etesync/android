/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.etesync.syncadapter.resource.LocalTaskList

class PackageChangedReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_PACKAGE_ADDED == intent.action || Intent.ACTION_PACKAGE_FULLY_REMOVED == intent.action)
            updateTaskSync(context)
    }

    companion object {

        internal fun updateTaskSync(context: Context) {
            val tasksInstalled = LocalTaskList.tasksProviderAvailable(context)
            App.log.info("Package (un)installed; OpenTasks provider now available = $tasksInstalled")

            // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
            // FIXME: Do something if we ever bring back tasks.
        }
    }

}
