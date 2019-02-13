/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

interface LocalResource<in TData: Any> {
    val uuid: String?

    /** True if doesn't exist on server yet, false otherwise.  */
    val isLocalOnly: Boolean

    /** Returns a string of how this should be represented for example: vCard.  */
    val content: String

    fun delete(): Int

    fun prepareForUpload()

    fun clearDirty(eTag: String)

    fun resetDeleted()
}
