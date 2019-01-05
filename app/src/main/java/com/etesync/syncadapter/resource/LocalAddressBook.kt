/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.support.v4.os.OperationCanceledException

import com.etesync.syncadapter.App
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.utils.AndroidCompat

import java.io.FileNotFoundException
import java.io.IOException
import java.util.Collections
import java.util.HashSet
import java.util.LinkedList
import java.util.logging.Level

import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidGroup
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.ContactsStorageException


class LocalAddressBook(protected val context: Context, account: Account, provider: ContentProviderClient?) : AndroidAddressBook(account, provider, LocalGroup.Factory.INSTANCE, LocalContact.Factory.INSTANCE), LocalCollection<LocalResource> {
    private val syncState = Bundle()

    /**
     * Whether contact groups (LocalGroup resources) are included in query results for
     * [.getDeleted], [.getDirty] and
     * [.getWithoutFileName].
     */
    var includeGroups = true

    /**
     * Returns an array of local contacts/groups which have been deleted locally. (DELETED != 0).
     */
    override val deleted: Array<LocalResource>
        @Throws(ContactsStorageException::class)
        get() {
            val deleted = LinkedList<LocalResource>()
            Collections.addAll(deleted, *deletedContacts)
            if (includeGroups)
                Collections.addAll(deleted, *deletedGroups)
            return deleted.toTypedArray()
        }

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     */
    override val dirty: Array<LocalResource>
        @Throws(ContactsStorageException::class)
        get() {
            val dirty = LinkedList<LocalResource>()
            Collections.addAll(dirty, *dirtyContacts)
            if (includeGroups)
                Collections.addAll(dirty, *dirtyGroups)
            return dirty.toTypedArray()
        }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    override val withoutFileName: Array<LocalResource>
        @Throws(ContactsStorageException::class)
        get() {
            val nameless = LinkedList<LocalResource>()
            Collections.addAll(nameless, *queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null) as Array<LocalContact>)
            if (includeGroups)
                Collections.addAll(nameless, *queryGroups(AndroidGroup.COLUMN_FILENAME + " IS NULL", null) as Array<LocalGroup>)
            return nameless.toTypedArray()
        }

    val deletedContacts: Array<LocalContact>
        @Throws(ContactsStorageException::class)
        get() = queryContacts(RawContacts.DELETED + "!= 0", null) as Array<LocalContact>

    val dirtyContacts: Array<LocalContact>
        @Throws(ContactsStorageException::class)
        get() = queryContacts(RawContacts.DIRTY + "!= 0 AND " + RawContacts.DELETED + "== 0", null) as Array<LocalContact>

    val all: Array<LocalContact>
        @Throws(ContactsStorageException::class)
        get() = queryContacts(RawContacts.DELETED + "== 0", null) as Array<LocalContact>

    val deletedGroups: Array<LocalGroup>
        @Throws(ContactsStorageException::class)
        get() = queryGroups(Groups.DELETED + "!= 0", null) as Array<LocalGroup>

    val dirtyGroups: Array<LocalGroup>
        @Throws(ContactsStorageException::class)
        get() = queryGroups(Groups.DIRTY + "!= 0 AND " + Groups.DELETED + "== 0", null) as Array<LocalGroup>

    var mainAccount: Account
        @Throws(ContactsStorageException::class)
        get() {
            val accountManager = AccountManager.get(context)
            val name = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME)
            val type = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE)
            return if (name != null && type != null)
                Account(name, type)
            else
                throw ContactsStorageException("Address book doesn't exist anymore")
        }
        @Throws(ContactsStorageException::class)
        set(mainAccount) {
            val accountManager = AccountManager.get(context)
            accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
        }

    var url: String?
        @Throws(ContactsStorageException::class)
        get() {
            val accountManager = AccountManager.get(context)
            return accountManager.getUserData(account, USER_DATA_URL)
        }
        @Throws(ContactsStorageException::class)
        set(url) {
            val accountManager = AccountManager.get(context)
            accountManager.setUserData(account, USER_DATA_URL, url)
        }

    @Throws(AuthenticatorException::class, OperationCanceledException::class, IOException::class, ContactsStorageException::class, android.accounts.OperationCanceledException::class)
    fun update(journalEntity: JournalEntity) {
        val info = journalEntity.info
        val newAccountName = accountName(mainAccount, info)
        if (account.name != newAccountName && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val accountManager = AccountManager.get(context)
            val future = accountManager.renameAccount(account, newAccountName, {
                try {
                    // update raw contacts to new account name
                    if (provider != null) {
                        val values = ContentValues(1)
                        values.put(RawContacts.ACCOUNT_NAME, newAccountName)
                        provider.update(syncAdapterURI(RawContacts.CONTENT_URI), values, RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?",
                                arrayOf(account.name, account.type))
                    }
                } catch (e: RemoteException) {
                    App.log.log(Level.WARNING, "Couldn't re-assign contacts to new account name", e)
                }
            }, null)
            account = future.result
        }

        // make sure it will still be synchronized when contacts are updated
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    fun delete() {
        val accountManager = AccountManager.get(context)
        AndroidCompat.removeAccount(accountManager, account)
    }

    @Throws(ContactsStorageException::class, FileNotFoundException::class)
    fun findContactByUID(uid: String): LocalContact {
        val contacts = queryContacts(LocalContact.COLUMN_UID + "=?", arrayOf(uid)) as Array<LocalContact>
        if (contacts.size == 0)
            throw FileNotFoundException()
        return contacts[0]
    }

    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     */
    @Throws(ContactsStorageException::class)
    fun verifyDirty(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("verifyDirty() should not be called on Android <7")

        var reallyDirty = 0
        for (contact in dirtyContacts) {
            try {
                val lastHash = contact.lastHashCode
                val currentHash = contact.dataHashCode()
                if (lastHash == currentHash) {
                    // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                    App.log.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact)
                    contact.resetDirty()
                } else {
                    App.log.log(Level.FINE, "Contact data has changed from hash $lastHash to $currentHash", contact)
                    reallyDirty++
                }
            } catch (e: FileNotFoundException) {
                throw ContactsStorageException("Couldn't calculate hash code", e)
            }

        }

        if (includeGroups)
            reallyDirty += dirtyGroups.size

        return reallyDirty
    }

    @Throws(ContactsStorageException::class)
    override fun getByUid(uid: String): LocalResource? {
        val ret = queryContacts(AndroidContact.COLUMN_FILENAME + " =? ", arrayOf(uid)) as Array<LocalContact>
        return if (ret != null && ret.size > 0) {
            ret[0]
        } else null
    }

    @Throws(ContactsStorageException::class)
    override fun count(): Long {
        try {
            val cursor = provider.query(syncAdapterURI(RawContacts.CONTENT_URI), null, null, null, null)
            try {
                return cursor.count.toLong()
            } finally {
                cursor.close()
            }
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }

    }

    @Throws(ContactsStorageException::class)
    internal fun getByGroupMembership(groupID: Long): Array<LocalContact> {
        try {
            val cursor = provider.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                    arrayOf(RawContacts.Data.RAW_CONTACT_ID),
                    "(" + GroupMembership.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?) OR (" + CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?)",
                    arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupID.toString(), CachedGroupMembership.CONTENT_ITEM_TYPE, groupID.toString()), null)

            val ids = HashSet<Long>()
            while (cursor != null && cursor.moveToNext())
                ids.add(cursor.getLong(0))

            cursor!!.close()

            val contacts = arrayOfNulls<LocalContact>(ids.size)
            var i = 0
            for (id in ids)
                contacts[i++] = LocalContact(this, id, null, null)
            return contacts
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }

    }


    @Throws(ContactsStorageException::class)
    fun deleteAll() {
        try {
            provider.delete(syncAdapterURI(RawContacts.CONTENT_URI), null, null)
            provider.delete(syncAdapterURI(Groups.CONTENT_URI), null, null)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't delete all local contacts and groups", e)
        }

    }


    /**
     * Finds the first group with the given title. If there is no group with this
     * title, a new group is created.
     * @param title     title of the group to look for
     * @return          id of the group with given title
     * @throws ContactsStorageException on contact provider errors
     */
    @Throws(ContactsStorageException::class)
    fun findOrCreateGroup(title: String): Long {
        try {
            val cursor = provider.query(syncAdapterURI(Groups.CONTENT_URI),
                    arrayOf(Groups._ID),
                    Groups.TITLE + "=?", arrayOf(title), null)
            try {
                if (cursor != null && cursor.moveToNext())
                    return cursor.getLong(0)
            } finally {
                cursor!!.close()
            }

            val values = ContentValues()
            values.put(Groups.TITLE, title)
            val uri = provider.insert(syncAdapterURI(Groups.CONTENT_URI), values)
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't find local contact group", e)
        }

    }

    @Throws(ContactsStorageException::class)
    fun removeEmptyGroups() {
        // find groups without members
        /** should be done using [Groups.SUMMARY_COUNT], but it's not implemented in Android yet  */
        for (group in queryGroups(null, null) as Array<LocalGroup>)
            if (group.members.size == 0) {
                App.log.log(Level.FINE, "Deleting group", group)
                group.delete()
            }
    }

    @Throws(ContactsStorageException::class)
    fun removeGroups() {
        try {
            provider.delete(syncAdapterURI(Groups.CONTENT_URI), null, null)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't remove all groups", e)
        }

    }

    /** Fix all of the etags of all of the non-dirty contacts to be non-null.
     * Currently set to all ones.  */
    @Throws(ContactsStorageException::class)
    fun fixEtags() {
        val newEtag = "1111111111111111111111111111111111111111111111111111111111111111"
        val where = ContactsContract.RawContacts.DIRTY + "=0 AND " + AndroidContact.COLUMN_ETAG + " IS NULL"

        val values = ContentValues(1)
        values.put(AndroidContact.COLUMN_ETAG, newEtag)
        try {
            val fixed = provider.update(syncAdapterURI(RawContacts.CONTENT_URI),
                    values, where, null)
            App.log.info("Fixed entries: " + fixed.toString())
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }

    }

    companion object {

        protected val USER_DATA_MAIN_ACCOUNT_TYPE = "real_account_type"
        protected val USER_DATA_MAIN_ACCOUNT_NAME = "real_account_name"
        protected val USER_DATA_URL = "url"


        @Throws(ContactsStorageException::class)
        fun find(context: Context, provider: ContentProviderClient, mainAccount: Account?): Array<LocalAddressBook> {
            val accountManager = AccountManager.get(context)

            val result = LinkedList<LocalAddressBook>()
            for (account in accountManager.getAccountsByType(App.addressBookAccountType)) {
                val addressBook = LocalAddressBook(context, account, provider)
                if (mainAccount == null || addressBook.mainAccount == mainAccount)
                    result.add(addressBook)
            }

            return result.toTypedArray()
        }

        @Throws(ContactsStorageException::class)
        fun findByUid(context: Context, provider: ContentProviderClient, mainAccount: Account?, uid: String): LocalAddressBook? {
            val accountManager = AccountManager.get(context)

            for (account in accountManager.getAccountsByType(App.addressBookAccountType)) {
                val addressBook = LocalAddressBook(context, account, provider)
                if (addressBook.url == uid && (mainAccount == null || addressBook.mainAccount == mainAccount))
                    return addressBook
            }

            return null
        }

        @Throws(ContactsStorageException::class)
        fun create(context: Context, provider: ContentProviderClient, mainAccount: Account, journalEntity: JournalEntity): LocalAddressBook {
            val info = journalEntity.info
            val accountManager = AccountManager.get(context)

            val account = Account(accountName(mainAccount, info), App.addressBookAccountType)
            if (!accountManager.addAccountExplicitly(account, null, null))
                throw ContactsStorageException("Couldn't create address book account")

            setUserData(accountManager, account, mainAccount, info.uid!!)
            val addressBook = LocalAddressBook(context, account, provider)
            addressBook.mainAccount = mainAccount
            addressBook.url = info.uid

            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)

            return addressBook
        }


        // SETTINGS

        // XXX: Workaround a bug in Android where passing a bundle to addAccountExplicitly doesn't work.
        fun setUserData(accountManager: AccountManager, account: Account, mainAccount: Account, url: String) {
            accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
            accountManager.setUserData(account, USER_DATA_URL, url)
        }

        // HELPERS

        fun accountName(mainAccount: Account, info: CollectionInfo): String {
            val displayName = if (info.displayName != null) info.displayName else info.uid
            val sb = StringBuilder(displayName)
            sb.append(" (")
                    .append(mainAccount.name)
                    .append(" ")
                    .append(info.uid!!.substring(0, 4))
                    .append(")")
            return sb.toString()
        }
    }
}
