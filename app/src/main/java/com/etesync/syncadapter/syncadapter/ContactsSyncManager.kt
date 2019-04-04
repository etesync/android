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
import android.provider.ContactsContract
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalEntryManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.SyncEntry
import com.etesync.syncadapter.resource.LocalAddress
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalContact
import com.etesync.syncadapter.resource.LocalGroup
import okhttp3.HttpUrl
import okhttp3.Request
import org.apache.commons.collections4.SetUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.io.StringReader
import java.util.logging.Level

/**
 *
 * Synchronization manager for CardDAV collections; handles contacts and groups.
 */
class ContactsSyncManager @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class, ContactsStorageException::class)
constructor(context: Context, account: Account, settings: AccountSettings, extras: Bundle, authority: String, private val provider: ContentProviderClient, result: SyncResult, localAddressBook: LocalAddressBook, private val remote: HttpUrl) : SyncManager<LocalAddress>(context, account, settings, extras, authority, result, localAddressBook.url, CollectionInfo.Type.ADDRESS_BOOK, localAddressBook.mainAccount.name) {
    private val resourceDownloader: ResourceDownloader

    protected override val syncErrorTitle: String
        get() = context.getString(R.string.sync_error_contacts, account.name)

    protected override val syncSuccessfullyTitle: String
        get() = context.getString(R.string.sync_successfully_contacts, account.name)

    init {
        localCollection = localAddressBook
        resourceDownloader = ResourceDownloader(context)
    }

    override fun notificationId(): Int {
        return Constants.NOTIFICATION_CONTACTS_SYNC
    }

    @Throws(ContactsStorageException::class, CalendarStorageException::class)
    override fun prepare(): Boolean {
        if (!super.prepare())
            return false
        val localAddressBook = localAddressBook()

        if (LocalContact.HASH_HACK) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val reallyDirty = localAddressBook.verifyDirty()
            val deleted = localAddressBook.findDeleted().size
            if (extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD) && reallyDirty == 0 && deleted == 0) {
                Logger.log.info("This sync was called to up-sync dirty/deleted contacts, but no contacts have been changed")
                return false
            }
        }

        journal = JournalEntryManager(httpClient.okHttpClient, remote, localAddressBook.url)

        return true
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    override fun prepareDirty() {
        super.prepareDirty()

        val addressBook = localAddressBook()

        /* groups as separate VCards: there are group contacts and individual contacts */

        // mark groups with changed members as dirty
        val batch = BatchOperation(addressBook.provider!!)
        for (contact in addressBook.findDirtyContacts()) {
            try {
                Logger.log.fine("Looking for changed group memberships of contact " + contact.fileName)
                val cachedGroups = contact.getCachedGroupMemberships()
                val currentGroups = contact.getGroupMemberships()
                for (groupID in SetUtils.disjunction(cachedGroups, currentGroups)) {
                    Logger.log.fine("Marking group as dirty: " + groupID!!)
                    batch.enqueue(BatchOperation.Operation(
                            ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupID)))
                                    .withValue(ContactsContract.Groups.DIRTY, 1)
                                    .withYieldAllowed(true)
                    ))
                }
            } catch (ignored: FileNotFoundException) {
            }

        }
        batch.commit()
    }

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    override fun postProcess() {
        super.postProcess()
        /* VCard4 group handling: there are group contacts and individual contacts */
        Logger.log.info("Assigning memberships of downloaded contact groups")
        LocalGroup.applyPendingMemberships(localAddressBook())
    }


    // helpers

    private fun localAddressBook(): LocalAddressBook {
        return localCollection as LocalAddressBook
    }

    @Throws(IOException::class, ContactsStorageException::class, CalendarStorageException::class)
    override fun processSyncEntry(cEntry: SyncEntry) {
        val inputReader = StringReader(cEntry.content)

        val contacts = Contact.fromReader(inputReader, resourceDownloader)
        if (contacts.size == 0) {
            Logger.log.warning("Received VCard without data, ignoring")
            return
        } else if (contacts.size > 1)
            Logger.log.warning("Received multiple VCards, using first one")

        val contact = contacts[0]
        val local = localCollection!!.findByUid(contact.uid!!)

        if (cEntry.isAction(SyncEntry.Actions.ADD) || cEntry.isAction(SyncEntry.Actions.CHANGE)) {
            processContact(contact, local)
        } else {
            if (local != null) {
                Logger.log.info("Removing local record which has been deleted on the server")
                local.delete()
            } else {
                Logger.log.warning("Tried deleting a non-existent record: " + contact.uid)
            }
        }
    }

    @Throws(IOException::class, ContactsStorageException::class)
    private fun processContact(newData: Contact, _local: LocalAddress?): LocalAddress {
        var local = _local
        val uuid = newData.uid
        // update local contact, if it exists
        if (local != null) {
            Logger.log.log(Level.INFO, "Updating $uuid in local address book")

            if (local is LocalGroup && newData.group) {
                // update group
                val group: LocalGroup = local
                group.eTag = uuid
                group.update(newData)
                syncResult.stats.numUpdates++

            } else if (local is LocalContact && !newData.group) {
                // update contact
                val contact: LocalContact = local
                contact.eTag = uuid
                contact.update(newData)
                syncResult.stats.numUpdates++

            } else {
                // group has become an individual contact or vice versa
                try {
                    local.delete()
                    local = null
                } catch (e: CalendarStorageException) {
                    // CalendarStorageException is not used by LocalGroup and LocalContact
                }

            }
        }

        if (local == null) {
            if (newData.group) {
                Logger.log.log(Level.INFO, "Creating local group", newData.uid)
                val group = LocalGroup(localAddressBook(), newData, uuid, uuid)
                group.add()

                local = group
            } else {
                Logger.log.log(Level.INFO, "Creating local contact", newData.uid)
                val contact = LocalContact(localAddressBook(), newData, uuid, uuid)
                contact.add()

                local = contact
            }
            syncResult.stats.numInserts++
        }

        if (LocalContact.HASH_HACK && local is LocalContact)
        // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            local.updateHashCode(null)

        return local
    }

    // downloader helper class

    class ResourceDownloader(internal var context: Context) : Contact.Downloader {

        override fun download(url: String, accepts: String): ByteArray? {
            val httpUrl = HttpUrl.parse(url)

            if (httpUrl == null) {
                Logger.log.log(Level.SEVERE, "Invalid external resource URL", url)
                return null
            }

            val host = httpUrl.host()
            if (host == null) {
                Logger.log.log(Level.SEVERE, "External resource URL doesn't specify a host name", url)
                return null
            }

            val resourceClient = HttpClient.Builder(context).setForeground(false).build().okHttpClient

            try {
                val response = resourceClient.newCall(Request.Builder()
                        .get()
                        .url(httpUrl)
                        .build()).execute()

                val body = response.body()
                if (body != null) {
                    return body.bytes()
                }
            } catch (e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't download external resource", e)
            }

            return null
        }
    }

}
