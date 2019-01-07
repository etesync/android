/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import java.util.LinkedList

class AccountsChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION == intent.action) {
            val serviceIntent = Intent(context, AccountUpdateService::class.java)
            serviceIntent.action = AccountUpdateService.ACTION_ACCOUNTS_UPDATED
            try {
                context.startService(serviceIntent)
            } catch (e: IllegalStateException) {
                App.log.warning("Got an illegal state exception! Ignoring...")
            }

            for (listener in listeners)
                listener.onAccountsUpdated(null)
        }
    }

    companion object {

        protected val listeners: MutableList<OnAccountsUpdateListener> = LinkedList()

        fun registerListener(listener: OnAccountsUpdateListener, callImmediately: Boolean) {
            listeners.add(listener)
            if (callImmediately)
                listener.onAccountsUpdated(null)
        }

        fun unregisterListener(listener: OnAccountsUpdateListener) {
            listeners.remove(listener)
        }
    }

}
