package com.etesync.syncadapter.utils;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedList;
import java.util.List;

public class HintManager {
    private final static String PREF_NAME = "hints";
    private static List<Hint> hints = new LinkedList<>();

    public static Hint registerHint(String hint) {
        hint = "hint_" + hint;
        Hint ret = new Hint(hint);
        hints.add(ret);

        return ret;
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setHintSeen(Context context, Hint hint, boolean seen) {
        SharedPreferences prefs = getPrefs(context);

        prefs.edit().putBoolean(hint.string, seen).apply();
    }

    public static boolean getHintSeen(Context context, Hint hint) {
        SharedPreferences prefs = getPrefs(context);

        return prefs.getBoolean(hint.string, false);
    }

    public static void resetHints(Context context) {
        for (Hint hint : hints) {
            setHintSeen(context, hint, false);
        }
    }

    public static class Hint {
        private String string;
        private Hint(String hint) {
            this.string = hint;
        }
    }
}
