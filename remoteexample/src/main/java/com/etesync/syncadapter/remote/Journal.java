package com.etesync.syncadapter.remote;

import android.os.Parcel;
import android.os.Parcelable;

public class Journal implements Parcelable {
    public String account;
    public String id;

    public String getId() {
        return id;
    }

    protected Journal(Parcel in) {
        account = in.readString();
        id = in.readString();
    }

    public static final Creator<Journal> CREATOR = new Creator<Journal>() {
        @Override
        public Journal createFromParcel(Parcel in) {
            return new Journal(in);
        }

        @Override
        public Journal[] newArray(int size) {
            return new Journal[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(account);
        parcel.writeString(id);
    }
}
