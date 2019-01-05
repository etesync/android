/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import android.text.TextUtils

import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.model.UnknownProperties

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.HashSet
import java.util.UUID
import java.util.logging.Level

import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidContactFactory
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import ezvcard.Ezvcard
import ezvcard.VCardVersion

import at.bitfire.vcard4android.GroupMethod.GROUP_VCARDS

class LocalContact : AndroidContact, LocalResource {

    private var saveAsDirty = false // When true, the resource will be saved as dirty

    internal val cachedGroupMemberships: MutableSet<Long> = HashSet()
    internal val groupMemberships: MutableSet<Long> = HashSet()

    override// The same now
    val uuid: String?
        get() = fileName

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    override val content: String
        @Throws(IOException::class, ContactsStorageException::class)
        get() {
            val contact: Contact
            contact = this.contact!!

            App.log.log(Level.FINE, "Preparing upload of VCard $uuid", contact)

            val os = ByteArrayOutputStream()
            contact.write(VCardVersion.V4_0, GROUP_VCARDS, os)

            return os.toString()
        }

    val lastHashCode: Int
        @Throws(ContactsStorageException::class)
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                App.log.severe("getLastHashCode() should not be called on Android <7")

            try {
                val c = addressBook.provider.query(rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)
                try {
                    return if (c == null || !c.moveToNext() || c.isNull(0)) 0 else c.getInt(0)
                } finally {
                    c?.close()
                }
            } catch (e: RemoteException) {
                throw ContactsStorageException("Could't read last hash code", e)
            }

        }


    constructor(addressBook: AndroidAddressBook, id: Long, uuid: String?, eTag: String?) : super(addressBook, id, uuid, eTag) {}

    constructor(addressBook: AndroidAddressBook, contact: Contact, uuid: String?, eTag: String?) : super(addressBook, contact, uuid, eTag) {}

    @Throws(ContactsStorageException::class)
    fun resetDirty() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DIRTY, 0)
        try {
            addressBook.provider.update(rawContactSyncURI(), values, null, null)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't clear dirty flag", e)
        }

    }

    @Throws(ContactsStorageException::class)
    override fun clearDirty(eTag: String) {
        try {
            val values = ContentValues(3)
            values.put(AndroidContact.COLUMN_ETAG, eTag)
            values.put(ContactsContract.RawContacts.DIRTY, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                val hashCode = dataHashCode()
                values.put(COLUMN_HASHCODE, hashCode)
                App.log.finer("Clearing dirty flag with eTag = $eTag, contact hash = $hashCode")
            }

            addressBook.provider.update(rawContactSyncURI(), values, null, null)

            this.eTag = eTag
        } catch (e: FileNotFoundException) {
            throw ContactsStorageException("Couldn't clear dirty flag", e)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't clear dirty flag", e)
        }

    }

    @Throws(ContactsStorageException::class)
    override fun prepareForUpload() {
        try {
            val uid = UUID.randomUUID().toString()

            val values = ContentValues(2)
            values.put(AndroidContact.COLUMN_FILENAME, uid)
            values.put(AndroidContact.COLUMN_UID, uid)
            addressBook.provider.update(rawContactSyncURI(), values, null, null)

            fileName = uid
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't update UID", e)
        }

    }

    override fun populateData(mimeType: String, row: ContentValues) {
        when (mimeType) {
            CachedGroupMembership.CONTENT_ITEM_TYPE -> cachedGroupMemberships.add(row.getAsLong(CachedGroupMembership.GROUP_ID))
            GroupMembership.CONTENT_ITEM_TYPE -> groupMemberships.add(row.getAsLong(GroupMembership.GROUP_ROW_ID))
            UnknownProperties.CONTENT_ITEM_TYPE -> contact.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
        }
    }

    @Throws(ContactsStorageException::class)
    override fun insertDataRows(batch: BatchOperation) {
        super.insertDataRows(batch)

        if (contact.unknownProperties != null) {
            val op: BatchOperation.Operation
            val builder = ContentProviderOperation.newInsert(dataSyncURI())
            if (id == null) {
                op = BatchOperation.Operation(builder, UnknownProperties.RAW_CONTACT_ID, 0)
            } else {
                op = BatchOperation.Operation(builder)
                builder.withValue(UnknownProperties.RAW_CONTACT_ID, id)
            }
            builder.withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                    .withValue(UnknownProperties.UNKNOWN_PROPERTIES, contact.unknownProperties)
            batch.enqueue(op)
        }

    }

    @Throws(ContactsStorageException::class)
    fun updateAsDirty(contact: Contact): Int {
        saveAsDirty = true
        return this.update(contact)
    }

    @Throws(ContactsStorageException::class)
    fun createAsDirty(): Uri {
        saveAsDirty = true
        return this.create()
    }

    override fun buildContact(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildContact(builder, update)
        builder.withValue(ContactsContract.RawContacts.DIRTY, if (saveAsDirty) 1 else 0)
    }

    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads [.contact] from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    @Throws(FileNotFoundException::class, ContactsStorageException::class)
    fun dataHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("dataHashCode() should not be called on Android <7")

        // reset contact so that getContact() reads from database
        contact = null

        // groupMemberships is filled by getContact()
        val dataHash = getContact().hashCode()
        val groupHash = groupMemberships.hashCode()
        App.log.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    @Throws(ContactsStorageException::class)
    fun updateHashCode(batch: BatchOperation?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("updateHashCode() should not be called on Android <7")

        val values = ContentValues(1)
        try {
            val hashCode = dataHashCode()
            App.log.fine("Storing contact hash = $hashCode")
            values.put(COLUMN_HASHCODE, hashCode)

            if (batch == null)
                addressBook.provider.update(rawContactSyncURI(), values, null, null)
            else {
                val builder = ContentProviderOperation
                        .newUpdate(rawContactSyncURI())
                        .withValues(values)
                batch.enqueue(BatchOperation.Operation(builder))
            }
        } catch (e: FileNotFoundException) {
            throw ContactsStorageException("Couldn't store contact checksum", e)
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't store contact checksum", e)
        }

    }


    fun addToGroup(batch: BatchOperation, groupID: Long) {
        assertID()

        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(GroupMembership.RAW_CONTACT_ID, id)
                        .withValue(GroupMembership.GROUP_ROW_ID, groupID)
        ))
        groupMemberships.add(groupID)

        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
                        .withValue(CachedGroupMembership.GROUP_ID, groupID)
                        .withYieldAllowed(true)
        ))
        cachedGroupMemberships.add(groupID)
    }

    fun removeGroupMemberships(batch: BatchOperation) {
        assertID()
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newDelete(dataSyncURI())
                        .withSelection(
                                Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + " IN (?,?)",
                                arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        )
                        .withYieldAllowed(true)
        ))
        groupMemberships.clear()
        cachedGroupMemberships.clear()
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVdroid and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of [GroupMembership.GROUP_ROW_ID] (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @Throws(ContactsStorageException::class, FileNotFoundException::class)
    fun getCachedGroupMemberships(): Set<Long> {
        getContact()
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of [GroupMembership.GROUP_ROW_ID]s (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @Throws(ContactsStorageException::class, FileNotFoundException::class)
    fun getGroupMemberships(): Set<Long> {
        getContact()
        return groupMemberships
    }


    // factory

    internal class Factory : AndroidContactFactory() {

        override fun newInstance(addressBook: AndroidAddressBook, id: Long, fileName: String, eTag: String): LocalContact {
            return LocalContact(addressBook, id, fileName, eTag)
        }

        override fun newArray(size: Int): Array<LocalContact?> {
            return arrayOfNulls(size)
        }

        companion object {
            val INSTANCE = Factory()
        }

    }

    companion object {
        init {
            Contact.productID = Constants.PRODID_BASE + " ez-vcard/" + Ezvcard.VERSION
        }

        val COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3
    }

}
