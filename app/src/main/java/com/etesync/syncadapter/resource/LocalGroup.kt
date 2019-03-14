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
import android.net.Uri
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts.Data
import android.text.TextUtils
import at.bitfire.vcard4android.*
import at.bitfire.vcard4android.GroupMethod.GROUP_VCARDS
import com.etesync.syncadapter.log.Logger
import ezvcard.VCardVersion
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.Level

class LocalGroup : AndroidGroup, LocalAddress {
    companion object {
        /** marshalled list of member UIDs, as sent by server  */
        val COLUMN_PENDING_MEMBERS = Groups.SYNC3

        /**
         * Processes all groups with non-null {@link #COLUMN_PENDING_MEMBERS}: the pending memberships
         * are (if possible) applied, keeping cached memberships in sync.
         * @param addressBook    address book to take groups from
         */
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            addressBook.provider!!.query(
                    addressBook.groupsSyncUri(),
                    arrayOf(Groups._ID, COLUMN_PENDING_MEMBERS),
                    "$COLUMN_PENDING_MEMBERS IS NOT NULL", null,
                    null
            )?.use { cursor ->
                val batch = BatchOperation(addressBook.provider)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)

                    // extract list of member UIDs
                    val members = LinkedList<String>()
                    val raw = cursor.getBlob(1)
                    val parcel = Parcel.obtain()
                    try {
                        parcel.unmarshall(raw, 0, raw.size)
                        parcel.setDataPosition(0)
                        parcel.readStringList(members)
                    } finally {
                        parcel.recycle()
                    }

                    // insert memberships
                    val membersIds = members.map {uid ->
                        Constants.log.fine("Assigning member: $uid")
                        val contact = addressBook.findByUid(uid) as LocalContact?
                        if (contact != null) contact.id else null
                    }.filterNotNull()

                    val group = addressBook.findGroupById(id)
                    group.setMembers(batch, membersIds)

                    batch.commit()
                }
            }
        }
    }

    private var saveAsDirty = false // When true, the resource will be saved as dirty

    override val uuid: String?
        get() = fileName

    override val content: String
        get() {
            val contact: Contact
            contact = this.contact!!

            Logger.log.log(Level.FINE, "Preparing upload of VCard $uuid", contact)

            val os = ByteArrayOutputStream()
            contact.write(VCardVersion.V4_0, GROUP_VCARDS, os)

            return os.toString()
        }

    override val isLocalOnly: Boolean
        get() = TextUtils.isEmpty(eTag)

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues)
            : super(addressBook, values) {}

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?)
            : super(addressBook, contact, fileName, eTag) {}

    override fun contentValues(): ContentValues  {
        val values = super.contentValues()

        val members = Parcel.obtain()
        try {
            members.writeStringList(contact?.members)
            values.put(COLUMN_PENDING_MEMBERS, members.marshall())
        } finally {
            members.recycle()
        }

        if (saveAsDirty) {
            values.put(Groups.DIRTY, true)
        }
        return values
    }

    override fun clearDirty(eTag: String) {
        val id = requireNotNull(id)

        val values = ContentValues(2)
        values.put(Groups.DIRTY, 0)
        this.eTag = eTag
        values.put(AndroidGroup.COLUMN_ETAG, eTag)
        update(values)

        // update cached group memberships
        val batch = BatchOperation(addressBook.provider!!)

        // delete cached group memberships
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newDelete(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                        .withSelection(
                                CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?",
                                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE, id.toString())
                        )
        ))

        // insert updated cached group memberships
        for (member in getMembers())
            batch.enqueue(BatchOperation.Operation(
                    ContentProviderOperation.newInsert(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                            .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                            .withValue(CachedGroupMembership.RAW_CONTACT_ID, member)
                            .withValue(CachedGroupMembership.GROUP_ID, id)
                            .withYieldAllowed(true)
            ))

        batch.commit()
    }

    override fun prepareForUpload() {
        val uid = UUID.randomUUID().toString()

        val values = ContentValues(2)
        values.put(AndroidGroup.COLUMN_FILENAME, uid)
        values.put(AndroidGroup.COLUMN_UID, uid)
        update(values)

        fileName = uid
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(Groups.DELETED, 0)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)
    }

    private fun setMembers(batch: BatchOperation, members: List<Long>) {
        val id = id!!
        val addressBook = this.addressBook as LocalAddressBook
        Constants.log.fine("Assigning members to group $id")

        // required for workaround for Android 7 which sets DIRTY flag when only meta-data is changed
        val changeContactIDs = HashSet<Long>()

        // delete all memberships and cached memberships for this group
        for (contact in addressBook.getByGroupMembership(id)) {
            contact.removeGroupMemberships(batch)
            changeContactIDs += contact.id!!
        }

        // insert memberships
        for (memberId in members) {
            Constants.log.fine("Assigning member: $memberId")
            addressBook.findContactByID(memberId).let { member ->
                member.addToGroup(batch, id)
                changeContactIDs += member.id!!
            } ?: Constants.log.warning("Group member not found: $memberId")
        }

        if (LocalContact.HASH_HACK)
        // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            changeContactIDs
                    .map { addressBook.findContactByID(it) }
                    .forEach { it.updateHashCode(batch) }

        // remove pending memberships
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)))
                        .withValue(COLUMN_PENDING_MEMBERS, null)
                        .withYieldAllowed(true)
        ))
    }

    fun createAsDirty(members: List<Long>): Uri {
        saveAsDirty = true

        val ret = this.add()

        val batch = BatchOperation(addressBook.provider!!)
        setMembers(batch, members)
        batch.commit()

        return ret
    }

    // helpers

    private fun groupSyncUri(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(addressBook.groupsSyncUri(), id)
    }

    /**
     * Lists all members of this group.
     * @return list of all members' raw contact IDs
     * @throws RemoteException on contact provider errors
     */
    internal fun getMembers(): List<Long> {
        val id = requireNotNull(id)
        val members = LinkedList<Long>()
        addressBook.provider!!.query(
                addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                arrayOf(Data.RAW_CONTACT_ID),
                "${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, id.toString()),
                null
        )?.use { cursor ->
            while (cursor.moveToNext())
                members += cursor.getLong(0)
        }
        return members
    }


    // factory

    object Factory: AndroidGroupFactory<LocalGroup> {
        override fun fromProvider(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) =
                LocalGroup(addressBook, values)
    }
}
