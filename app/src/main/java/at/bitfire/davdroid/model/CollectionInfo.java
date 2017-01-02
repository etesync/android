/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import java.io.Serializable;

import at.bitfire.davdroid.GsonHelper;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import lombok.ToString;

@ToString
public class CollectionInfo implements Serializable {
    public transient long id;
    public transient Long serviceID;

    public enum Type {
        ADDRESS_BOOK,
        CALENDAR
    }

    public Type type;

    public transient String url; // Essentially the uuid

    public boolean readOnly;
    public String displayName, description;
    public Integer color;

    public String timeZone;
    public Boolean supportsVEVENT;
    public Boolean supportsVTODO;

    public boolean selected;

    public CollectionInfo() {
    }

    public static CollectionInfo defaultForService(String sService) {
        Type service = Type.valueOf(sService);
        CollectionInfo info = new CollectionInfo();
        info.displayName = "Default";
        info.selected = true;
        info.readOnly = false;
        info.type = service;

        if (service.equals(Type.CALENDAR)) {
            info.supportsVEVENT =  true;
            // info.supportsVTODO = true;
        } else {
            // Carddav
        }
        return info;
    }

    public boolean isOfTypeService(String service) {
        return service.equals(type.toString());
    }

    public static CollectionInfo fromDB(ContentValues values) {
        CollectionInfo info = new CollectionInfo();
        info.id = values.getAsLong(Collections.ID);
        info.serviceID = values.getAsLong(Collections.SERVICE_ID);

        info.url = values.getAsString(Collections.URL);
        info.readOnly = values.getAsInteger(Collections.READ_ONLY) != 0;
        info.displayName = values.getAsString(Collections.DISPLAY_NAME);
        info.description = values.getAsString(Collections.DESCRIPTION);

        info.color = values.getAsInteger(Collections.COLOR);

        info.timeZone = values.getAsString(Collections.TIME_ZONE);
        info.supportsVEVENT = getAsBooleanOrNull(values, Collections.SUPPORTS_VEVENT);
        info.supportsVTODO = getAsBooleanOrNull(values, Collections.SUPPORTS_VTODO);

        info.selected = values.getAsInteger(Collections.SYNC) != 0;
        return info;
    }

    public ContentValues toDB() {
        ContentValues values = new ContentValues();
        // Collections.SERVICE_ID is never changed

        values.put(Collections.URL, url);
        values.put(Collections.READ_ONLY, readOnly ? 1 : 0);
        values.put(Collections.DISPLAY_NAME, displayName);
        values.put(Collections.DESCRIPTION, description);
        values.put(Collections.COLOR, color);

        values.put(Collections.TIME_ZONE, timeZone);
        if (supportsVEVENT != null)
            values.put(Collections.SUPPORTS_VEVENT, supportsVEVENT ? 1 : 0);
        if (supportsVTODO != null)
            values.put(Collections.SUPPORTS_VTODO, supportsVTODO ? 1 : 0);

        values.put(Collections.SYNC, selected ? 1 : 0);
        return values;
    }

    public static CollectionInfo fromJson(String json) {
        return GsonHelper.gson.fromJson(json, CollectionInfo.class);
    }

    public String toJson() {
        return GsonHelper.gson.toJson(this, CollectionInfo.class);
    }

    private static Boolean getAsBooleanOrNull(ContentValues values, String field) {
        Integer i = values.getAsInteger(field);
        return (i == null) ? null : (i != 0);
    }

}
