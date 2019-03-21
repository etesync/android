/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.resource

interface LocalCollection<out T: LocalResource<*>> {
    val url: String?

    fun findDeleted(): List<T>
    fun findDirty(limit: Int? = null): List<T>
    fun findWithoutFileName(): List<T>
    fun findAll(): List<T>

    fun findByUid(uid: String): T?


    fun count(): Long
}
