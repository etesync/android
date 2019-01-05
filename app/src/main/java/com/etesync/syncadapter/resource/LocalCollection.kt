/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

import java.io.FileNotFoundException

import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException

interface LocalCollection<T> {

    val deleted: Array<T>
    val withoutFileName: Array<T>
    /** Dirty *non-deleted* entries  */
    val dirty: Array<T>

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun getByUid(uid: String): T?

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun count(): Long
}
