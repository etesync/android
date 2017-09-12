package com.etesync.syncadapter.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Created by tal on 11/09/17.
 */

public class LanguageUtils {

    public static LocaleList getAppLanguages(Context context) {
        Locale[] locales = Locale.getAvailableLocales();
        Arrays.sort(locales, new Comparator<Locale>() {
            @Override
            public int compare(Locale aLocale, Locale aT1) {
                return aLocale.getDisplayName().compareTo(aT1.getDisplayName());
            }
        });
        String[] localeData = new String[locales.length + 1];
        String[] displayNames = new String[locales.length + 1];
        localeData[0] = App.DEFAULT_LANGUAGE;
        displayNames[0] = context.getString(R.string.app_settings_force_language_default);
        int index = 1;
        for (Locale locale : locales) {
            localeData[index] = encodeLocale(locale);
            displayNames[index] = locale.getDisplayName();
            index++;
        }
        return new LocaleList(localeData, displayNames);
    }

    public static void setLanguage(Context context, String locale) {
        if (locale.equals(App.DEFAULT_LANGUAGE)) setLanguage(context, App.sDefaultLocacle);
        else setLanguage(context, decodeLocale(locale));
    }

    @SuppressWarnings("deprecation")
    public static void setLanguage(Context context, Locale locale) {
        context = context.getApplicationContext();
        Locale.setDefault(locale);
        Configuration config = new Configuration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            context.getResources().updateConfiguration(
                    config, context.getResources().getDisplayMetrics());
        }
        context.getApplicationContext().getResources().updateConfiguration(config,
                context.getResources().getDisplayMetrics());
    }

    private static String encodeLocale(Locale locale) {
        return String.format("%s;%s;%s",
                locale.getLanguage(), locale.getCountry(), locale.getVariant());
    }

    private static Locale decodeLocale(String encodedLocale) {
        String[] data = encodedLocale.split(";", -1);
        return new Locale(data[0], data[1], data[2]);
    }

    public static class LocaleList {
        private final String[] mLocaleData;

        private final String[] mDisplayNames;

        LocaleList(String[] localeData, String[] displayName) {
            mLocaleData = localeData;
            mDisplayNames = displayName;
        }

        public String[] getDisplayNames() {
            return mDisplayNames;
        }
        public String[] getLocaleData() {
            return mLocaleData;
        }

    }
}
