/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.model

import android.content.ContentValues
import com.etesync.syncadapter.journalmanager.Constants
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.model.ServiceDB.Collections
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.requery.Persistable
import io.requery.sql.EntityDataStore
import java.io.Serializable

class CollectionInfo : Serializable {
    @Deprecated("")
    var id: Long = 0

    var serviceID: Int = 0

    // FIXME: Shouldn't be exposed, as it's already saved in the journal. We just expose it for when we save for db.
    @Expose
    var version = -1

    @Expose
    var type: Type? = null

    var uid: String? = null

    @Expose
    var displayName: String? = null
    @Expose
    var description: String? = null
    @Expose
    var color: Int? = null

    @Expose
    var timeZone: String? = null

    @Expose
    var selected: Boolean = false

    enum class Type {
        ADDRESS_BOOK,
        CALENDAR,
        TASKS,
    }

    init {
        version = Constants.CURRENT_VERSION
    }

    fun updateFromJournal(journal: JournalManager.Journal) {
        uid = journal.uid!!
        version = journal.version
    }

    fun isOfTypeService(service: String): Boolean {
        return service == type.toString()
    }

    fun getServiceEntity(data: EntityDataStore<Persistable>): ServiceEntity {
        return data.findByKey(ServiceEntity::class.java, serviceID)
    }

    fun toJson(): String {
        return GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this, CollectionInfo::class.java)
    }

    override fun toString(): String {
        return "CollectionInfo(serviceID=" + this.serviceID + ", version=" + this.version + ", type=" + this.type + ", uid=" + this.uid + ", displayName=" + this.displayName + ", description=" + this.description + ", color=" + this.color + ", timeZone=" + this.timeZone + ", selected=" + this.selected + ")"
    }

    companion object {

        fun defaultForServiceType(service: Type): CollectionInfo {
            val info = CollectionInfo()
            info.displayName = "Default"
            info.selected = true
            info.type = service

            return info
        }

        fun fromDB(values: ContentValues): CollectionInfo {
            val info = CollectionInfo()
            info.id = values.getAsLong(Collections.ID)!!
            info.serviceID = values.getAsInteger(Collections.SERVICE_ID)!!

            info.uid = values.getAsString(Collections.URL)
            info.displayName = values.getAsString(Collections.DISPLAY_NAME)
            info.description = values.getAsString(Collections.DESCRIPTION)

            info.color = values.getAsInteger(Collections.COLOR)

            info.timeZone = values.getAsString(Collections.TIME_ZONE)

            info.selected = values.getAsInteger(Collections.SYNC) != 0
            return info
        }

        fun fromJson(json: String): CollectionInfo {
            return GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, CollectionInfo::class.java)
        }

        private fun getAsBooleanOrNull(values: ContentValues, field: String): Boolean? {
            val i = values.getAsInteger(field)
            return if (i == null) null else i != 0
        }
    }
}
