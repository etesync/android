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
import android.annotation.TargetApi
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import at.bitfire.vcard4android.*
import com.etesync.syncadapter.App
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level


class LocalAddressBook(
        private val context: Context,
        account: Account,
        provider: ContentProviderClient?
): AndroidAddressBook<LocalContact, LocalGroup>(account, provider, LocalContact.Factory, LocalGroup.Factory), LocalCollection<LocalAddress> {

    companion object {
        val USER_DATA_MAIN_ACCOUNT_TYPE = "real_account_type"
        val USER_DATA_MAIN_ACCOUNT_NAME = "real_account_name"
        val USER_DATA_URL = "url"
        const val USER_DATA_READ_ONLY = "read_only"

        fun create(context: Context, provider: ContentProviderClient, mainAccount: Account, journalEntity: JournalEntity): LocalAddressBook {
            val info = journalEntity.info
            val accountManager = AccountManager.get(context)

            val account = Account(accountName(mainAccount, info), App.addressBookAccountType)
            if (!accountManager.addAccountExplicitly(account, null, initialUserData(mainAccount, info.uid!!)))
                throw ContactsStorageException("Couldn't create address book account")

            val addressBook = LocalAddressBook(context, account, provider)
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)

            val values = ContentValues(2)
            values.put(ContactsContract.Settings.SHOULD_SYNC, 1)
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            addressBook.settings = values

            return addressBook
        }


        fun find(context: Context, provider: ContentProviderClient?, mainAccount: Account?) = AccountManager.get(context)
                .getAccountsByType(App.addressBookAccountType)
                .map { LocalAddressBook(context, it, provider) }
                .filter { mainAccount == null || it.mainAccount == mainAccount }
                .toList()


        fun findByUid(context: Context, provider: ContentProviderClient, mainAccount: Account?, uid: String): LocalAddressBook? {
            val accountManager = AccountManager.get(context)

            for (account in accountManager.getAccountsByType(App.addressBookAccountType)) {
                val addressBook = LocalAddressBook(context, account, provider)
                if (addressBook.url == uid && (mainAccount == null || addressBook.mainAccount == mainAccount))
                    return addressBook
            }

            return null
        }

        // HELPERS

        fun accountName(mainAccount: Account, info: CollectionInfo): String {
            val displayName = info.displayName ?: info.uid!!
            val sb = StringBuilder(displayName)
            sb.append(" (")
                    .append(mainAccount.name)
                    .append(" ")
                    .append(info.uid!!.substring(0, 4))
                    .append(")")
            return sb.toString()
        }

        fun initialUserData(mainAccount: Account, url: String): Bundle {
            val bundle = Bundle(3)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
            bundle.putString(USER_DATA_URL, url)
            return bundle
        }
    }

    /**
     * Whether contact groups (LocalGroup resources) are included in query results for
     * [.getDeleted], [.getDirty] and
     * [.getWithoutFileName].
     */
    var includeGroups = true

    private var _mainAccount: Account? = null
    var mainAccount: Account
        get() {
            _mainAccount?.let { return it }

            AccountManager.get(context).let { accountManager ->
                val name = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME)
                val type = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE)
                if (name != null && type != null)
                    return Account(name, type)
                else
                    throw IllegalStateException("Address book doesn't exist anymore")
            }
        }
        set(newMainAccount) {
            AccountManager.get(context).let { accountManager ->
                accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, newMainAccount.name)
                accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, newMainAccount.type)
            }

            _mainAccount = newMainAccount
        }

    override var url: String
        get() = AccountManager.get(context).getUserData(account, USER_DATA_URL)
                ?: throw IllegalStateException("Address book has no URL")
        set(url) = AccountManager.get(context).setUserData(account, USER_DATA_URL, url)

    var readOnly: Boolean
        get() = AccountManager.get(context).getUserData(account, USER_DATA_READ_ONLY) != null
        set(readOnly) = AccountManager.get(context).setUserData(account, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

    fun update(journalEntity: JournalEntity) {
        val info = journalEntity.info
        val newAccountName = accountName(mainAccount, info)

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        if (account.name != newAccountName && Build.VERSION.SDK_INT >= 21) {
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
                    Logger.log.log(Level.WARNING, "Couldn't re-assign contacts to new account name", e)
                }
            }, null)
            account = future.result
        }

        Logger.log.info("Address book write permission? = ${!journalEntity.isReadOnly}")
        readOnly = journalEntity.isReadOnly

        // make sure it will still be synchronized when contacts are updated
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    fun delete() {
        val accountManager = AccountManager.get(context)

        @Suppress("DEPRECATION")
        @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccountExplicitly(account)
        else
            accountManager.removeAccount(account, null, null)
    }

    override fun findAll(): List<LocalAddress> =
            if (includeGroups)
                findAllContacts() + findAllGroups()
            else
                findAllContacts()

    fun findAllContacts() = queryContacts("${RawContacts.DELETED}==0", null)
    fun findAllGroups() = queryGroups("${Groups.DELETED}==0", null)

    /**
     * Returns an array of local contacts/groups which have been deleted locally. (DELETED != 0).
     * @throws RemoteException on content provider errors
     */
    override fun findDeleted() =
            if (includeGroups)
                findDeletedContacts() + findDeletedGroups()
            else
                findDeletedContacts()

    fun findDeletedContacts() = queryContacts("${RawContacts.DELETED}!=0", null)
    fun findDeletedGroups() = queryGroups("${Groups.DELETED}!=0", null)

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     * @throws RemoteException on content provider errors
     */
    override fun findDirty(limit: Int?) =
            if (includeGroups)
                findDirtyContacts(limit) + findDirtyGroups(limit) // FIXME: Doesn't rspect limit correctly, but not a big deal for now
            else
                findDirtyContacts(limit)

    fun findDirtyContacts(limit: Int? = null) = queryContacts("${RawContacts.DIRTY}!=0 AND ${RawContacts.DELETED}==0", null, if (limit != null) "${RawContacts._ID} ASC LIMIT $limit" else null)
    fun findDirtyGroups(limit: Int? = null) = queryGroups("${Groups.DIRTY}!=0 AND ${Groups.DELETED}==0", null, if (limit != null) "${Groups._ID} ASC LIMIT $limit" else null)

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    override fun findWithoutFileName() =
            if (includeGroups)
                findWithoutFileNameContacts() + findWithoutFileNameGroups()
            else
                findWithoutFileNameContacts()

    fun findWithoutFileNameContacts() = queryContacts("${AndroidContact.COLUMN_FILENAME} IS NULL", null)
    fun findWithoutFileNameGroups() = queryGroups("${AndroidGroup.COLUMN_FILENAME} IS NULL", null)

    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     */
    fun verifyDirty(): Int {
        if (!LocalContact.HASH_HACK)
            throw IllegalStateException("verifyDirty() should not be called on Android != 7")

        var reallyDirty = 0
        for (contact in findDirtyContacts()) {
            val lastHash = contact.getLastHashCode()
            val currentHash = contact.dataHashCode()
            if (lastHash == currentHash) {
                // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                Logger.log.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact)
                contact.resetDirty()
            } else {
                Logger.log.log(Level.FINE, "Contact data has changed from hash $lastHash to $currentHash", contact)
                reallyDirty++
            }
        }

        if (includeGroups)
            reallyDirty += findDirtyGroups().size

        return reallyDirty
    }

    override fun findByUid(uid: String): LocalAddress? {
        val found = findContactByUID(uid)
        if (found != null) {
            return found
        } else {
            return queryGroups("${AndroidGroup.COLUMN_UID}=?", arrayOf(uid)).firstOrNull()
        }
    }

    fun findGroupById(id: Long): LocalGroup =
            queryGroups("${Groups._ID}=?", arrayOf(id.toString())).firstOrNull()
                    ?: throw FileNotFoundException()

    override fun count(): Long {
        try {
            val cursor = provider?.query(syncAdapterURI(RawContacts.CONTENT_URI), null, null, null, null)
            try {
                return cursor?.count?.toLong()!!
            } finally {
                cursor?.close()
            }
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }

    }

    fun deleteAll() {
        try {
            provider?.delete(syncAdapterURI(RawContacts.CONTENT_URI), null, null)
            provider?.delete(syncAdapterURI(Groups.CONTENT_URI), null, null)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't delete all local contacts and groups", e)
        }

    }


    /* special group operations */
    fun getByGroupMembership(groupID: Long): List<LocalContact> {
        val ids = HashSet<Long>()
        provider!!.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                arrayOf(RawContacts.Data.RAW_CONTACT_ID),
                "(${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?) OR (${CachedGroupMembership.MIMETYPE}=? AND ${CachedGroupMembership.GROUP_ID}=?)",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupID.toString(), CachedGroupMembership.CONTENT_ITEM_TYPE, groupID.toString()),
                null)?.use { cursor ->
            while (cursor.moveToNext())
                ids += cursor.getLong(0)
        }

        return ids.map { findContactByID(it) }
    }


    /* special group operations */

    /**
     * Finds the first group with the given title. If there is no group with this
     * title, a new group is created.
     * @param title title of the group to look for
     * @return id of the group with given title
     * @throws RemoteException on content provider errors
     */
    fun findOrCreateGroup(title: String): Long {
        provider!!.query(syncAdapterURI(Groups.CONTENT_URI), arrayOf(Groups._ID),
                "${Groups.TITLE}=?", arrayOf(title), null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getLong(0)
        }

        val values = ContentValues(1)
        values.put(Groups.TITLE, title)
        val uri = provider.insert(syncAdapterURI(Groups.CONTENT_URI), values)
        return ContentUris.parseId(uri)
    }

    fun removeEmptyGroups() {
        // find groups without members
        /** should be done using {@link Groups.SUMMARY_COUNT}, but it's not implemented in Android yet */
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.forEach { group ->
            Logger.log.log(Level.FINE, "Deleting group", group)
            group.delete()
        }
    }


    /** Fix all of the etags of all of the non-dirty contacts to be non-null.
     * Currently set to all ones.  */
    fun fixEtags() {
        val newEtag = "1111111111111111111111111111111111111111111111111111111111111111"
        val where = ContactsContract.RawContacts.DIRTY + "=0 AND " + AndroidContact.COLUMN_ETAG + " IS NULL"

        val values = ContentValues(1)
        values.put(AndroidContact.COLUMN_ETAG, newEtag)
        try {
            val fixed = provider?.update(syncAdapterURI(RawContacts.CONTENT_URI),
                    values, where, null)
            Logger.log.info("Fixed entries: " + fixed.toString())
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }

    }
}
