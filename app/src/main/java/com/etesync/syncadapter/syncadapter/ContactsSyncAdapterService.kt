/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.*
import android.os.Bundle
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.InvalidAccountException
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.ui.DebugInfoActivity
import okhttp3.HttpUrl

class ContactsSyncAdapterService : SyncAdapterService() {

    override fun syncAdapter(): AbstractThreadedSyncAdapter {
        return ContactsSyncAdapter(this)
    }


    private class ContactsSyncAdapter(context: Context) : SyncAdapterService.SyncAdapter(context) {
        override val syncErrorTitle = R.string.sync_error_contacts
        override val notificationManager = SyncNotification(context, "journals-contacts", Constants.NOTIFICATION_CONTACTS_SYNC)

        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            val addressBook = LocalAddressBook(context, account, provider)

            val settings: AccountSettings
            try {
                settings = AccountSettings(context, addressBook.mainAccount)
            } catch (e: InvalidAccountException) {
                Logger.log.info("Skipping sync due to invalid account.")
                Logger.log.info(e.localizedMessage)
                return
            } catch (e: ContactsStorageException) {
                Logger.log.info("Skipping sync due to invalid account.")
                Logger.log.info(e.localizedMessage)
                return
            }

            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                return

            Logger.log.info("Synchronizing address book: " + addressBook.url)
            Logger.log.info("Taking settings from: " + addressBook.mainAccount)

            val principal = HttpUrl.get(settings.uri!!)!!
            ContactsSyncManager(context, account, settings, extras, authority, provider, syncResult, addressBook, principal).use {
                it.performSync()
            }

            Logger.log.info("Contacts sync complete")
        }
    }

}
