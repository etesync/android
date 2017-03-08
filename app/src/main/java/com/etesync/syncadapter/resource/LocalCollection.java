/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;

public interface LocalCollection {

    LocalResource[] getDeleted() throws CalendarStorageException, ContactsStorageException;
    LocalResource[] getWithoutFileName()  throws CalendarStorageException, ContactsStorageException;
    /** Dirty *non-deleted* entries */
    LocalResource[] getDirty() throws CalendarStorageException, ContactsStorageException, FileNotFoundException;

    LocalResource getByUid(String uid) throws CalendarStorageException, ContactsStorageException;

    String getCTag() throws CalendarStorageException, ContactsStorageException;
    void setCTag(String cTag) throws CalendarStorageException, ContactsStorageException;

    long count() throws CalendarStorageException, ContactsStorageException;
}
