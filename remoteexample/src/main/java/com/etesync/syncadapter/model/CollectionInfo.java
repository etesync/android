/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class CollectionInfo implements Serializable, Parcelable {
    public int serviceID;

    public long id;

    public enum Type {
        ADDRESS_BOOK,
        CALENDAR
    }

    public int version = -1;

    public Type type;

    public String uid;

    public String displayName, description;
    public Integer color;

    public String timeZone;

    public boolean selected;

    protected CollectionInfo(Parcel in) {
        id = in.readLong();
        serviceID = in.readInt();
        version = in.readInt();
        uid = in.readString();
        displayName = in.readString();
        description = in.readString();
        timeZone = in.readString();
        selected = in.readByte() != 0;
    }

    public static final Creator<CollectionInfo> CREATOR = new Creator<CollectionInfo>() {
        @Override
        public CollectionInfo createFromParcel(Parcel in) {
            return new CollectionInfo(in);
        }

        @Override
        public CollectionInfo[] newArray(int size) {
            return new CollectionInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel aParcel, int aI) {
        aParcel.writeLong(id);
        aParcel.writeInt(serviceID);
        aParcel.writeInt(version);
        aParcel.writeString(uid);
        aParcel.writeString(displayName);
        aParcel.writeString(description);
        aParcel.writeString(timeZone);
        aParcel.writeByte((byte) (selected ? 1 : 0));
    }
}
