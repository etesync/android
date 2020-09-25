/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.model

import android.content.ContentValues
import com.etesync.syncadapter.model.ServiceDB.Collections
import com.google.gson.GsonBuilder

class CollectionInfo : com.etesync.journalmanager.model.CollectionInfo() {
    @Deprecated("")
    var id: Long = 0

    var serviceID: Int = 0

    fun getServiceEntity(data: MyEntityDataStore): ServiceEntity {
        return data.findByKey(ServiceEntity::class.java, serviceID)
    }

    enum class Type {
        ADDRESS_BOOK,
        CALENDAR,
        TASKS,
    }

    var enumType: Type?
        get() = if (super.type != null) Type.valueOf(super.type!!) else null
        set(value) {
            super.type = value?.name
        }

    companion object {
        fun defaultForServiceType(service: Type): CollectionInfo {
            val info = CollectionInfo()
            info.displayName = when (service) {
                Type.ADDRESS_BOOK -> "My Contacts"
                Type.CALENDAR -> "My Calendar"
                Type.TASKS -> "My Tasks"
            }
            info.selected = true
            info.enumType = service

            return info
        }

        fun fromJson(json: String): CollectionInfo {
            return GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, CollectionInfo::class.java)
        }
    }
}
