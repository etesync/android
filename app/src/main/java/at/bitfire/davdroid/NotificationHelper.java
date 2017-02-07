package at.bitfire.davdroid;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import java.util.logging.Level;

import at.bitfire.davdroid.journalmanager.Exceptions;
import at.bitfire.davdroid.ui.AccountSettingsActivity;
import at.bitfire.davdroid.ui.DebugInfoActivity;
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

    public NotificationHelper(Context context, String notificationTag, int notificationId) {
        this.notificationManager = NotificationManagerCompat.from(context);
        this.context = context;
        this.notificationTag = notificationTag;
        this.notificationId = notificationId;
    }

    public void setThrowable(Throwable e) {
        if (e instanceof Exceptions.UnauthorizedException) {
            App.log.log(Level.SEVERE, "Not authorized anymore", e);
            messageString = R.string.sync_error_unauthorized;
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

        if (e instanceof Exceptions.UnauthorizedException) {
            detailsIntent = new Intent(context, AccountSettingsActivity.class);
        } else {
            detailsIntent = new Intent(context, DebugInfoActivity.class);
            detailsIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e);
        }

        detailsIntent.setData(Uri.parse("uri://" + getClass().getName() + "/" + notificationTag));
    }

    public void notify(String title, String state) {
        String message = context.getString(messageString, state);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.ic_error_light)
                .setLargeIcon(App.getLauncherBitmap(context))
                .setContentTitle(title)
                .setContentIntent(PendingIntent.getActivity(context, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentText(message);

        notificationManager.notify(notificationTag, notificationId, builder.build());
    }

    public void cancel() {
        notificationManager.cancel(notificationTag, notificationId);
    }
}
