/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.*
import android.os.Bundle
import android.provider.CalendarContract
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.CalendarStorageException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.resource.LocalCalendar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*

class CalendarsSyncAdapterService : SyncAdapterService() {
    override fun syncAdapter(): AbstractThreadedSyncAdapter {
        return SyncAdapter(this)
    }


    private class SyncAdapter(context: Context) : SyncAdapterService.SyncAdapter(context) {
        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            val settings = AccountSettings(context, account)
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                return

            RefreshCollections(account, CollectionInfo.Type.CALENDAR).run()

            if (settings.isLegacy) {
                legacyUpdateLocalCalendars(provider, account, settings)
            } else {
                updateLocalCalendars(provider, account, settings)
            }

            val principal = settings.uri?.toHttpUrlOrNull()!!

            for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                Logger.log.info("Synchronizing calendar #" + calendar.id + ", URL: " + calendar.name)
                CalendarSyncManager(context, account, settings, extras, authority, syncResult, calendar, principal).use {
                    it.performSync()
                }
            }

            Logger.log.info("Calendar sync complete")
        }

        private fun updateLocalCalendars(provider: ContentProviderClient, account: Account, settings: AccountSettings) {
            val remote = HashMap<String, CachedCollection>()
            val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
            val collections: List<CachedCollection>
            synchronized(etebaseLocalCache) {
                val httpClient = HttpClient.Builder(context, settings).setForeground(false).build()
                val etebase = EtebaseLocalCache.getEtebase(context, httpClient.okHttpClient, settings)
                val colMgr = etebase.collectionManager

                collections = etebaseLocalCache.collectionList(colMgr).filter { it.meta.collectionType == Constants.ETEBASE_TYPE_CALENDAR }
            }

            for (collection in collections) {
                remote[collection.col.uid] = collection
            }

            val local = AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null)

            val updateColors = settings.manageCalendarColors

            // delete obsolete local calendar
            for (calendar in local) {
                val url = calendar.name
                val collection = remote[url]
                if (collection == null) {
                    Logger.log.fine("Deleting obsolete local calendar $url")
                    calendar.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local calendar $url")
                    calendar.update(collection, updateColors)
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local calendars
            for (url in remote.keys) {
                val cachedCollection = remote[url]!!
                Logger.log.info("Adding local calendar list $cachedCollection")
                LocalCalendar.create(account, provider, cachedCollection)
            }
        }

        @Throws(CalendarStorageException::class)
        private fun legacyUpdateLocalCalendars(provider: ContentProviderClient, account: Account, settings: AccountSettings) {
            val data = (context.applicationContext as App).data
            val service = JournalModel.Service.fetchOrCreate(data, account.name, CollectionInfo.Type.CALENDAR)

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
                    Logger.log.fine("Deleting obsolete local calendar $url")
                    calendar.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local calendar $url with $journalEntity")
                    calendar.update(journalEntity, updateColors)
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local calendars
            for (url in remote.keys) {
                val journalEntity = remote[url]!!
                Logger.log.info("Adding local calendar list $journalEntity")
                LocalCalendar.create(account, provider, journalEntity)
            }
        }
    }

}
