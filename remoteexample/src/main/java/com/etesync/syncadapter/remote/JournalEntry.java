package com.etesync.syncadapter.remote;

import android.os.Parcel;
import android.os.Parcelable;

public class JournalEntry implements Parcelable {
    public String account;
    public String id;
    public String content;

    public JournalEntry(String id) {
        this.id = id;
    }

    protected JournalEntry(Parcel in) {
        account = in.readString();
        id = in.readString();
        content = in.readString();
    }

    public static final Creator<JournalEntry> CREATOR = new Creator<JournalEntry>() {
        @Override
        public JournalEntry createFromParcel(Parcel in) {
            return new JournalEntry(in);
        }

        @Override
        public JournalEntry[] newArray(int size) {
            return new JournalEntry[size];
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
        parcel.writeString(content);
    }
}
