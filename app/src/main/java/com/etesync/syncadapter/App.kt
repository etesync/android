/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.content.*
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.StrictMode
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.*
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.AccountsActivity
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.LanguageUtils
import com.etesync.syncadapter.utils.NotificationUtils
import io.requery.Persistable
import io.requery.android.sqlite.DatabaseSource
import io.requery.meta.EntityModel
import io.requery.sql.EntityDataStore
import org.acra.ACRA
import org.jetbrains.anko.doAsync
import java.util.*


class App : Application() {
    /**
     * @return [EntityDataStore] single instance for the application.
     *
     *
     * Note if you're using Dagger you can make this part of your application level module returning
     * `@Provides @Singleton`.
     */
    // override onUpgrade to handle migrating to a new version
    val data: EntityDataStore<Persistable>
        get() = initDataStore()

    fun initDataStore(): EntityDataStore<Persistable> {
        val source = MyDatabaseSource(this, Models.DEFAULT, 4)
        val configuration = source.configuration
        return EntityDataStore(configuration)
    }

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        reinitLogger()
        StrictMode.enableDefaults()
        initPrefVersion()

        NotificationUtils.createChannels(this)

        appName = getString(R.string.app_name)
        accountType = getString(R.string.account_type)
        addressBookAccountType = getString(R.string.account_type_address_book)
        addressBooksAuthority = getString(R.string.address_books_authority)

        loadLanguage()

        // don't block UI for some background checks
        doAsync {
            // watch installed/removed apps
            val tasksFilter = IntentFilter()
            tasksFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            tasksFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            tasksFilter.addDataScheme("package")
            registerReceiver(PackageChangedReceiver(), tasksFilter)

            // check whether a tasks app is currently installed
            PackageChangedReceiver.updateTaskSync(this@App)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        if (!BuildConfig.DEBUG) {
            // The following line triggers the initialization of ACRA
            ACRA.init(this, AcraConfiguration.getConfig(this))
            val pm = base.packageManager
            val installedFrom = pm.getInstallerPackageName(BuildConfig.APPLICATION_ID)
            ACRA.getErrorReporter().putCustomData("installedFrom", installedFrom);
        }
    }

    private fun loadLanguage() {
        val serviceDB = ServiceDB.OpenHelper(this)
        val lang = Settings(serviceDB.readableDatabase).getString(App.FORCE_LANGUAGE, null)
        if (lang != null && lang != DEFAULT_LANGUAGE) {
            LanguageUtils.setLanguage(this, lang)
        }

        serviceDB.close()
    }

    fun reinitLogger() {
        Logger.initialize(this)
    }

    private class MyDatabaseSource internal constructor(context: Context, entityModel: EntityModel, version: Int) : DatabaseSource(context, entityModel, version) {

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            super.onUpgrade(db, oldVersion, newVersion)

            if (oldVersion < 3) {
                db.execSQL("PRAGMA foreign_keys=OFF;")

                db.execSQL("CREATE TABLE new_Journal (id integer primary key autoincrement not null, deleted boolean not null, encryptedKey varbinary(255), info varchar(255), owner varchar(255), service integer, serviceModel integer, uid varchar(64) not null, readOnly boolean default false, foreign key (serviceModel) references Service (id) on delete cascade);")
                db.execSQL("CREATE TABLE new_Entry (id integer primary key autoincrement not null, content varchar(255), journal integer, uid varchar(64) not null, foreign key (journal) references new_Journal (id) on delete cascade);")

                db.execSQL("INSERT INTO new_Journal SELECT id, deleted, encryptedKey, info, owner, service, serviceModel, uid, 0 from Journal;")
                db.execSQL("INSERT INTO new_Entry SELECT id, content, journal, uid from Entry;")

                db.execSQL("DROP TABLE Journal;")
                db.execSQL("DROP TABLE Entry;")
                db.execSQL("ALTER TABLE new_Journal RENAME TO Journal;")
                db.execSQL("ALTER TABLE new_Entry RENAME TO Entry;")
                // Add back indexes
                db.execSQL("CREATE UNIQUE INDEX journal_unique_together on Journal (serviceModel, uid);")
                db.execSQL("CREATE UNIQUE INDEX entry_unique_together on Entry (journal, uid);")
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }
    }

    /** Init the preferences version of the app.
     * This is used to initialise the first version if not alrady set.  */
    private fun initPrefVersion() {
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_VERSION, 0) == 0) {
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }

    private fun update(fromVersion: Int) {
        Logger.log.info("Updating from version " + fromVersion + " to " + BuildConfig.VERSION_CODE)

        if (fromVersion < 6) {
            val data = this.data

            val dbHelper = ServiceDB.OpenHelper(this)

            val collections = readCollections(dbHelper)
            for (info in collections) {
                val journalEntity = JournalEntity(data, info)
                data.insert(journalEntity)
            }

            val db = dbHelper.writableDatabase
            db.delete(ServiceDB.Collections._TABLE, null, null)
            db.close()
        }

        if (fromVersion < 7) {
            /* Fix all of the etags to be non-null */
            val am = AccountManager.get(this)
            for (account in am.getAccountsByType(App.accountType)) {
                try {
                    // Generate account settings to make sure account is migrated.
                    AccountSettings(this, account)

                    val calendars = AndroidCalendar.find(account, this.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!,
                            LocalCalendar.Factory, null, null)
                    for (calendar in calendars) {
                        calendar.fixEtags()
                    }
                } catch (e: CalendarStorageException) {
                    e.printStackTrace()
                } catch (e: InvalidAccountException) {
                    e.printStackTrace()
                }

            }

            for (account in am.getAccountsByType(App.addressBookAccountType)) {
                val addressBook = LocalAddressBook(this, account, this.contentResolver.acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI))
                try {
                    addressBook.fixEtags()
                } catch (e: ContactsStorageException) {
                    e.printStackTrace()
                }

            }
        }

        if (fromVersion < 10) {
            HintManager.setHintSeen(this, AccountsActivity.HINT_ACCOUNT_ADD, true)
        }

        if (fromVersion < 11) {
            val dbHelper = ServiceDB.OpenHelper(this)

            migrateServices(dbHelper)
        }
    }

    class AppUpdatedReceiver : BroadcastReceiver() {

        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Logger.log.info("EteSync was updated, checking for app version")

            val app = context.applicationContext as App
            val prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE)
            val fromVersion = prefs.getInt(PREF_VERSION, 1)
            app.update(fromVersion)
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply()
        }

    }

    private fun readCollections(dbHelper: ServiceDB.OpenHelper): List<CollectionInfo> {
        val db = dbHelper.writableDatabase
        val collections = LinkedList<CollectionInfo>()
        val cursor = db.query(ServiceDB.Collections._TABLE, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val values = ContentValues()
            DatabaseUtils.cursorRowToContentValues(cursor, values)
            collections.add(CollectionInfo.fromDB(values))
        }

        db.close()
        cursor.close()
        return collections
    }

    fun migrateServices(dbHelper: ServiceDB.OpenHelper) {
        val db = dbHelper.readableDatabase
        val data = this.data
        val cursor = db.query(ServiceDB.Services._TABLE, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val values = ContentValues()
            DatabaseUtils.cursorRowToContentValues(cursor, values)
            val service = ServiceEntity()
            service.account = values.getAsString(ServiceDB.Services.ACCOUNT_NAME)
            service.type = CollectionInfo.Type.valueOf(values.getAsString(ServiceDB.Services.SERVICE))
            data.insert(service)

            for (journalEntity in data.select(JournalEntity::class.java).where(JournalEntity.SERVICE.eq(values.getAsLong(ServiceDB.Services.ID))).get()) {
                journalEntity.serviceModel = service
                data.update(journalEntity)
            }
        }

        db.delete(ServiceDB.Services._TABLE, null, null)
        db.close()
        cursor.close()
    }

    companion object {
        val DISTRUST_SYSTEM_CERTIFICATES = "distrustSystemCerts"
        val LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage"
        val OVERRIDE_PROXY = "overrideProxy"
        val OVERRIDE_PROXY_HOST = "overrideProxyHost"
        val OVERRIDE_PROXY_PORT = "overrideProxyPort"
        val FORCE_LANGUAGE = "forceLanguage"
        val CHANGE_NOTIFICATION = "show_change_notification"

        val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        val OVERRIDE_PROXY_PORT_DEFAULT = 8118

        val DEFAULT_LANGUAGE = "default"
        var sDefaultLocacle = Locale.getDefault()

        var appName: String = "EteSync"

        lateinit var accountType: String
            private set
        lateinit var addressBookAccountType: String
            private set
        lateinit var addressBooksAuthority: String
            private set

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        fun getLauncherBitmap(context: Context): Bitmap? {
            var bitmapLogo: Bitmap? = null
            val drawableLogo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)

            if (drawableLogo is BitmapDrawable)
                bitmapLogo = drawableLogo.bitmap
            return bitmapLogo
        }

        // update from previous account settings

        private val PREF_VERSION = "version"
    }
}
