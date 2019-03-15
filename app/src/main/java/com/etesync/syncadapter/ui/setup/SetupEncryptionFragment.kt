/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.provider.CalendarContract
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.UserInfoManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.ServiceEntity
import com.etesync.syncadapter.resource.LocalTaskList
import com.etesync.syncadapter.ui.setup.BaseConfigurationFinder.Configuration
import com.etesync.syncadapter.utils.AndroidCompat
import okhttp3.HttpUrl
import java.util.logging.Level

class SetupEncryptionFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.login_encryption_setup_title)
        progress.setMessage(getString(R.string.login_encryption_setup))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SetupEncryptionLoader(context!!, arguments!!.getSerializable(KEY_CONFIG) as Configuration).execute()
    }

    private inner class SetupEncryptionLoader(internal val context: Context, internal val config: Configuration) : AsyncTask<Void, Void, Configuration>() {

        override fun onPostExecute(result: Configuration) {
            if (config.error != null && config.error is Exceptions.IntegrityException) {
                Logger.log.severe("Wrong encryption password.")
                AlertDialog.Builder(activity!!)
                        .setTitle(R.string.wrong_encryption_password)
                        .setIcon(R.drawable.ic_error_dark)
                        .setMessage(getString(R.string.wrong_encryption_password_content, config.error!!.localizedMessage))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            // dismiss
                        }.show()
            } else {
                try {
                    if (createAccount(config.userName, config)) {
                        activity!!.setResult(Activity.RESULT_OK)
                        activity!!.finish()
                    }
                } catch (e: InvalidAccountException) {
                    Logger.log.severe("Account creation failed!")
                    AlertDialog.Builder(activity!!)
                            .setTitle(R.string.account_creation_failed)
                            .setIcon(R.drawable.ic_error_dark)
                            .setMessage(e.localizedMessage)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                // dismiss
                            }.show()
                }

            }

            dismissAllowingStateLoss()
        }

        override fun doInBackground(vararg aVoids: Void): Configuration {
            Logger.log.info("Started deriving key")
            config.password = Crypto.deriveKey(config.userName, config.rawPassword!!)
            Logger.log.info("Finished deriving key")
            config.error = null

            try {
                val cryptoManager: Crypto.CryptoManager
                val httpClient = HttpClient.Builder(context, config.url.host, config.authtoken!!).build().okHttpClient

                val userInfoManager = UserInfoManager(httpClient, HttpUrl.get(config.url)!!)
                val userInfo = userInfoManager.fetch(config.userName)
                if (userInfo != null) {
                    Logger.log.info("Fetched userInfo for " + config.userName)
                    cryptoManager = Crypto.CryptoManager(userInfo.version!!.toInt(), config.password!!, "userInfo")
                    userInfo.verify(cryptoManager)
                    config.keyPair = Crypto.AsymmetricKeyPair(userInfo.getContent(cryptoManager)!!, userInfo.pubkey!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                config.error = e
            }

            return config
        }
    }


    @Throws(InvalidAccountException::class)
    protected fun createAccount(accountName: String, config: BaseConfigurationFinder.Configuration): Boolean {
        val account = Account(accountName, App.accountType)

        // create Android account
        Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, config.userName, config.url))

        val accountManager = AccountManager.get(context)
        if (!accountManager.addAccountExplicitly(account, config.password, null))
            return false

        AccountSettings.setUserData(accountManager, account, config.url, config.userName)

        // add entries for account to service DB
        Logger.log.log(Level.INFO, "Writing account configuration to database", config)
        try {
            val settings = AccountSettings(context!!, account)

            settings.authToken = config.authtoken!!
            if (config.keyPair != null) {
                settings.keyPair = config.keyPair!!
            }

            if (config.cardDAV != null) {
                // insert CardDAV service
                insertService(accountName, CollectionInfo.Type.ADDRESS_BOOK, config.cardDAV)

                // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
                settings.setSyncInterval(App.addressBooksAuthority, Constants.DEFAULT_SYNC_INTERVAL.toLong())
            } else {
                ContentResolver.setIsSyncable(account, App.addressBooksAuthority, 0)
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                insertService(accountName, CollectionInfo.Type.CALENDAR, config.calDAV)

                // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
                settings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL.toLong())

                // enable task sync if OpenTasks is installed
                // further changes will be handled by PackageChangedReceiver
                if (LocalTaskList.tasksProviderAvailable(context!!)) {
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1)
                    settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL.toLong())
                }
            } else {
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)
            }

        } catch (e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
            AndroidCompat.removeAccount(accountManager, account)
            throw e
        }

        return true
    }

    protected fun insertService(accountName: String, serviceType: CollectionInfo.Type, info: BaseConfigurationFinder.Configuration.ServiceInfo) {
        val data = (context!!.applicationContext as App).data

        // insert service
        val serviceEntity = ServiceEntity()
        serviceEntity.account = accountName
        serviceEntity.type = serviceType
        data.upsert(serviceEntity)

        // insert collections
        for (collection in info.collections.values) {
            collection.serviceID = serviceEntity.id
            val journalEntity = JournalEntity(data, collection)
            data.insert(journalEntity)
        }
    }

    companion object {
        private val KEY_CONFIG = "config"

        fun newInstance(config: BaseConfigurationFinder.Configuration): SetupEncryptionFragment {
            val frag = SetupEncryptionFragment()
            val args = Bundle(1)
            args.putSerializable(KEY_CONFIG, config)
            frag.arguments = args
            return frag
        }
    }
}
