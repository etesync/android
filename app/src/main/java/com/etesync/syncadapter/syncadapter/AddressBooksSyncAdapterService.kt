/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.*
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.provider.ContactsContract
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.ui.DebugInfoActivity
import java.io.IOException
import java.util.*
import java.util.logging.Level

class AddressBooksSyncAdapterService : SyncAdapterService() {

    override fun syncAdapter(): AbstractThreadedSyncAdapter {
        return AddressBooksSyncAdapter(this)
    }


    private class AddressBooksSyncAdapter(context: Context) : SyncAdapterService.SyncAdapter(context) {
        override val syncErrorTitle = R.string.sync_error_contacts
        override val notificationManager = SyncNotification(context, "journals-contacts", Constants.NOTIFICATION_CONTACTS_SYNC)

        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            val contactsProvider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
            if (contactsProvider == null) {
                Logger.log.severe("Couldn't access contacts provider")
                syncResult.databaseError = true
                return
            }

            val settings = AccountSettings(context, account)
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                return

            RefreshCollections(account, CollectionInfo.Type.ADDRESS_BOOK).run()

            updateLocalAddressBooks(contactsProvider, account)

            contactsProvider.release()

            val accountManager = AccountManager.get(context)
            for (addressBookAccount in accountManager.getAccountsByType(App.addressBookAccountType)) {
                Logger.log.log(Level.INFO, "Running sync for address book", addressBookAccount)
                val syncExtras = Bundle(extras)
                syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, syncExtras)
            }

            Logger.log.info("Address book sync complete")
        }


        @Throws(ContactsStorageException::class, AuthenticatorException::class, OperationCanceledException::class, IOException::class)
        private fun updateLocalAddressBooks(provider: ContentProviderClient, account: Account) {
            val context = context
            val data = (getContext().applicationContext as App).data
            val service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.ADDRESS_BOOK)

            val remote = HashMap<String, JournalEntity>()
            val remoteJournals = JournalEntity.getJournals(data, service)
            for (journalEntity in remoteJournals) {
                remote[journalEntity.uid] = journalEntity
            }

            val local = LocalAddressBook.find(context, provider, account)

            // delete obsolete local address books
            for (addressBook in local) {
                val url = addressBook.url
                val journalEntity = remote[url]
                if (journalEntity == null) {
                    Logger.log.fine("Deleting obsolete local address book $url")
                    addressBook.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local address book $url with $journalEntity")
                    addressBook.update(journalEntity)
                    // we already have a local collection for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local address books
            for (url in remote.keys) {
                val journalEntity = remote[url]!!
                Logger.log.info("Adding local address book $journalEntity")
                LocalAddressBook.create(context, provider, account, journalEntity)
            }
        }
    }

}
