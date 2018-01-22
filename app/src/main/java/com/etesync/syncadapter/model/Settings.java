/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

public class Settings {

    final SQLiteDatabase db;

    public Settings(SQLiteDatabase db) {
        this.db = db;
    }


    public boolean getBoolean(String name, boolean defaultValue) {
        Cursor cursor = db.query(ServiceDB.Settings._TABLE, new String[] { ServiceDB.Settings.VALUE },
                ServiceDB.Settings.NAME + "=?", new String[] { name }, null, null, null);
        try {
            if (cursor.moveToNext() && !cursor.isNull(0))
                return cursor.getInt(0) != 0;
            else
                return defaultValue;
        } finally {
            cursor.close();
        }
    }

    public void putBoolean(String name, boolean value) {
        ContentValues values = new ContentValues(2);
        values.put(ServiceDB.Settings.NAME, name);
        values.put(ServiceDB.Settings.VALUE, value ? 1 : 0);
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }


    public int getInt(String name, int defaultValue) {
        Cursor cursor = db.query(ServiceDB.Settings._TABLE, new String[] { ServiceDB.Settings.VALUE },
                ServiceDB.Settings.NAME + "=?", new String[] { name }, null, null, null);
        try {
            if (cursor.moveToNext() && !cursor.isNull(0))
                return cursor.isNull(0) ? defaultValue : cursor.getInt(0);
            else
                return defaultValue;
        } finally {
            cursor.close();
        }
    }

    public void putInt(String name, int value) {
        ContentValues values = new ContentValues(2);
        values.put(ServiceDB.Settings.NAME, name);
        values.put(ServiceDB.Settings.VALUE, value);
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }


    @Nullable
    public String getString(String name, @Nullable String defaultValue) {
        Cursor cursor = db.query(ServiceDB.Settings._TABLE, new String[] { ServiceDB.Settings.VALUE },
                ServiceDB.Settings.NAME + "=?", new String[] { name }, null, null, null);
        try {
            if (cursor.moveToNext())
                return cursor.getString(0);
            else
                return defaultValue;
        } finally {
            cursor.close();
        }
    }

    public void putString(String name, @Nullable String value) {
        ContentValues values = new ContentValues(2);
        values.put(ServiceDB.Settings.NAME, name);
        values.put(ServiceDB.Settings.VALUE, value);
        db.insertWithOnConflict(ServiceDB.Settings._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }


    public void remove(String name) {
        db.delete(ServiceDB.Settings._TABLE, ServiceDB.Settings.NAME + "=?", new String[] { name });
    }

}
