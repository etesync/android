package com.etesync.syncadapter.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.etesync.syncadapter.App
import com.etesync.syncadapter.R
import java.util.*

/**
 * Created by tal on 11/09/17.
 */

object LanguageUtils {

    fun getAppLanguages(context: Context): LocaleList {
        val locales = Locale.getAvailableLocales()
        Arrays.sort(locales) { aLocale, aT1 -> aLocale.displayName.compareTo(aT1.displayName) }
        val localeData = arrayOfNulls<String>(locales.size + 1)
        val displayNames = arrayOfNulls<String>(locales.size + 1)
        localeData[0] = App.DEFAULT_LANGUAGE
        displayNames[0] = context.getString(R.string.app_settings_force_language_default)
        var index = 1
        for (locale in locales) {
            localeData[index] = encodeLocale(locale)
            displayNames[index] = locale.displayName
            index++
        }
        return LocaleList(localeData as Array<String>, displayNames as Array<String>)
    }

    fun setLanguage(context: Context, locale: String) {
        if (locale == App.DEFAULT_LANGUAGE)
            setLanguage(context, App.sDefaultLocacle)
        else
            setLanguage(context, decodeLocale(locale))
    }

    fun setLanguage(context: Context, locale: Locale) {
        var context = context
        context = context.applicationContext
        Locale.setDefault(locale)
        val config = Configuration()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            config.locale = locale
            context.resources.updateConfiguration(
                    config, context.resources.displayMetrics)
        }
        context.applicationContext.resources.updateConfiguration(config,
                context.resources.displayMetrics)
    }

    private fun encodeLocale(locale: Locale): String {
        return String.format("%s;%s;%s",
                locale.language, locale.country, locale.variant)
    }

    private fun decodeLocale(encodedLocale: String): Locale {
        val data = encodedLocale.split(";".toRegex()).toTypedArray()
        return Locale(data[0], data[1], data[2])
    }

    class LocaleList internal constructor(val localeData: Array<String>, val displayNames: Array<String>)
}
