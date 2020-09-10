/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.database.sqlite.SQLiteException
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Pair
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import com.etebase.client.FetchOptions
import com.etebase.client.exceptions.ConnectionException
import com.etebase.client.exceptions.TemporaryServerErrorException
import com.etebase.client.exceptions.UnauthorizedException
import com.etesync.syncadapter.*
import com.etesync.journalmanager.Crypto
import com.etesync.journalmanager.Exceptions
import com.etesync.journalmanager.JournalManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import com.etesync.syncadapter.ui.DebugInfoActivity
import com.etesync.syncadapter.ui.PermissionsActivity
import com.etesync.syncadapter.utils.NotificationUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.lang.Math.abs
import java.util.*
import java.util.logging.Level

//import com.android.vending.billing.IInAppBillingService;

typealias JournalList = List<Pair<JournalManager.Journal, CollectionInfo>>

class CachedJournalFetcher {
    private val cache: HashMap<String, Pair<Long, JournalList>> = HashMap()

    private fun fetchJournals(journalsManager: JournalManager, settings: AccountSettings, serviceType: CollectionInfo.Type): JournalList {
        val journals = LinkedList<Pair<JournalManager.Journal, CollectionInfo>>()

        for (journal in journalsManager.list()) {
            val crypto: Crypto.CryptoManager
            if (journal.key != null) {
                crypto = Crypto.CryptoManager(journal.version, settings.keyPair!!, journal.key!!)
            } else {
                crypto = Crypto.CryptoManager(journal.version, settings.password(), journal.uid!!)
            }

            journal.verify(crypto)

            val info = CollectionInfo.fromJson(journal.getContent(crypto))
            info.updateFromJournal(journal)

            journals.add(Pair(journal, info))
        }

        return journals;
    }

    fun list(journalsManager: JournalManager, settings: AccountSettings, serviceType: CollectionInfo.Type): JournalList {
        val cacheAge = 5 * 1000 // 5 seconds - it's just a hack for burst fetching
        val now = System.currentTimeMillis()

        val journals: JournalList
        synchronized (cache) {
            val cached = cache.get(settings.account.name)
            if ((cached != null) && (abs(now - cached.first!!) <= cacheAge)) {
                journals = cached.second!!
            } else {
                journals = fetchJournals(journalsManager, settings, serviceType)
                cache.set(settings.account.name, Pair(System.currentTimeMillis(), journals))
            }
        }

        return journals.filter { it.second?.enumType == serviceType }
    }
}

abstract class SyncAdapterService : Service() {

    protected abstract fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent): IBinder? {
        return syncAdapter().syncAdapterBinder
    }

    abstract class SyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, false) {
        private val syncErrorTitle: Int = R.string.sync_error_generic
        private val notificationManager = SyncNotification(context, "refresh-collections", Constants.NOTIFICATION_REFRESH_COLLECTIONS)

        abstract fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            Logger.log.log(Level.INFO, "$authority sync of $account has been initiated.", extras.keySet().toTypedArray())

            // required for dav4android (ServiceLoader)
            Thread.currentThread().contextClassLoader = context.classLoader

            notificationManager.cancel()

            try {
                onPerformSyncDo(account, extras, authority, provider, syncResult)
            } catch (e: SecurityException) {
                // Shouldn't be needed - not sure why it doesn't fail
                onSecurityException(account, extras, authority, syncResult)
            } catch (e: Exceptions.ServiceUnavailableException) {
                syncResult.stats.numIoExceptions++
                syncResult.delayUntil = if (e.retryAfter > 0) e.retryAfter else Constants.DEFAULT_RETRY_DELAY
            } catch (e: TemporaryServerErrorException) {
                syncResult.stats.numIoExceptions++
                syncResult.delayUntil = Constants.DEFAULT_RETRY_DELAY
            } catch (e: ConnectionException) {
                syncResult.stats.numIoExceptions++
                syncResult.delayUntil = Constants.DEFAULT_RETRY_DELAY
            } catch (e: Exceptions.IgnorableHttpException) {
                // Ignore
            } catch (e: Exception) {
                if (e is ContactsStorageException || e is CalendarStorageException || e is SQLiteException) {
                    Logger.log.log(Level.SEVERE, "Couldn't prepare local journals", e)
                    syncResult.databaseError = true
                }

                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(syncErrorTitle, account.name)

                notificationManager.setThrowable(e)

                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(Constants.KEY_ACCOUNT, account)
                if (e !is Exceptions.UnauthorizedException && e !is UnauthorizedException) {
                    detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
                    detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase)
                }

                notificationManager.notify(title, context.getString(syncPhase))
            } catch (e: OutOfMemoryError) {
                val syncPhase = R.string.sync_phase_journals
                val title = context.getString(syncErrorTitle, account.name)
                notificationManager.setThrowable(e)
                val detailsIntent = notificationManager.detailsIntent
                detailsIntent.putExtra(Constants.KEY_ACCOUNT, account)
                notificationManager.notify(title, context.getString(syncPhase))
            }
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
            syncResult.databaseError = true

            val intent = Intent(context, PermissionsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
                    .setSmallIcon(R.drawable.ic_error_light)
                    .setLargeIcon(App.getLauncherBitmap(context))
                    .setContentTitle(context.getString(R.string.sync_error_permissions))
                    .setContentText(context.getString(R.string.sync_error_permissions_text))
                    .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            val nm = NotificationManagerCompat.from(context)
            nm.notify(Constants.NOTIFICATION_PERMISSIONS, notify)
        }

        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.syncWifiOnly) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetworkInfo
                if (network == null) {
                    Logger.log.info("No network available, stopping")
                    return false
                }
                if (network.type != ConnectivityManager.TYPE_WIFI || !network.isConnected) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }

                var onlySSID = settings.syncWifiOnlySSID
                if (onlySSID != null) {
                    onlySSID = "\"" + onlySSID + "\""
                    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wifi.connectionInfo
                    if (info == null || onlySSID != info.ssid) {
                        Logger.log.info("Connected to wrong WiFi network (" + info!!.ssid + ", required: " + onlySSID + "), ignoring")
                        return false
                    }
                }
            }
            return true
        }

        inner class RefreshCollections internal constructor(private val account: Account, private val serviceType: CollectionInfo.Type) {
            private val context: Context

            init {
                context = getContext()
            }

            @Throws(Exceptions.HttpException::class, Exceptions.IntegrityException::class, InvalidAccountException::class, Exceptions.GenericCryptoException::class)
            internal fun run() {
                Logger.log.info("Refreshing " + serviceType + " collections of service #" + serviceType.toString())

                val settings = AccountSettings(context, account)
                val httpClient = HttpClient.Builder(context, settings).setForeground(false).build()

                if (settings.isLegacy) {
                    val journalsManager = JournalManager(httpClient.okHttpClient, settings.uri?.toHttpUrlOrNull()!!)

                    var journals = journalFetcher.list(journalsManager, settings, serviceType)

                    if (journals.isEmpty()) {
                        journals = LinkedList()
                        try {
                            val info = CollectionInfo.defaultForServiceType(serviceType)
                            val uid = JournalManager.Journal.genUid()
                            info.uid = uid
                            val crypto = Crypto.CryptoManager(info.version, settings.password(), uid)
                            val journal = JournalManager.Journal(crypto, info.toJson(), uid)
                            journalsManager.create(journal)
                            journals.add(Pair(journal, info))
                        } catch (e: Exceptions.AssociateNotAllowedException) {
                            // Skip for now
                        }
                    }

                    legacySaveCollections(journals)

                    httpClient.close()
                    return
                }


                val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
                synchronized(etebaseLocalCache) {
                    val cacheAge = 5 * 1000 // 5 seconds - it's just a hack for burst fetching
                    val now = System.currentTimeMillis()
                    val lastCollectionsFetch = collectionLastFetchMap[account.name] ?: 0

                    if (abs(now - lastCollectionsFetch) <= cacheAge) {
                        return@synchronized
                    }

                    val etebase = EtebaseLocalCache.getEtebase(context, httpClient.okHttpClient, settings)
                    val colMgr = etebase.collectionManager
                    var stoken = etebaseLocalCache.loadStoken()
                    var done = false
                    while (!done) {
                        val colList = colMgr.list(FetchOptions().stoken(stoken))
                        for (col in colList.data) {
                            etebaseLocalCache.collectionSet(colMgr, col)
                        }

                        for (col in colList.removedMemberships) {
                            etebaseLocalCache.collectionUnset(colMgr, col.uid())
                        }

                        stoken = colList.stoken
                        done = colList.isDone
                        if (stoken != null) {
                            etebaseLocalCache.saveStoken(stoken)
                        }
                    }
                    collectionLastFetchMap[account.name] = now
                }

                httpClient.close()
            }

            private fun legacySaveCollections(journals: Iterable<Pair<JournalManager.Journal, CollectionInfo>>) {
                val data = (context.applicationContext as App).data
                val service = JournalModel.Service.fetchOrCreate(data, account.name, serviceType)

                val existing = HashMap<String, JournalEntity>()
                for (journalEntity in JournalEntity.getJournals(data, service)) {
                    existing[journalEntity.uid] = journalEntity
                }

                for (pair in journals) {
                    val journal = pair.first
                    val collection = pair.second
                    Logger.log.log(Level.FINE, "Saving collection", journal!!.uid)

                    collection!!.serviceID = service.id
                    val journalEntity = JournalEntity.fetchOrCreate(data, collection)
                    journalEntity.owner = journal.owner
                    journalEntity.encryptedKey = journal.key
                    journalEntity.isReadOnly = journal.readOnly
                    journalEntity.isDeleted = false
                    journalEntity.remoteLastUid = journal.lastUid
                    data.upsert(journalEntity)

                    existing.remove(collection.uid)
                }

                for (journalEntity in existing.values) {
                    Logger.log.log(Level.FINE, "Deleting collection", journalEntity.uid)

                    journalEntity.isDeleted = true
                    data.update(journalEntity)
                }
            }
        }
    }

    companion object {
        val journalFetcher = CachedJournalFetcher()
        var collectionLastFetchMap = HashMap<String, Long>()
    }
}
