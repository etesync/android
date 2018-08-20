package com.etesync.syncadapter;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
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

public class NotificationHelper {
    private static final String CHANNEL_ID = "EteSync_default";

    final NotificationManagerCompat notificationManager;
    final Context context;
    final String notificationTag;
    final int notificationId;
    Intent detailsIntent;
    int messageString;

    public Intent getDetailsIntent() {
        return detailsIntent;
    }

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
        notify(title, content, bigText, intent, -1);
    }

    public void notify(String title, String content, String bigText, Intent intent, int icon) {
        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        String category =
                throwable == null ?
                        NotificationCompat.CATEGORY_STATUS : NotificationCompat.CATEGORY_ERROR;
        if (icon == -1) {
            //Check if error was configured
            if (throwable == null) {
                icon = R.drawable.ic_sync_dark;
            } else {
                icon = R.drawable.ic_error_light;
            }
        }

        builder.setLargeIcon(App.getLauncherBitmap(context))
                .setContentTitle(title)
                .setContentText(content)
                .setChannelId(CHANNEL_ID)
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_channel_name);
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

}
