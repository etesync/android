/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.provider.CalendarContract
import at.bitfire.ical4android.AndroidCalendar

import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.NotificationHelper
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.model.ServiceEntity
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.DebugInfoActivity

import java.util.HashMap
import java.util.logging.Level

import at.bitfire.ical4android.CalendarStorageException
import io.requery.Persistable
import io.requery.sql.EntityDataStore
import okhttp3.HttpUrl

import com.etesync.syncadapter.Constants.KEY_ACCOUNT

class CalendarsSyncAdapterService : SyncAdapterService() {

    override fun syncAdapter(): AbstractThreadedSyncAdapter {
        return SyncAdapter(this)
    }


    private class SyncAdapter(context: Context) : SyncAdapterService.SyncAdapter(context) {

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult)

            val notificationManager = NotificationHelper(context, "journals-calendar", Constants.NOTIFICATION_CALENDAR_SYNC)
            notificationManager.cancel()

            try {
                val settings = AccountSettings(context, account)
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return

                RefreshCollections(account, CollectionInfo.Type.CALENDAR).run()

                updateLocalCalendars(provider, account, settings)

                val principal = HttpUrl.get(settings.uri!!)!!

                for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                    App.log.info("Synchronizing calendar #" + calendar.id + ", URL: " + calendar.name)
                    val syncManager = CalendarSyncManager(context, account, settings, extras, authority, syncResult, calendar, principal)
                    syncManager.performSync()
                }
            } catch (e: Exceptions.ServiceUnavailableException) {
                syncResult.stats.numIoExceptions++
                syncResult.delayUntil = if (e.retryAfter > 0) e.retryAfter else Constants.DEFAULT_RETRY_DELAY
            } catch (e: Exception) {
                if (e is CalendarStorageException || e is SQLiteException) {
                    App.log.log(Level.SEVERE, "Couldn't prepare local calendars", e)
                    syncResult.databaseError = true
                }

                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(R.string.sync_error_calendar, account.name)

                notificationManager.setThrowable(e)

                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(KEY_ACCOUNT, account)
                if (e !is Exceptions.UnauthorizedException) {
                    detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
                    detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase)
                }

                notificationManager.notify(title, context.getString(syncPhase))
            } catch (e: OutOfMemoryError) {
                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(R.string.sync_error_calendar, account.name)
                notificationManager.setThrowable(e)
                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(KEY_ACCOUNT, account)
                notificationManager.notify(title, context.getString(syncPhase))
            }

            App.log.info("Calendar sync complete")
        }

        @Throws(CalendarStorageException::class)
        private fun updateLocalCalendars(provider: ContentProviderClient, account: Account, settings: AccountSettings) {
            val data = (context.applicationContext as App).data
            val service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.CALENDAR)

            val remote = HashMap<String, JournalEntity>()
            val remoteJournals = JournalEntity.getJournals(data, service)
            for (journalEntity in remoteJournals) {
                remote[journalEntity.uid] = journalEntity
            }

            val local = AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null)

            val updateColors = settings.manageCalendarColors

            // delete obsolete local calendar
            for (calendar in local) {
                val url = calendar.name
                val journalEntity = remote[url]
                if (journalEntity == null) {
                    App.log.fine("Deleting obsolete local calendar $url")
                    calendar.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    App.log.fine("Updating local calendar $url with $journalEntity")
                    calendar.update(journalEntity, updateColors)
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local calendars
            for (url in remote.keys) {
                val journalEntity = remote[url]!!
                App.log.info("Adding local calendar list $journalEntity")
                LocalCalendar.create(account, provider, journalEntity)
            }
        }
    }

}
