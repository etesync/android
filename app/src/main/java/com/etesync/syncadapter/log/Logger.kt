/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.log

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.AppSettingsActivity
import com.etesync.syncadapter.utils.NotificationUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level

@SuppressLint("StaticFieldLeak")    // we'll only keep an app context
object Logger : SharedPreferences.OnSharedPreferenceChangeListener {

    private const val LOG_TO_FILE = "log_to_file"
    private const val LOG_VERBOSE = "log_verbose"

    val log = java.util.logging.Logger.getLogger("etesync")!!

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    fun initialize(someContext: Context) {
        context = someContext.applicationContext
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)

        reinitialize()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == LOG_TO_FILE || key == LOG_VERBOSE) {
            log.info("Logging settings changed; re-initializing logger")
            reinitialize()
        }
    }

    private fun reinitialize() {
        val logToFile = preferences.getBoolean(LOG_TO_FILE, false)
        val logVerbose = preferences.getBoolean(LOG_VERBOSE, false) || Log.isLoggable(Logger.log.name, Log.DEBUG)

        log.info("Verbose logging: $logVerbose; to file: $logToFile")

        // set logging level according to preferences
        val rootLogger = java.util.logging.Logger.getLogger("")
        rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

        // remove all handlers and add our own logcat handler
        rootLogger.useParentHandlers = false
        rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        rootLogger.addHandler(LogcatHandler)

        val nm = NotificationManagerCompat.from(context)
        // log to external file according to preferences
        if (logToFile) {
            val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_DEBUG)
            builder.setSmallIcon(R.drawable.ic_sd_storage_light)
                    .setLargeIcon(App.getLauncherBitmap(context))
                    .setContentTitle(context.getString(R.string.logging_davdroid_file_logging))

            val logDir = debugDir(context) ?: return
            val logFile = File(logDir,
                    "etesync-${Process.myPid()}-${DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss")}.txt")

            try {
                val fileHandler = FileHandler(logFile.toString(), true)
                fileHandler.formatter = PlainTextFormatter.DEFAULT
                rootLogger.addHandler(fileHandler)

                val prefIntent = Intent(context, AppSettingsActivity::class.java)
                prefIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                builder .setContentText(logDir.path)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSubText(context.getString(R.string.logging_to_external_storage_warning))
                        .setContentIntent(PendingIntent.getActivity(context, 0, prefIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setStyle(NotificationCompat.BigTextStyle()
                                .bigText(context.getString(R.string.logging_to_external_storage, logDir.path)))
                        .setOngoing(true)
            } catch(e: IOException) {
                log.log(Level.SEVERE, "Couldn't create log file", e)
                Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
            }

            nm.notify(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING, builder.build())
        } else {
            nm.cancel(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING)

            // delete old logs
            debugDir(context)?.deleteRecursively()
        }
    }

    private fun debugDir(context: Context): File? {
        val dir = File(context.getExternalFilesDir(null), "debug")
        if (dir.exists() && dir.isDirectory)
            return dir

        if (dir.mkdir())
            return dir

        Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
        return null
    }
}