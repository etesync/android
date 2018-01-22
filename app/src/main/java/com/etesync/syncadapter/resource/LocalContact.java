/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.RawContacts.Data;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.model.UnknownProperties;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.BatchOperation;
import at.bitfire.vcard4android.CachedGroupMembership;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.Ezvcard;
import ezvcard.VCardVersion;

import static at.bitfire.vcard4android.GroupMethod.GROUP_VCARDS;

public class LocalContact extends AndroidContact implements LocalResource {
    static {
        Contact.productID = Constants.PRODID_BASE + " ez-vcard/" + Ezvcard.VERSION;
    }
    public static final String COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3;

    private boolean saveAsDirty = false; // When true, the resource will be saved as dirty

    protected final Set<Long>
            cachedGroupMemberships = new HashSet<>(),
            groupMemberships = new HashSet<>();


    protected LocalContact(AndroidAddressBook addressBook, long id, String uuid, String eTag) {
        super(addressBook, id, uuid, eTag);
    }

    public LocalContact(AndroidAddressBook addressBook, Contact contact, String uuid, String eTag) {
        super(addressBook, contact, uuid, eTag);
    }

    public String getUuid() {
        // The same now
        return getFileName();
    }

    @Override
    public boolean isLocalOnly() {
        return TextUtils.isEmpty(getETag());
    }

    public void resetDirty() throws ContactsStorageException {
        ContentValues values = new ContentValues(1);
        values.put(ContactsContract.RawContacts.DIRTY, 0);
        try {
            addressBook.provider.update(rawContactSyncURI(), values, null, null);
        } catch(RemoteException e) {
            throw new ContactsStorageException("Couldn't clear dirty flag", e);
        }
    }

    public void clearDirty(String eTag) throws ContactsStorageException {
        try {
            ContentValues values = new ContentValues(3);
            values.put(COLUMN_ETAG, eTag);
            values.put(ContactsContract.RawContacts.DIRTY, 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                int hashCode = dataHashCode();
                values.put(COLUMN_HASHCODE, hashCode);
                App.log.finer("Clearing dirty flag with eTag = " + eTag + ", contact hash = " + hashCode);
            }

            addressBook.provider.update(rawContactSyncURI(), values, null, null);

            this.eTag = eTag;
        } catch (FileNotFoundException|RemoteException e) {
            throw new ContactsStorageException("Couldn't clear dirty flag", e);
        }
    }

    public void prepareForUpload() throws ContactsStorageException {
        try {
            final String uid = UUID.randomUUID().toString();
            final String newFileName = uid;

            ContentValues values = new ContentValues(2);
            values.put(COLUMN_FILENAME, newFileName);
            values.put(COLUMN_UID, uid);
            addressBook.provider.update(rawContactSyncURI(), values, null, null);

            fileName = newFileName;
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't update UID", e);
        }
    }

    @Override
    public String getContent() throws IOException, ContactsStorageException {
        final Contact contact;
        contact = getContact();

        App.log.log(Level.FINE, "Preparing upload of VCard " + getUuid(), contact);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        contact.write(VCardVersion.V4_0, GROUP_VCARDS, os);

        return os.toString();
    }

    @Override
    protected void populateData(String mimeType, ContentValues row) {
        switch (mimeType) {
            case CachedGroupMembership.CONTENT_ITEM_TYPE:
                cachedGroupMemberships.add(row.getAsLong(CachedGroupMembership.GROUP_ID));
                break;
            case GroupMembership.CONTENT_ITEM_TYPE:
                groupMemberships.add(row.getAsLong(GroupMembership.GROUP_ROW_ID));
                break;
            case UnknownProperties.CONTENT_ITEM_TYPE:
                contact.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES);
                break;
        }
    }

    @Override
    protected void insertDataRows(BatchOperation batch) throws ContactsStorageException {
        super.insertDataRows(batch);

        if (contact.unknownProperties != null) {
            final BatchOperation.Operation op;
            final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(dataSyncURI());
            if (id == null) {
                op = new BatchOperation.Operation(builder, UnknownProperties.RAW_CONTACT_ID, 0);
            } else {
                op = new BatchOperation.Operation(builder);
                builder.withValue(UnknownProperties.RAW_CONTACT_ID, id);
            }
            builder .withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                    .withValue(UnknownProperties.UNKNOWN_PROPERTIES, contact.unknownProperties);
            batch.enqueue(op);
        }

    }

    public int updateAsDirty(Contact contact) throws ContactsStorageException {
        saveAsDirty = true;
        return this.update(contact);
    }

    public Uri createAsDirty() throws ContactsStorageException {
        saveAsDirty = true;
        return this.create();
    }

    @Override
    protected void buildContact(ContentProviderOperation.Builder builder, boolean update) {
        super.buildContact(builder, update);
        builder.withValue(ContactsContract.RawContacts.DIRTY, saveAsDirty ? 1 : 0);
    }

    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    protected int dataHashCode() throws FileNotFoundException, ContactsStorageException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("dataHashCode() should not be called on Android <7");

        // reset contact so that getContact() reads from database
        contact = null;

        // groupMemberships is filled by getContact()
        int dataHash = getContact().hashCode(),
            groupHash = groupMemberships.hashCode();
        App.log.finest("Calculated data hash = " + dataHash + ", group memberships hash = " + groupHash);
        return dataHash ^ groupHash;
    }

    public void updateHashCode(@Nullable BatchOperation batch) throws ContactsStorageException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("updateHashCode() should not be called on Android <7");

        ContentValues values = new ContentValues(1);
        try {
            int hashCode = dataHashCode();
            App.log.fine("Storing contact hash = " + hashCode);
            values.put(COLUMN_HASHCODE, hashCode);

            if (batch == null)
                addressBook.provider.update(rawContactSyncURI(), values, null, null);
            else {
                ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newUpdate(rawContactSyncURI())
                        .withValues(values);
                batch.enqueue(new BatchOperation.Operation(builder));
            }
        } catch(FileNotFoundException|RemoteException e) {
            throw new ContactsStorageException("Couldn't store contact checksum", e);
        }
    }

    public int getLastHashCode() throws ContactsStorageException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("getLastHashCode() should not be called on Android <7");

        try {
            Cursor c = addressBook.provider.query(rawContactSyncURI(), new String[] { COLUMN_HASHCODE }, null, null, null);
            try {
                if (c == null || !c.moveToNext() || c.isNull(0))
                    return 0;
                return c.getInt(0);
            } finally {
                if (c != null)
                    c.close();
            }
        } catch(RemoteException e) {
            throw new ContactsStorageException("Could't read last hash code", e);
        }
    }


    public void addToGroup(BatchOperation batch, long groupID) {
        assertID();

        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(GroupMembership.RAW_CONTACT_ID, id)
                        .withValue(GroupMembership.GROUP_ROW_ID, groupID)
        ));
        groupMemberships.add(groupID);

        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
                        .withValue(CachedGroupMembership.GROUP_ID, groupID)
                        .withYieldAllowed(true)
        ));
        cachedGroupMemberships.add(groupID);
    }

    public void removeGroupMemberships(BatchOperation batch) {
        assertID();
        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newDelete(dataSyncURI())
                        .withSelection(
                                Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + " IN (?,?)",
                                new String[] { String.valueOf(id), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE }
                        )
                        .withYieldAllowed(true)
        ));
        groupMemberships.clear();
        cachedGroupMemberships.clear();
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVdroid and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of {@link GroupMembership#GROUP_ROW_ID} (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @NonNull
    public Set<Long> getCachedGroupMemberships() throws ContactsStorageException, FileNotFoundException {
        getContact();
        return cachedGroupMemberships;
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of {@link GroupMembership#GROUP_ROW_ID}s (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @NonNull
    public Set<Long> getGroupMemberships() throws ContactsStorageException, FileNotFoundException {
        getContact();
        return groupMemberships;
    }


    // factory

    static class Factory extends AndroidContactFactory {
        static final Factory INSTANCE = new Factory();

        @Override
        public LocalContact newInstance(AndroidAddressBook addressBook, long id, String fileName, String eTag) {
            return new LocalContact(addressBook, id, fileName, eTag);
        }

        @Override
        public LocalContact[] newArray(int size) {
            return new LocalContact[size];
        }

    }

}
