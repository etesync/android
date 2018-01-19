/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.StrictMode;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.etesync.syncadapter.log.LogcatHandler;
import com.etesync.syncadapter.log.PlainTextFormatter;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.Models;
import com.etesync.syncadapter.model.ServiceDB;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.model.Settings;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.ui.AccountsActivity;
import com.etesync.syncadapter.utils.HintManager;
import com.etesync.syncadapter.utils.LanguageUtils;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraToast;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.android.sqlite.DatabaseSource;
import io.requery.meta.EntityModel;
import io.requery.sql.Configuration;
import io.requery.sql.EntityDataStore;
import lombok.Cleanup;
import lombok.Getter;
import okhttp3.internal.tls.OkHostnameVerifier;

@AcraCore(buildConfigClass = BuildConfig.class)
@AcraMailSender(mailTo = "reports@etesync.com",
        reportFileName = "ACRA-report.stacktrace.json")
@AcraToast(resText = R.string.crash_message,
        length = Toast.LENGTH_LONG)
public class App extends Application {
    public static final String
            DISTRUST_SYSTEM_CERTIFICATES = "distrustSystemCerts",
            LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage",
            OVERRIDE_PROXY = "overrideProxy",
            OVERRIDE_PROXY_HOST = "overrideProxyHost",
            OVERRIDE_PROXY_PORT = "overrideProxyPort",
            FORCE_LANGUAGE = "forceLanguage";

    public static final String OVERRIDE_PROXY_HOST_DEFAULT = "localhost";
    public static final int OVERRIDE_PROXY_PORT_DEFAULT = 8118;

    public static final String DEFAULT_LANGUAGE = "default";
    public static Locale sDefaultLocacle = Locale.getDefault();

    @Getter
    private static String appName;

    @Getter
    private CustomCertManager certManager;

    @Getter
    private static SSLSocketFactoryCompat sslSocketFactoryCompat;

    @Getter
    private static HostnameVerifier hostnameVerifier;

    public final static Logger log = Logger.getLogger("syncadapter");
    static {
        at.bitfire.cert4android.Constants.log = Logger.getLogger("syncadapter.cert4android");
    }

    @Getter
    private static String accountType;
    @Getter
    private static String addressBookAccountType;
    @Getter
    private static String addressBooksAuthority;

    @Override
    @SuppressLint("HardwareIds")
    public void onCreate() {
        super.onCreate();
        reinitCertManager();
        reinitLogger();
        StrictMode.enableDefaults();
        initPrefVersion();

        appName = getString(R.string.app_name);
        accountType = getString(R.string.account_type);
        addressBookAccountType = getString(R.string.account_type_address_book);
        addressBooksAuthority = getString(R.string.address_books_authority);

        loadLanguage();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }

    private void loadLanguage() {
        @Cleanup ServiceDB.OpenHelper serviceDB = new ServiceDB.OpenHelper(this);
        String lang = new Settings(serviceDB.getReadableDatabase()).getString(App.FORCE_LANGUAGE, null);
        if (lang != null && !lang.equals(DEFAULT_LANGUAGE)) {
            LanguageUtils.setLanguage(this, lang);
        }
    }

    public void reinitCertManager() {
        if (BuildConfig.customCerts) {
            if (certManager != null)
                certManager.close();

            @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(this);
            Settings settings = new Settings(dbHelper.getReadableDatabase());

            certManager = new CustomCertManager(this, !settings.getBoolean(DISTRUST_SYSTEM_CERTIFICATES, false));
            sslSocketFactoryCompat = new SSLSocketFactoryCompat(certManager);
            hostnameVerifier = certManager.hostnameVerifier(OkHostnameVerifier.INSTANCE);
        }
    }

    public void reinitLogger() {
        @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(this);
        Settings settings = new Settings(dbHelper.getReadableDatabase());

        boolean logToFile = settings.getBoolean(LOG_TO_EXTERNAL_STORAGE, false),
                logVerbose = logToFile || Log.isLoggable(log.getName(), Log.DEBUG);

        App.log.info("Verbose logging: " + logVerbose);

        // set logging level according to preferences
        final Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(logVerbose ? Level.ALL : Level.INFO);

        // remove all handlers and add our own logcat handler
        rootLogger.setUseParentHandlers(false);
        for (Handler handler : rootLogger.getHandlers())
            rootLogger.removeHandler(handler);
        rootLogger.addHandler(LogcatHandler.INSTANCE);

        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        // log to external file according to preferences
        if (logToFile) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder .setSmallIcon(R.drawable.ic_sd_storage_light)
                    .setLargeIcon(getLauncherBitmap(this))
                    .setContentTitle(getString(R.string.logging_davdroid_file_logging))
                    .setLocalOnly(true);

            File dir = getExternalFilesDir(null);
            if (dir != null)
                try {
                    String fileName = new File(dir, "etesync-" + Process.myPid() + "-" +
                            DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss") + ".txt").toString();
                    log.info("Logging to " + fileName);

                    FileHandler fileHandler = new FileHandler(fileName);
                    fileHandler.setFormatter(PlainTextFormatter.DEFAULT);
                    log.addHandler(fileHandler);
                    builder .setContentText(dir.getPath())
                            .setSubText(getString(R.string.logging_to_external_storage_warning))
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(getString(R.string.logging_to_external_storage, dir.getPath())))
                            .setOngoing(true);

                } catch (IOException e) {
                    log.log(Level.SEVERE, "Couldn't create external log file", e);

                    builder .setContentText(getString(R.string.logging_couldnt_create_file, e.getLocalizedMessage()))
                            .setCategory(NotificationCompat.CATEGORY_ERROR);
                }
            else
                builder.setContentText(getString(R.string.logging_no_external_storage));

            nm.notify(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING, builder.build());
        } else
            nm.cancel(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING);
    }

    @Nullable
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bitmap getLauncherBitmap(@NonNull Context context) {
        Bitmap bitmapLogo = null;
        Drawable drawableLogo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher);

        if (drawableLogo instanceof BitmapDrawable)
            bitmapLogo = ((BitmapDrawable)drawableLogo).getBitmap();
        return bitmapLogo;
    }


    public static class ReinitSettingsReceiver extends BroadcastReceiver {

        public static final String ACTION_REINIT_SETTINGS = BuildConfig.APPLICATION_ID + ".REINIT_SETTINGS";

        @Override
        public void onReceive(Context context, Intent intent) {
            log.info("Received broadcast: re-initializing settings (logger/cert manager)");

            App app = (App)context.getApplicationContext();
            app.reinitLogger();
        }

    }

    private EntityDataStore<Persistable> dataStore;

    /**
     * @return {@link EntityDataStore} single instance for the application.
     * <p/>
     * Note if you're using Dagger you can make this part of your application level module returning
     * {@code @Provides @Singleton}.
     */
    public EntityDataStore<Persistable> getData() {
        if (dataStore == null) {
            // override onUpgrade to handle migrating to a new version
            DatabaseSource source = new MyDatabaseSource(this, Models.DEFAULT, 4);
            Configuration configuration = source.getConfiguration();
            dataStore = new EntityDataStore<>(configuration);
        }
        return dataStore;
    }

    private static class MyDatabaseSource extends DatabaseSource {
        MyDatabaseSource(Context context, EntityModel entityModel, int version) {
            super(context, entityModel, version);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            super.onUpgrade(db, oldVersion, newVersion);

            if (oldVersion < 3) {
                db.execSQL("PRAGMA foreign_keys=OFF;");

                db.execSQL("CREATE TABLE new_Journal (id integer primary key autoincrement not null, deleted boolean not null, encryptedKey varbinary(255), info varchar(255), owner varchar(255), service integer, serviceModel integer, uid varchar(64) not null, readOnly boolean default false, foreign key (serviceModel) references Service (id) on delete cascade);");
                db.execSQL("CREATE TABLE new_Entry (id integer primary key autoincrement not null, content varchar(255), journal integer, uid varchar(64) not null, foreign key (journal) references new_Journal (id) on delete cascade);");

                db.execSQL("INSERT INTO new_Journal SELECT id, deleted, encryptedKey, info, owner, service, serviceModel, uid, 0 from Journal;");
                db.execSQL("INSERT INTO new_Entry SELECT id, content, journal, uid from Entry;");

                db.execSQL("DROP TABLE Journal;");
                db.execSQL("DROP TABLE Entry;");
                db.execSQL("ALTER TABLE new_Journal RENAME TO Journal;");
                db.execSQL("ALTER TABLE new_Entry RENAME TO Entry;");
                // Add back indexes
                db.execSQL("CREATE UNIQUE INDEX journal_unique_together on Journal (serviceModel, uid);");
                db.execSQL("CREATE UNIQUE INDEX entry_unique_together on Entry (journal, uid);");
                db.execSQL("PRAGMA foreign_keys=ON;");
            }
        }
    }

    // update from previous account settings

    private final static String PREF_VERSION = "version";

    /** Init the preferences version of the app.
     * This is used to initialise the first version if not alrady set. */
    private void initPrefVersion() {
        SharedPreferences prefs = getSharedPreferences("app", Context.MODE_PRIVATE);
        if (prefs.getInt(PREF_VERSION, 0) == 0) {
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply();
        }
    }

    private void update(int fromVersion) {
        App.log.info("Updating from version " + fromVersion + " to " + BuildConfig.VERSION_CODE);

        if (fromVersion < 6) {
            EntityDataStore<Persistable> data = this.getData();

            ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(this);

            List<CollectionInfo> collections = readCollections(dbHelper);
            for (CollectionInfo info : collections) {
                JournalEntity journalEntity = new JournalEntity(data, info);
                data.insert(journalEntity);
            }

            @Cleanup SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(ServiceDB.Collections._TABLE, null, null);
        }

        if (fromVersion < 7) {
            /* Fix all of the etags to be non-null */
            AccountManager am = AccountManager.get(this);
            for (Account account : am.getAccountsByType(App.getAccountType())) {
                try {
                    // Generate account settings to make sure account is migrated.
                    new AccountSettings(this, account);

                    LocalCalendar calendars[] = (LocalCalendar[]) LocalCalendar.find(account, this.getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI),
                            LocalCalendar.Factory.INSTANCE, null, null);
                    for (LocalCalendar calendar : calendars) {
                        calendar.fixEtags();
                    }
                } catch (CalendarStorageException|InvalidAccountException e) {
                    e.printStackTrace();
                }
            }

            for (Account account : am.getAccountsByType(App.getAddressBookAccountType())) {
                LocalAddressBook addressBook = new LocalAddressBook(this, account, this.getContentResolver().acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI));
                try {
                    addressBook.fixEtags();
                } catch (ContactsStorageException e) {
                    e.printStackTrace();
                }
            }
        }

        if (fromVersion < 10) {
            HintManager.setHintSeen(this, AccountsActivity.HINT_ACCOUNT_ADD, true);
        }

        if (fromVersion < 11) {
            ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(this);

            migrateServices(dbHelper);
        }
    }

    public static class AppUpdatedReceiver extends BroadcastReceiver {

        @Override
        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        public void onReceive(Context context, Intent intent) {
            App.log.info("EteSync was updated, checking for app version");

            App app = (App) context.getApplicationContext();
            SharedPreferences prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE);
            int fromVersion = prefs.getInt(PREF_VERSION, 1);
            app.update(fromVersion);
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply();
        }

    }

    @NonNull
    private List<CollectionInfo> readCollections(ServiceDB.OpenHelper dbHelper) {
        @Cleanup SQLiteDatabase db = dbHelper.getWritableDatabase();
        List<CollectionInfo> collections = new LinkedList<>();
        @Cleanup Cursor cursor = db.query(ServiceDB.Collections._TABLE, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, values);
            collections.add(CollectionInfo.fromDB(values));
        }
        return collections;
    }

    public void migrateServices(ServiceDB.OpenHelper dbHelper) {
        @Cleanup SQLiteDatabase db = dbHelper.getReadableDatabase();
        EntityDataStore<Persistable> data = this.getData();
        @Cleanup Cursor cursor = db.query(ServiceDB.Services._TABLE, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, values);
            ServiceEntity service = new ServiceEntity();
            service.setAccount(values.getAsString(ServiceDB.Services.ACCOUNT_NAME));
            service.setType(CollectionInfo.Type.valueOf(values.getAsString(ServiceDB.Services.SERVICE)));
            data.insert(service);

            for (JournalEntity journalEntity : data.select(JournalEntity.class).where(JournalEntity.SERVICE.eq(values.getAsLong(ServiceDB.Services.ID))).get()) {
                journalEntity.setServiceModel(service);
                data.update(journalEntity);
            }
        }

        db.delete(ServiceDB.Services._TABLE, null, null);
    }
}
