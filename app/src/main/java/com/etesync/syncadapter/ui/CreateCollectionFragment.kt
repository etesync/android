/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import androidx.fragment.app.DialogFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import okhttp3.HttpUrl

class CreateCollectionFragment : DialogFragment(), LoaderManager.LoaderCallbacks<Exception> {

    protected lateinit var account: Account
    protected lateinit var info: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments!!.getParcelable(ARG_ACCOUNT)
        info = arguments!!.getSerializable(ARG_COLLECTION_INFO) as CollectionInfo

        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.create_collection_creating)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }


    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Exception> {
        return CreateCollectionLoader(context!!, account, info)
    }

    override fun onLoadFinished(loader: Loader<Exception>, exception: Exception?) {
        dismissAllowingStateLoss()

        val parent = activity
        if (parent != null) {
            if (exception != null)
                fragmentManager!!.beginTransaction()
                        .add(ExceptionInfoFragment.newInstance(exception, account), null)
                        .commitAllowingStateLoss()
            else
                parent.finish()
        }

    }

    override fun onLoaderReset(loader: Loader<Exception>) {}


    protected class CreateCollectionLoader(context: Context, internal val account: Account, internal val info: CollectionInfo) : AsyncTaskLoader<Exception>(context) {

        override fun onStartLoading() {
            forceLoad()
        }

        override fun loadInBackground(): Exception? {
            try {
                var authority: String = ""

                val data = (context.applicationContext as App).data

                // 1. find service ID
                when (info.type){
                    CollectionInfo.Type.ADDRESS_BOOK -> authority = App.addressBooksAuthority
                    CollectionInfo.Type.CALENDAR -> authority = CalendarContract.AUTHORITY
                    CollectionInfo.Type.TASKS -> authority = TaskProvider.ProviderName.OpenTasks.authority
                }

                val serviceEntity = JournalModel.Service.fetch(data, account.name, info.type)
                info.serviceID = serviceEntity.id

                val settings = AccountSettings(context, account)
                val principal = HttpUrl.get(settings.uri!!)

                val httpClient = HttpClient.Builder(context, settings).build().okHttpClient
                val journalManager = JournalManager(httpClient, principal!!)
                var uid = info.uid

                if (uid == null) {
                    uid = JournalManager.Journal.genUid()
                    info.uid = uid
                    val crypto = Crypto.CryptoManager(info.version, settings.password(), uid)
                    val journal = JournalManager.Journal(crypto, info.toJson(), uid)
                    journalManager.create(journal)

                    val journalEntity = JournalEntity.fetchOrCreate(data, info)
                    data.upsert(journalEntity)
                } else {
                    val crypto: Crypto.CryptoManager
                    val journalEntity = JournalEntity.fetch(data, serviceEntity, uid)

                    if (journalEntity.encryptedKey != null) {
                        crypto = Crypto.CryptoManager(info.version, settings.keyPair!!, journalEntity.encryptedKey)
                    } else {
                        crypto = Crypto.CryptoManager(info.version, settings.password(), uid)
                    }
                    val journal = JournalManager.Journal(crypto, info.toJson(), uid)
                    journalManager.update(journal)
                }

                requestSync(authority)
            } catch (e: IllegalStateException) {
                return e
            } catch (e: Exceptions.HttpException) {
                return e
            } catch (e: InvalidAccountException) {
                return e
            } catch (e: Exceptions.IntegrityException) {
                return e
            } catch (e: Exceptions.GenericCryptoException) {
                return e
            } catch (e: Exceptions.AssociateNotAllowedException) {
                return e
            }

            return null
        }

        private fun requestSync(authority: String) {
            val extras = Bundle()
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
            ContentResolver.requestSync(account, authority, extras)
        }
    }

    companion object {
        private val ARG_ACCOUNT = "account"
        private val ARG_COLLECTION_INFO = "collectionInfo"

        fun newInstance(account: Account, info: CollectionInfo): CreateCollectionFragment {
            val frag = CreateCollectionFragment()
            val args = Bundle(2)
            args.putParcelable(ARG_ACCOUNT, account)
            args.putSerializable(ARG_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }
    }

}
