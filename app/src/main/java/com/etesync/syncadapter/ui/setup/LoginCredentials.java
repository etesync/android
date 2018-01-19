/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup;

import android.os.Parcel;
import android.os.Parcelable;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;

import java.net.URI;
import java.net.URISyntaxException;

public class LoginCredentials implements Parcelable {
    public final URI uri;
    public final String userName, password;

    public LoginCredentials(URI uri, String userName, String password) {
        this.userName = userName;
        this.password = password;

        if (uri == null) {
            try {
                uri = new URI(Constants.serviceUrl.toString());
            } catch (URISyntaxException e) {
                App.log.severe("Should never happen, it's a constant");
            }
        }
        this.uri = uri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(uri);
        dest.writeString(userName);
        dest.writeString(password);
    }

    public static final Creator CREATOR = new Creator<LoginCredentials>() {
        @Override
        public LoginCredentials createFromParcel(Parcel source) {
            return new LoginCredentials(
                    (URI)source.readSerializable(),
                    source.readString(), source.readString()
            );
        }

        @Override
        public LoginCredentials[] newArray(int size) {
            return new LoginCredentials[size];
        }
    };
}
