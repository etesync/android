/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup

import android.os.Parcel
import android.os.Parcelable
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.log.Logger
import java.net.URI
import java.net.URISyntaxException

class LoginCredentials(val uri: URI?, val userName: String, val password: String) : Parcelable {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(uri)
        dest.writeString(userName)
        dest.writeString(password)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<*> = object : Parcelable.Creator<LoginCredentials> {
            override fun createFromParcel(source: Parcel): LoginCredentials {
                return LoginCredentials(
                        source.readSerializable() as URI,
                        source.readString()!!, source.readString()!!
                )
            }

            override fun newArray(size: Int): Array<LoginCredentials?> {
                return arrayOfNulls(size)
            }
        }
    }
}
