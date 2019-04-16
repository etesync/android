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
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import okhttp3.HttpUrl

class DeleteCollectionFragment : DialogFragment(), LoaderManager.LoaderCallbacks<Exception> {

    protected lateinit var account: Account
    protected lateinit var collectionInfo: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loaderManager.initLoader(0, arguments, this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.delete_collection_deleting_collection)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }


    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Exception> {
        account = args!!.getParcelable(ARG_ACCOUNT)!!
        collectionInfo = args.getSerializable(ARG_COLLECTION_INFO) as CollectionInfo
        return DeleteCollectionLoader(context!!, account, collectionInfo)
    }

    override fun onLoadFinished(loader: Loader<Exception>, exception: Exception?) {
        dismissAllowingStateLoss()

        if (exception != null)
            fragmentManager!!.beginTransaction()
                    .add(ExceptionInfoFragment.newInstance(exception, account), null)
                    .commitAllowingStateLoss()
        else {
            val activity = activity
            if (activity is Refreshable)
                (activity as Refreshable).refresh()
            else if (activity is EditCollectionActivity)
                activity.finish()
        }
    }

    override fun onLoaderReset(loader: Loader<Exception>) {}


    protected class DeleteCollectionLoader(context: Context, internal val account: Account, internal val collectionInfo: CollectionInfo) : AsyncTaskLoader<Exception>(context) {

        override fun onStartLoading() {
            forceLoad()
        }

        override fun loadInBackground(): Exception? {
            try {
                // delete collection locally
                val data = (context.applicationContext as App).data

                val settings = AccountSettings(context, account)
                val principal = HttpUrl.get(settings.uri!!)

                val httpClient = HttpClient.Builder(context, settings).build().okHttpClient
                val journalManager = JournalManager(httpClient, principal!!)
                val crypto = Crypto.CryptoManager(collectionInfo.version, settings.password(), collectionInfo.uid!!)

                journalManager.delete(JournalManager.Journal(crypto, collectionInfo.toJson(), collectionInfo.uid!!))
                val journalEntity = JournalEntity.fetch(data, collectionInfo.getServiceEntity(data), collectionInfo.uid)
                journalEntity!!.isDeleted = true
                data.update(journalEntity)

                return null
            } catch (e: Exceptions.HttpException) {
                return e
            } catch (e: Exceptions.IntegrityException) {
                return e
            } catch (e: Exceptions.GenericCryptoException) {
                return e
            } catch (e: InvalidAccountException) {
                return e
            }

        }
    }


    class ConfirmDeleteCollectionFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val collectionInfo = arguments!!.getSerializable(ARG_COLLECTION_INFO) as CollectionInfo
            val name = if (TextUtils.isEmpty(collectionInfo.displayName)) collectionInfo.uid else collectionInfo.displayName

            return AlertDialog.Builder(context!!)
                    .setTitle(R.string.delete_collection_confirm_title)
                    .setMessage(getString(R.string.delete_collection_confirm_warning, name))
                    .setPositiveButton(android.R.string.yes) { dialog, _ ->
                        val frag = DeleteCollectionFragment()
                        frag.arguments = arguments
                        frag.show(fragmentManager!!, null)
                    }
                    .setNegativeButton(android.R.string.no) { _, _ -> dismiss() }
                    .create()
        }

        companion object {

            fun newInstance(account: Account, collectionInfo: CollectionInfo): ConfirmDeleteCollectionFragment {
                val frag = ConfirmDeleteCollectionFragment()
                val args = Bundle(2)
                args.putParcelable(ARG_ACCOUNT, account)
                args.putSerializable(ARG_COLLECTION_INFO, collectionInfo)
                frag.arguments = args
                return frag
            }
        }
    }

    companion object {
        protected val ARG_ACCOUNT = "account"
        protected val ARG_COLLECTION_INFO = "collectionInfo"
    }

}
