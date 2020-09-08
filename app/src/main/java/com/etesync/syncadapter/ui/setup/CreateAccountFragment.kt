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
import android.os.Bundle
import android.provider.CalendarContract
import androidx.fragment.app.DialogFragment
import at.bitfire.ical4android.TaskProvider.Companion.OPENTASK_PROVIDERS
import com.etesync.syncadapter.*
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.ui.setup.BaseConfigurationFinder.Configuration
import com.etesync.syncadapter.utils.AndroidCompat
import com.etesync.syncadapter.utils.TaskProviderHandling
import java.util.logging.Level

class CreateAccountFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.setting_up_encryption)
        progress.setMessage(getString(R.string.setting_up_encryption_content))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = requireArguments().getSerializable(KEY_CONFIG) as Configuration

        val activity = requireActivity()
        if (createAccount(config.userName, config)) {
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        }
    }

    @Throws(InvalidAccountException::class)
    protected fun createAccount(accountName: String, config: Configuration): Boolean {
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
            val settings = AccountSettings(requireContext(), account)

            settings.etebaseSession = config.etebaseSession

            // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
            settings.setSyncInterval(App.addressBooksAuthority, Constants.DEFAULT_SYNC_INTERVAL.toLong())

            // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
            settings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL.toLong())

            OPENTASK_PROVIDERS.forEach {
                // enable task sync if OpenTasks is installed
                // further changes will be handled by PackageChangedReceiver
                TaskProviderHandling.updateTaskSync(requireContext(), it)
            }

        } catch (e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
            AndroidCompat.removeAccount(accountManager, account)
            throw e
        }

        return true
    }

    companion object {
        private val KEY_CONFIG = "config"

        fun newInstance(config: Configuration): CreateAccountFragment {
            val frag = CreateAccountFragment()
            val args = Bundle(1)
            args.putSerializable(KEY_CONFIG, config)
            frag.arguments = args
            return frag
        }
    }
}
