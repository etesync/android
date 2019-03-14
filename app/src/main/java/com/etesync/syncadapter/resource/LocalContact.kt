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
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import android.text.TextUtils
import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidContactFactory
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod.GROUP_VCARDS
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.UnknownProperties
import ezvcard.Ezvcard
import ezvcard.VCardVersion
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level

class LocalContact : AndroidContact, LocalAddress {
    companion object {
        init {
            Contact.productID = Constants.PRODID_BASE + " ez-vcard/" + Ezvcard.VERSION
        }

        internal const val COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3

        internal val HASH_HACK = Build.VERSION_CODES.N <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    }

    private var saveAsDirty = false // When true, the resource will be saved as dirty

    internal val cachedGroupMemberships: MutableSet<Long> = HashSet()
    internal val groupMemberships: MutableSet<Long> = HashSet()

    override// The same now
    val uuid: String?
        get() = fileName

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    override val content: String
        get() {
            val contact: Contact
            contact = this.contact!!

            Logger.log.log(Level.FINE, "Preparing upload of VCard $uuid", contact)

            val os = ByteArrayOutputStream()
            contact.write(VCardVersion.V4_0, GROUP_VCARDS, os)

            return os.toString()
        }

    constructor(addressBook: AndroidAddressBook<LocalContact,*>, values: ContentValues)
            : super(addressBook, values) {}

    constructor(addressBook: AndroidAddressBook<LocalContact, *>, contact: Contact, uuid: String?, eTag: String?)
            : super(addressBook, contact, uuid, eTag) {}

    fun resetDirty() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DIRTY, 0)
        addressBook.provider?.update(rawContactSyncURI(), values, null, null)
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DELETED, 0)
        addressBook.provider?.update(rawContactSyncURI(), values, null, null)
    }

    override fun clearDirty(eTag: String) {
        val values = ContentValues(3)
        values.put(AndroidContact.COLUMN_ETAG, eTag)
        values.put(ContactsContract.RawContacts.DIRTY, 0)

        if (LocalContact.HASH_HACK) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val hashCode = dataHashCode()
            values.put(COLUMN_HASHCODE, hashCode)
            Logger.log.finer("Clearing dirty flag with eTag = $eTag, contact hash = $hashCode")
        }

        addressBook.provider?.update(rawContactSyncURI(), values, null, null)

        this.eTag = eTag
    }

    override fun prepareForUpload() {
        val uid = UUID.randomUUID().toString()

        val values = ContentValues(2)
        values.put(AndroidContact.COLUMN_FILENAME, uid)
        values.put(AndroidContact.COLUMN_UID, uid)
        addressBook.provider?.update(rawContactSyncURI(), values, null, null)

        fileName = uid
    }

    override fun populateData(mimeType: String, row: ContentValues) {
        when (mimeType) {
            CachedGroupMembership.CONTENT_ITEM_TYPE -> cachedGroupMemberships.add(row.getAsLong(CachedGroupMembership.GROUP_ID))
            GroupMembership.CONTENT_ITEM_TYPE -> groupMemberships.add(row.getAsLong(GroupMembership.GROUP_ROW_ID))
            UnknownProperties.CONTENT_ITEM_TYPE -> contact?.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
        }
    }

    override fun insertDataRows(batch: BatchOperation) {
        super.insertDataRows(batch)

        if (contact?.unknownProperties != null) {
            val op: BatchOperation.Operation
            val builder = ContentProviderOperation.newInsert(dataSyncURI())
            if (id == null) {
                op = BatchOperation.Operation(builder, UnknownProperties.RAW_CONTACT_ID, 0)
            } else {
                op = BatchOperation.Operation(builder)
                builder.withValue(UnknownProperties.RAW_CONTACT_ID, id)
            }
            builder.withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                    .withValue(UnknownProperties.UNKNOWN_PROPERTIES, contact?.unknownProperties)
            batch.enqueue(op)
        }

    }

    fun updateAsDirty(contact: Contact): Uri {
        saveAsDirty = true
        return this.update(contact)
    }

    fun createAsDirty(): Uri {
        saveAsDirty = true
        return this.add()
    }

    override fun buildContact(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildContact(builder, update)
        builder.withValue(ContactsContract.RawContacts.DIRTY, if (saveAsDirty) 1 else 0)
    }

    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    internal fun dataHashCode(): Int {
        if (!LocalContact.HASH_HACK)
            throw IllegalStateException("dataHashCode() should not be called on Android != 7")

        // reset contact so that getContact() reads from database
        contact = null

        // groupMemberships is filled by getContact()
        val dataHash = contact!!.hashCode()
        val groupHash = groupMemberships.hashCode()
        Logger.log.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    fun updateHashCode(batch: BatchOperation?) {
        if (!LocalContact.HASH_HACK)
            throw IllegalStateException("updateHashCode() should not be called on Android != 7")

        val values = ContentValues(1)
        val hashCode = dataHashCode()
        Logger.log.fine("Storing contact hash = $hashCode")
        values.put(COLUMN_HASHCODE, hashCode)

        if (batch == null)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
        else {
            val builder = ContentProviderOperation
                    .newUpdate(rawContactSyncURI())
                    .withValues(values)
            batch.enqueue(BatchOperation.Operation(builder))
        }
    }

    fun getLastHashCode(): Int {
        if (!LocalContact.HASH_HACK)
            throw IllegalStateException("getLastHashCode() should not be called on Android != 7")

        addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)?.use { c ->
            if (c.moveToNext() && !c.isNull(0))
                return c.getInt(0)
        }
        return 0
    }



    fun addToGroup(batch: BatchOperation, groupID: Long) {
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
    fun getCachedGroupMemberships(): Set<Long> {
        contact
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of [GroupMembership.GROUP_ROW_ID]s (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    fun getGroupMemberships(): Set<Long> {
        contact
        return groupMemberships
    }


    // factory

    object Factory: AndroidContactFactory<LocalContact> {
        override fun fromProvider(addressBook: AndroidAddressBook<LocalContact, *>, values: ContentValues) =
                LocalContact(addressBook, values)
    }
}
