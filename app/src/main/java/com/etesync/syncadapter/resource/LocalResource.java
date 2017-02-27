/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource;

import java.io.IOException;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;

public interface LocalResource {
    String getUuid();
    Long getId();

    /** True if doesn't exist on server yet, false otherwise. */
    boolean isLocalOnly();

    /** Returns a string of how this should be represented for example: vCard. */
    String getContent() throws IOException, ContactsStorageException, CalendarStorageException;

    int delete() throws CalendarStorageException, ContactsStorageException;

    void updateFileNameAndUID(String uuid) throws CalendarStorageException, ContactsStorageException;
    void clearDirty(String eTag) throws CalendarStorageException, ContactsStorageException;

}
