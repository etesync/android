package com.etesync.syncadapter.syncadapter

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.ui.AccountSettingsActivity
import com.etesync.syncadapter.ui.DebugInfoActivity
import com.etesync.syncadapter.ui.WebViewActivity
import com.etesync.syncadapter.utils.NotificationUtils
import java.util.logging.Level

class SyncNotification(internal val context: Context, internal val notificationTag: String, internal val notificationId: Int) {

    internal val notificationManager: NotificationManagerCompat
    lateinit var detailsIntent: Intent
        internal set
    internal var messageString: Int = 0

    private var throwable: Throwable? = null

    init {
        this.notificationManager = NotificationManagerCompat.from(context)
    }

    fun setThrowable(e: Throwable) {
        throwable = e
        if (e is Exceptions.UnauthorizedException) {
            Logger.log.log(Level.SEVERE, "Not authorized anymore", e)
            messageString = R.string.sync_error_unauthorized
        } else if (e is Exceptions.UserInactiveException) {
            Logger.log.log(Level.SEVERE, "User inactive")
            messageString = R.string.sync_error_user_inactive
        } else if (e is Exceptions.ServiceUnavailableException) {
            Logger.log.log(Level.SEVERE, "Service unavailable")
            messageString = R.string.sync_error_unavailable
        } else if (e is Exceptions.ReadOnlyException) {
            Logger.log.log(Level.SEVERE, "Journal is read only", e)
            messageString = R.string.sync_error_journal_readonly
        } else if (e is Exceptions.HttpException) {
            Logger.log.log(Level.SEVERE, "HTTP Exception during sync", e)
            messageString = R.string.sync_error_http_dav
        } else if (e is CalendarStorageException || e is ContactsStorageException || e is SQLiteException) {
            Logger.log.log(Level.SEVERE, "Couldn't access local storage", e)
            messageString = R.string.sync_error_local_storage
        } else if (e is Exceptions.IntegrityException) {
            Logger.log.log(Level.SEVERE, "Integrity error", e)
            messageString = R.string.sync_error_integrity
        } else {
            Logger.log.log(Level.SEVERE, "Unknown sync error", e)
            messageString = R.string.sync_error
        }

        detailsIntent = Intent(context, NotificationHandlerActivity::class.java)
        detailsIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e)
        detailsIntent.data = Uri.parse("uri://" + javaClass.name + "/" + notificationTag)
    }

    fun notify(title: String, state: String) {
        val message = context.getString(messageString, state)
        notify(title, message, null, detailsIntent)
    }

    @JvmOverloads
    fun notify(title: String, content: String, bigText: String?, intent: Intent, _icon: Int = -1) {
        var icon = _icon
        val category: String;
        val channel: String;
        if (throwable == null) {
            category = NotificationCompat.CATEGORY_STATUS
            channel = NotificationUtils.CHANNEL_SYNC_STATUS
        } else {
            category = NotificationCompat.CATEGORY_ERROR
            channel = NotificationUtils.CHANNEL_SYNC_ERRORS
        }
        val builder = NotificationUtils.newBuilder(context, channel)
        if (icon == -1) {
            //Check if error was configured
            if (throwable == null) {
                icon = R.drawable.ic_sync_dark
            } else {
                icon = R.drawable.ic_error_light
            }
        }

        builder .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setCategory(category)
                .setSmallIcon(icon)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

        if (bigText != null)
            builder.setStyle(NotificationCompat.BigTextStyle()
                    .bigText(bigText))

        notificationManager.notify(notificationTag, notificationId, builder.build())
    }


    fun cancel() {
        notificationManager.cancel(notificationTag, notificationId)
    }

    class NotificationHandlerActivity : Activity() {

        public override fun onCreate(savedBundle: Bundle?) {
            super.onCreate(savedBundle)
            val extras = intent.extras
            val e = extras!!.get(DebugInfoActivity.KEY_THROWABLE) as Exception

            val detailsIntent: Intent
            if (e is Exceptions.UnauthorizedException) {
                detailsIntent = Intent(this, AccountSettingsActivity::class.java)
            } else if (e is Exceptions.UserInactiveException) {
                WebViewActivity.openUrl(this, Constants.dashboard)
                return
            } else if (e is AccountSettings.AccountMigrationException) {
                WebViewActivity.openUrl(this, Constants.faqUri.buildUpon().encodedFragment("account-migration-error").build())
                return
            } else {
                detailsIntent = Intent(this, DebugInfoActivity::class.java)
            }
            detailsIntent.putExtras(intent.extras!!)
            startActivity(detailsIntent)
        }

        public override fun onStop() {
            super.onStop()
            finish()
        }
    }
}
