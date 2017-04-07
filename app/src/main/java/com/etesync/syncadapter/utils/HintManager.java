package com.etesync.syncadapter.utils;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedList;
import java.util.List;

public class HintManager {
    private final static String PREF_NAME = "hints";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setHintSeen(Context context, String hint, boolean seen) {
        SharedPreferences prefs = getPrefs(context);

        prefs.edit().putBoolean(hint, seen).apply();
    }

    public static boolean getHintSeen(Context context, String hint) {
        SharedPreferences prefs = getPrefs(context);

        return prefs.getBoolean(hint, false);
    }

    public static void resetHints(Context context) {
        SharedPreferences prefs = getPrefs(context);

        prefs.edit().clear().apply();
    }
}
