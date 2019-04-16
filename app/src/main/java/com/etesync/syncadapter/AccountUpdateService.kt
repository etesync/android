/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.ServiceEntity
import com.etesync.syncadapter.resource.LocalAddressBook
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

class AccountUpdateService : Service() {

    private val binder = InfoBinder()

    private val runningRefresh = HashSet<Long>()
    private val refreshingStatusListeners = LinkedList<WeakReference<RefreshingStatusListener>>()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action

            when (action) {
                ACTION_ACCOUNTS_UPDATED -> cleanupAccounts()
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


    /* ACTION RUNNABLES
       which actually do the work
     */

    @SuppressLint("MissingPermission")
    internal fun cleanupAccounts() {
        Logger.log.info("Cleaning up orphaned accounts")

        val accountNames = LinkedList<String>()
        val am = AccountManager.get(this)
        for (account in am.getAccountsByType(getString(R.string.account_type))) {
            accountNames.add(account.name)
        }

        val data = (application as App).data

        // delete orphaned address book accounts
        for (addrBookAccount in am.getAccountsByType(getString(R.string.account_type_address_book))) {
            val addressBook = LocalAddressBook(this, addrBookAccount, null)
            try {
                if (!accountNames.contains(addressBook.mainAccount.name))
                    addressBook.delete()
            } catch (e: ContactsStorageException) {
                Logger.log.log(Level.SEVERE, "Couldn't get address book main account", e)
            }

        }


        if (accountNames.isEmpty()) {
            data.delete(ServiceEntity::class.java).get().value()
        } else {
            data.delete(ServiceEntity::class.java).where(ServiceEntity.ACCOUNT.notIn(accountNames)).get().value()
        }
    }

    companion object {

        val ACTION_ACCOUNTS_UPDATED = "accountsUpdated"
    }
}
