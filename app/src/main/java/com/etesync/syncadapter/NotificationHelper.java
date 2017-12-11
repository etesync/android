package com.etesync.syncadapter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.ui.AccountSettingsActivity;
import com.etesync.syncadapter.ui.DebugInfoActivity;
import com.etesync.syncadapter.ui.WebViewActivity;

import java.util.logging.Level;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Getter;

public class NotificationHelper {
    final NotificationManagerCompat notificationManager;
    final Context context;
    final String notificationTag;
    final int notificationId;
    @Getter
    Intent detailsIntent;
    int messageString;

    private Throwable throwable = null;

    public NotificationHelper(Context context, String notificationTag, int notificationId) {
        this.notificationManager = NotificationManagerCompat.from(context);
        this.context = context;
        this.notificationTag = notificationTag;
        this.notificationId = notificationId;
    }

    public void setThrowable(Throwable e) {
        throwable = e;
        if (e instanceof Exceptions.UnauthorizedException) {
            App.log.log(Level.SEVERE, "Not authorized anymore", e);
            messageString = R.string.sync_error_unauthorized;
        } else if (e instanceof Exceptions.UserInactiveException) {
            App.log.log(Level.SEVERE, "User inactive");
            messageString = R.string.sync_error_user_inactive;
        } else if (e instanceof Exceptions.ServiceUnavailableException) {
            App.log.log(Level.SEVERE, "Service unavailable");
            messageString = R.string.sync_error_unavailable;
        } else if (e instanceof Exceptions.HttpException) {
            App.log.log(Level.SEVERE, "HTTP Exception during sync", e);
            messageString = R.string.sync_error_http_dav;
        } else if (e instanceof CalendarStorageException || e instanceof ContactsStorageException || e instanceof SQLiteException) {
            App.log.log(Level.SEVERE, "Couldn't access local storage", e);
            messageString = R.string.sync_error_local_storage;
        } else if (e instanceof Exceptions.IntegrityException) {
            App.log.log(Level.SEVERE, "Integrity error", e);
            messageString = R.string.sync_error_integrity;
        } else {
            App.log.log(Level.SEVERE, "Unknown sync error", e);
            messageString = R.string.sync_error;
        }

        detailsIntent = new Intent(context, NotificationHandlerActivity.class);
        detailsIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e);
        detailsIntent.setData(Uri.parse("uri://" + getClass().getName() + "/" + notificationTag));
    }

    public void notify(String title, String state) {
        String message = context.getString(messageString, state);
        notify(title, message, null, detailsIntent);
    }

    public void notify(String title, String content, String bigText, Intent intent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        int icon;
        String category;
        String tag;
        //Check if error was configured
        if (throwable == null) {
            icon = R.drawable.ic_sync_dark;
            category = NotificationCompat.CATEGORY_STATUS;
        } else {
            icon = R.drawable.ic_error_light;
            category = NotificationCompat.CATEGORY_ERROR;
        }

        builder.setLargeIcon(App.getLauncherBitmap(context))
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setCategory(category)
                .setSmallIcon(icon)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
        ;

        if (bigText != null) builder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(bigText));

        notificationManager.notify(notificationTag, notificationId, builder.build());
    }


    public void cancel() {
        notificationManager.cancel(notificationTag, notificationId);
    }

    public static class NotificationHandlerActivity extends Activity {

        @Override
        public void onCreate(Bundle savedBundle) {
            super.onCreate(savedBundle);
            Bundle extras = getIntent().getExtras();
            Exception e = (Exception) extras.get(DebugInfoActivity.KEY_THROWABLE);

            Intent detailsIntent;
            if (e instanceof Exceptions.UnauthorizedException) {
                detailsIntent = new Intent(this, AccountSettingsActivity.class);
            } else if (e instanceof Exceptions.UserInactiveException) {
                WebViewActivity.openUrl(this, Constants.dashboard);
                return;
            } else if (e instanceof AccountSettings.AccountMigrationException) {
                WebViewActivity.openUrl(this, Constants.faqUri.buildUpon().encodedFragment("account-migration-error").build());
                return;
            } else {
                detailsIntent = new Intent(this, DebugInfoActivity.class);
            }
            detailsIntent.putExtras(getIntent().getExtras());
            startActivity(detailsIntent);
        }

        @Override
        public void onStop() {
            super.onStop();
            finish();
        }
    }
}
