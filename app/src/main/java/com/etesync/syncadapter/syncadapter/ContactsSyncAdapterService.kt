/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle

import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.InvalidAccountException
import com.etesync.syncadapter.NotificationHelper
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.model.ServiceEntity
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.ui.DebugInfoActivity

import java.util.logging.Level

import at.bitfire.vcard4android.ContactsStorageException
import io.requery.Persistable
import io.requery.sql.EntityDataStore
import okhttp3.HttpUrl

import com.etesync.syncadapter.Constants.KEY_ACCOUNT

class ContactsSyncAdapterService : SyncAdapterService() {

    override fun syncAdapter(): AbstractThreadedSyncAdapter {
        return ContactsSyncAdapter(this)
    }


    private class ContactsSyncAdapter(context: Context) : SyncAdapterService.SyncAdapter(context) {

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult)
            val notificationManager = NotificationHelper(context, "journals-contacts", Constants.NOTIFICATION_CONTACTS_SYNC)
            notificationManager.cancel()

            try {
                val addressBook = LocalAddressBook(context, account, provider)

                val settings: AccountSettings
                try {
                    settings = AccountSettings(context, addressBook.mainAccount)
                } catch (e: InvalidAccountException) {
                    App.log.info("Skipping sync due to invalid account.")
                    App.log.info(e.localizedMessage)
                    return
                } catch (e: ContactsStorageException) {
                    App.log.info("Skipping sync due to invalid account.")
                    App.log.info(e.localizedMessage)
                    return
                }

                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return

                App.log.info("Synchronizing address book: " + addressBook.url)
                App.log.info("Taking settings from: " + addressBook.mainAccount)

                val principal = HttpUrl.get(settings.uri!!)!!
                val syncManager = ContactsSyncManager(context, account, settings, extras, authority, provider, syncResult, addressBook, principal)
                syncManager.performSync()
            } catch (e: Exception) {
                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(R.string.sync_error_contacts, account.name)

                notificationManager.setThrowable(e)

                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(KEY_ACCOUNT, account)
                if (e !is Exceptions.UnauthorizedException) {
                    detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
                    detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase)
                }
                notificationManager.notify(title, context.getString(syncPhase))
            } catch (e: OutOfMemoryError) {
                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(R.string.sync_error_contacts, account.name)
                notificationManager.setThrowable(e)
                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(KEY_ACCOUNT, account)
                notificationManager.notify(title, context.getString(syncPhase))
            }

            App.log.info("Contacts sync complete")
        }
    }

}
