/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.model;

import android.content.ContentValues;

import com.etesync.syncadapter.journalmanager.Constants;
import com.etesync.syncadapter.journalmanager.JournalManager;
import com.etesync.syncadapter.model.ServiceDB.Collections;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import java.io.Serializable;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

public class CollectionInfo implements Serializable {
    @Deprecated
    public long id;

    public int serviceID;

    public enum Type {
        ADDRESS_BOOK,
        CALENDAR
    }

    // FIXME: Shouldn't be exposed, as it's already saved in the journal. We just expose it for when we save for db.
    @Expose
    public int version = -1;

    @Expose
    public Type type;

    public String uid;

    @Expose
    public String displayName, description;
    @Expose
    public Integer color;

    @Expose
    public String timeZone;

    @Expose
    public boolean selected;

    public CollectionInfo() {
        version = Constants.CURRENT_VERSION;
    }

    public static CollectionInfo defaultForServiceType(Type service) {
        CollectionInfo info = new CollectionInfo();
        info.displayName = "Default";
        info.selected = true;
        info.type = service;

        return info;
    }

    public void updateFromJournal(JournalManager.Journal journal) {
        uid = journal.getUid();
        version = journal.getVersion();
    }

    public boolean isOfTypeService(String service) {
        return service.equals(type.toString());
    }

    public static CollectionInfo fromDB(ContentValues values) {
        CollectionInfo info = new CollectionInfo();
        info.id = values.getAsLong(Collections.ID);
        info.serviceID = values.getAsInteger(Collections.SERVICE_ID);

        info.uid = values.getAsString(Collections.URL);
        info.displayName = values.getAsString(Collections.DISPLAY_NAME);
        info.description = values.getAsString(Collections.DESCRIPTION);

        info.color = values.getAsInteger(Collections.COLOR);

        info.timeZone = values.getAsString(Collections.TIME_ZONE);

        info.selected = values.getAsInteger(Collections.SYNC) != 0;
        return info;
    }

    public ServiceEntity getServiceEntity(EntityDataStore<Persistable> data) {
        return data.findByKey(ServiceEntity.class, serviceID);
    }

    public static CollectionInfo fromJson(String json) {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, CollectionInfo.class);
    }

    public String toJson() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this, CollectionInfo.class);
    }

    private static Boolean getAsBooleanOrNull(ContentValues values, String field) {
        Integer i = values.getAsInteger(field);
        return (i == null) ? null : (i != 0);
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "CollectionInfo(serviceID=" + this.serviceID + ", version=" + this.version + ", type=" + this.type + ", uid=" + this.uid + ", displayName=" + this.displayName + ", description=" + this.description + ", color=" + this.color + ", timeZone=" + this.timeZone + ", selected=" + this.selected + ")";
    }
}
