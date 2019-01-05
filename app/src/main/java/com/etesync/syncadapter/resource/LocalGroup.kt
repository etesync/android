/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.os.Build
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import android.text.TextUtils

import com.etesync.syncadapter.App

import org.apache.commons.lang3.ArrayUtils

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.HashSet
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Level

import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidGroup
import at.bitfire.vcard4android.AndroidGroupFactory
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import ezvcard.VCardVersion

import at.bitfire.vcard4android.GroupMethod.GROUP_VCARDS

class LocalGroup : AndroidGroup, LocalResource {

    override val uuid: String
        get() = getFileName()

    override val content: String
        @Throws(IOException::class, ContactsStorageException::class)
        get() {
            val contact: Contact
            contact = getContact()

            App.log.log(Level.FINE, "Preparing upload of VCard $uuid", contact)

            val os = ByteArrayOutputStream()
            contact.write(VCardVersion.V4_0, GROUP_VCARDS, os)

            return os.toString()
        }

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(getETag())

    /**
     * Lists all members of this group.
     * @return list of all members' raw contact IDs
     * @throws ContactsStorageException on contact provider errors
     */
    val members: LongArray
        @Throws(ContactsStorageException::class)
        get() {
            assertID()
            val members = LinkedList<Long>()
            try {
                val cursor = addressBook.provider.query(
                        addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                        arrayOf(Data.RAW_CONTACT_ID),
                        GroupMembership.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                        arrayOf(GroupMembership.CONTENT_ITEM_TYPE, id.toString()), null
                )
                while (cursor != null && cursor.moveToNext())
                    members.add(cursor.getLong(0))
                cursor!!.close()
            } catch (e: RemoteException) {
                throw ContactsStorageException("Couldn't list group members", e)
            }

            return ArrayUtils.toPrimitive(members.toTypedArray())
        }

    constructor(addressBook: AndroidAddressBook, id: Long, fileName: String?, eTag: String?) : super(addressBook, id, fileName, eTag) {}

    constructor(addressBook: AndroidAddressBook, contact: Contact, fileName: String?, eTag: String?) : super(addressBook, contact, fileName, eTag) {}

    @Throws(ContactsStorageException::class)
    override fun clearDirty(eTag: String) {
        assertID()

        val values = ContentValues(2)
        values.put(Groups.DIRTY, 0)
        this.eTag = eTag
        values.put(AndroidGroup.COLUMN_ETAG, eTag)
        update(values)

        // update cached group memberships
        val batch = BatchOperation(addressBook.provider)

        // delete cached group memberships
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newDelete(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                        .withSelection(
                                CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?",
                                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE, id.toString())
                        )
        ))

        // insert updated cached group memberships
        for (member in members)
            batch.enqueue(BatchOperation.Operation(
                    ContentProviderOperation.newInsert(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                            .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                            .withValue(CachedGroupMembership.RAW_CONTACT_ID, member)
                            .withValue(CachedGroupMembership.GROUP_ID, id)
                            .withYieldAllowed(true)
            ))

        batch.commit()
    }

    @Throws(ContactsStorageException::class)
    override fun prepareForUpload() {
        val uid = UUID.randomUUID().toString()

        val values = ContentValues(2)
        values.put(AndroidGroup.COLUMN_FILENAME, uid)
        values.put(AndroidGroup.COLUMN_UID, uid)
        update(values)

        fileName = uid
    }

    override fun contentValues(): ContentValues {
        val values = super.contentValues()

        val members = Parcel.obtain()
        members.writeStringList(contact.members)
        values.put(COLUMN_PENDING_MEMBERS, members.marshall())

        members.recycle()
        return values
    }


    /**
     * Marks all members of the current group as dirty.
     */
    @Throws(ContactsStorageException::class)
    fun markMembersDirty() {
        assertID()
        val batch = BatchOperation(addressBook.provider)

        for (member in members)
            batch.enqueue(BatchOperation.Operation(
                    ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(RawContacts.CONTENT_URI, member)))
                            .withValue(RawContacts.DIRTY, 1)
                            .withYieldAllowed(true)
            ))

        batch.commit()
    }


    // helpers

    private fun assertID() {
        if (id == null)
            throw IllegalStateException("Group has not been saved yet")
    }

    override fun toString(): String {
        return "LocalGroup(super=" + super.toString() + ", uuid=" + this.uuid + ")"
    }

    // factory

    internal class Factory : AndroidGroupFactory() {

        override fun newInstance(addressBook: AndroidAddressBook, id: Long, fileName: String, eTag: String): LocalGroup {
            return LocalGroup(addressBook, id, fileName, eTag)
        }

        override fun newInstance(addressBook: AndroidAddressBook, contact: Contact, fileName: String, eTag: String): LocalGroup {
            return LocalGroup(addressBook, contact, fileName, eTag)
        }

        override fun newArray(size: Int): Array<LocalGroup> {
            return arrayOfNulls(size)
        }

        companion object {
            val INSTANCE = Factory()
        }

    }

    companion object {
        /** marshalled list of member UIDs, as sent by server  */
        val COLUMN_PENDING_MEMBERS = Groups.SYNC3

        /**
         * Processes all groups with non-null [.COLUMN_PENDING_MEMBERS]: the pending memberships
         * are (if possible) applied, keeping cached memberships in sync.
         * @param addressBook    address book to take groups from
         * @throws ContactsStorageException on contact provider errors
         */
        @Throws(ContactsStorageException::class)
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            try {
                val cursor = addressBook.provider.query(
                        addressBook.syncAdapterURI(Groups.CONTENT_URI),
                        arrayOf(Groups._ID, COLUMN_PENDING_MEMBERS),
                        "$COLUMN_PENDING_MEMBERS IS NOT NULL", arrayOf(), null
                )

                val batch = BatchOperation(addressBook.provider)
                while (cursor != null && cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    App.log.fine("Assigning members to group $id")

                    // required for workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                    val changeContactIDs = HashSet<Long>()

                    // delete all memberships and cached memberships for this group
                    for (contact in addressBook.getByGroupMembership(id)) {
                        contact.removeGroupMemberships(batch)
                        changeContactIDs.add(contact.id)
                    }

                    // extract list of member UIDs
                    val members = LinkedList<String>()
                    val raw = cursor.getBlob(1)
                    val parcel = Parcel.obtain()
                    parcel.unmarshall(raw, 0, raw.size)
                    parcel.setDataPosition(0)
                    parcel.readStringList(members)
                    parcel.recycle()

                    // insert memberships
                    for (uid in members) {
                        App.log.fine("Assigning member: $uid")
                        try {
                            val member = addressBook.findContactByUID(uid)
                            member.addToGroup(batch, id)
                            changeContactIDs.add(member.id)
                        } catch (e: FileNotFoundException) {
                            App.log.log(Level.WARNING, "Group member not found: $uid", e)
                        }

                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                        for (contactID in changeContactIDs) {
                            val contact = LocalContact(addressBook, contactID, null, null)
                            contact.updateHashCode(batch)
                        }

                    // remove pending memberships
                    batch.enqueue(BatchOperation.Operation(
                            ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)))
                                    .withValue(COLUMN_PENDING_MEMBERS, null)
                                    .withYieldAllowed(true)
                    ))

                    batch.commit()
                }
                cursor!!.close()
            } catch (e: RemoteException) {
                throw ContactsStorageException("Couldn't get pending memberships", e)
            }

        }
    }

}
