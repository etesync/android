/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import java.io.IOException

import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException

interface LocalResource {
    val uuid: String?

    /** True if doesn't exist on server yet, false otherwise.  */
    val isLocalOnly: Boolean

    /** Returns a string of how this should be represented for example: vCard.  */
    val content: String

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun delete(): Int

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun prepareForUpload()

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun clearDirty(eTag: String)

}
