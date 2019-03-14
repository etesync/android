package com.etesync.syncadapter.ui.importlocal

import android.accounts.Account
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.ContactsContract
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.resource.LocalCalendar
import java.util.*

/**
 * Created by tal on 27/03/17.
 */

class CalendarAccount protected constructor(val account: Account) {
    private val calendars = ArrayList<LocalCalendar>()

    val accountName: String
        get() = account.name

    val accountType: String
        get() = account.type

    fun getCalendars(): List<LocalCalendar> {
        return calendars
    }

    override fun toString(): String {
        return account.toString()
    }

    companion object {

        private val CAL_COLS = arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.DELETED, Calendars.NAME)

        // Load all available calendars.
        // If an empty list is returned the caller probably needs to enable calendar
        // read permissions in App Ops/XPrivacy etc.
        fun loadAll(resolver: ContentResolver): List<CalendarAccount> {

            if (missing(resolver, Calendars.CONTENT_URI) || missing(resolver, Events.CONTENT_URI))
                return ArrayList()

            var cur: Cursor?
            try {
                cur = resolver.query(Calendars.CONTENT_URI,
                        CAL_COLS, null, null,
                        ContactsContract.RawContacts.ACCOUNT_NAME + " ASC, " + ContactsContract.RawContacts.ACCOUNT_TYPE)
            } catch (except: Exception) {
                Logger.log.warning("Calendar provider is missing columns, continuing anyway")
                cur = resolver.query(Calendars.CONTENT_URI, null, null, null, null)
                except.printStackTrace()
            }

            val calendarAccounts = ArrayList<CalendarAccount>(cur!!.count)

            var calendarAccount: CalendarAccount? = null

            val contentProviderClient = resolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!
            while (cur.moveToNext()) {
                if (getLong(cur, Calendars.DELETED) != 0L)
                    continue

                val accountName = getString(cur, Calendars.ACCOUNT_NAME)
                val accountType = getString(cur, Calendars.ACCOUNT_TYPE)
                if (calendarAccount == null ||
                        calendarAccount.accountName != accountName ||
                        calendarAccount.accountType != accountType) {
                    calendarAccount = CalendarAccount(Account(accountName, accountType))
                    calendarAccounts.add(calendarAccount)
                }

                try {
                    val localCalendar = LocalCalendar.findByName(calendarAccount.account,
                            contentProviderClient,
                            LocalCalendar.Factory, getString(cur, Calendars.NAME)!!)
                    if (localCalendar != null) calendarAccount.calendars.add(localCalendar)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }
            contentProviderClient.release()
            cur.close()
            return calendarAccounts
        }

        private fun getColumnIndex(cur: Cursor?, dbName: String?): Int {
            return if (dbName == null) -1 else cur!!.getColumnIndex(dbName)
        }

        private fun getLong(cur: Cursor?, dbName: String): Long {
            val i = getColumnIndex(cur, dbName)
            return if (i == -1) -1 else cur!!.getLong(i)
        }

        private fun getString(cur: Cursor?, dbName: String): String? {
            val i = getColumnIndex(cur, dbName)
            return if (i == -1) null else cur!!.getString(i)
        }

        private fun missing(resolver: ContentResolver, uri: Uri): Boolean {
            // Determine if a provider is missing
            val provider = resolver.acquireContentProviderClient(uri)
            provider?.release()
            return provider == null
        }
    }
}
