/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.lang.ref.WeakReference
import java.util.*

class AccountUpdateService : Service() {

    private val binder = InfoBinder()

    private val runningRefresh = HashSet<Long>()
    private val refreshingStatusListeners = LinkedList<WeakReference<RefreshingStatusListener>>()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action

            when (action) {
            }
        }

        return Service.START_NOT_STICKY
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    interface RefreshingStatusListener {
        fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean)
    }

    inner class InfoBinder : Binder() {
        fun isRefreshing(id: Long): Boolean {
            return runningRefresh.contains(id)
        }

        fun addRefreshingStatusListener(listener: RefreshingStatusListener, callImmediate: Boolean) {
            refreshingStatusListeners.add(WeakReference(listener))
            if (callImmediate)
                for (id in runningRefresh)
                    listener.onDavRefreshStatusChanged(id, true)
        }

        fun removeRefreshingStatusListener(listener: RefreshingStatusListener) {
            val iterator = refreshingStatusListeners.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next().get()
                if (listener == item)
                    iterator.remove()
            }
        }
    }

}
