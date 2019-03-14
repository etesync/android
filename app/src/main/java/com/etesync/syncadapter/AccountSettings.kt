/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.utils.Base64
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level

class AccountSettings @TargetApi(Build.VERSION_CODES.LOLLIPOP)
@Throws(InvalidAccountException::class)
constructor(internal val context: Context, internal val account: Account) {
    internal val accountManager: AccountManager


    // authentication settings

    var uri: URI?
        get() {
            try {
                return URI(accountManager.getUserData(account, KEY_URI))
            } catch (e: URISyntaxException) {
                return null
            }

        }
        set(uri) = accountManager.setUserData(account, KEY_URI, uri.toString())

    var authToken: String
        get() = accountManager.getUserData(account, KEY_TOKEN)
        set(token) = accountManager.setUserData(account, KEY_TOKEN, token)


    var keyPair: Crypto.AsymmetricKeyPair?
        get() {
            if (accountManager.getUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY) != null) {
                val pubkey = Base64.decode(accountManager.getUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY), Base64.NO_WRAP)
                val privkey = Base64.decode(accountManager.getUserData(account, KEY_ASYMMETRIC_PRIVATE_KEY), Base64.NO_WRAP)

                return Crypto.AsymmetricKeyPair(privkey, pubkey)
            }

            return null
        }
        set(keyPair) {
            accountManager.setUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY, Base64.encodeToString(keyPair?.publicKey, Base64.NO_WRAP))
            accountManager.setUserData(account, KEY_ASYMMETRIC_PRIVATE_KEY, Base64.encodeToString(keyPair?.privateKey, Base64.NO_WRAP))
        }

    val syncWifiOnly: Boolean
        get() = accountManager.getUserData(account, KEY_WIFI_ONLY) != null

    var syncWifiOnlySSID: String?
        get() = accountManager.getUserData(account, KEY_WIFI_ONLY_SSID)
        set(ssid) = accountManager.setUserData(account, KEY_WIFI_ONLY_SSID, ssid)


    // CalDAV settings

    var manageCalendarColors: Boolean
        get() = accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null
        set(manage) = accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")


    // CardDAV settings

    var groupMethod: GroupMethod
        get() {
            val name = accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
            return if (name != null)
                GroupMethod.valueOf(name)
            else
                GroupMethod.GROUP_VCARDS
        }
        set(method) {
            val name = if (method == GroupMethod.GROUP_VCARDS) null else method.name
            accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, name)
        }


    init {

        accountManager = AccountManager.get(context)

        synchronized(AccountSettings::class.java) {
            val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION)
                    ?: throw InvalidAccountException(account)

            var version = 0
            try {
                version = Integer.parseInt(versionStr)
            } catch (ignored: NumberFormatException) {
            }

            Logger.log.fine("Account " + account.name + " has version " + version + ", current version: " + CURRENT_VERSION)

            if (version < CURRENT_VERSION)
                update(version)
        }
    }

    fun username(): String {
        return accountManager.getUserData(account, KEY_USERNAME)
    }

    fun username(userName: String) {
        accountManager.setUserData(account, KEY_USERNAME, userName)
    }

    fun password(): String {
        return accountManager.getPassword(account)
    }

    fun password(password: String) {
        accountManager.setPassword(account, password)
    }


    // sync. settings

    fun getSyncInterval(authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        if (ContentResolver.getSyncAutomatically(account, authority)) {
            val syncs = ContentResolver.getPeriodicSyncs(account, authority)
            return if (syncs.isEmpty())
                SYNC_INTERVAL_MANUALLY
            else
                syncs[0].period
        } else
            return SYNC_INTERVAL_MANUALLY
    }

    fun setSyncInterval(authority: String, seconds: Long) {
        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false)
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true)
            ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)
        }
    }

    fun setSyncWiFiOnly(wiFiOnly: Boolean) {
        accountManager.setUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)
    }


    // update from previous account settings

    private fun update(fromVersion: Int) {
        val toVersion = CURRENT_VERSION
        Logger.log.info("Updating account " + account.name + " from version " + fromVersion + " to " + toVersion)
        try {
            updateInner(fromVersion)
            accountManager.setUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't update account settings", e)
        }

    }

    @Throws(ContactsStorageException::class)
    private fun updateInner(fromVersion: Int) {
        if (fromVersion < 2) {
        }
    }

    class AppUpdatedReceiver : BroadcastReceiver() {

        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Logger.log.info("EteSync was updated, checking for AccountSettings version")

            // peek into AccountSettings to initiate a possible migration
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(App.accountType))
                try {
                    Logger.log.info("Checking account " + account.name)
                    AccountSettings(context, account)
                } catch (e: InvalidAccountException) {
                    Logger.log.log(Level.SEVERE, "Couldn't check for updated account settings", e)
                }

        }

    }

    class AccountMigrationException(msg: String) : Exception(msg)

    companion object {
        private val CURRENT_VERSION = 2
        private val KEY_SETTINGS_VERSION = "version"
        private val KEY_URI = "uri"
        private val KEY_USERNAME = "user_name"
        private val KEY_TOKEN = "auth_token"
        private val KEY_ASYMMETRIC_PRIVATE_KEY = "asymmetric_private_key"
        private val KEY_ASYMMETRIC_PUBLIC_KEY = "asymmetric_public_key"
        private val KEY_WIFI_ONLY = "wifi_only"
        // sync on WiFi only (default: false)
        private val KEY_WIFI_ONLY_SSID = "wifi_only_ssid"  // restrict sync to specific WiFi SSID

        /**
         * Time range limitation to the past [in days]
         * value = null            default value (DEFAULT_TIME_RANGE_PAST_DAYS)
         * < 0 (-1)          no limit
         * >= 0              entries more than n days in the past won't be synchronized
         */
        private val KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days"
        private val DEFAULT_TIME_RANGE_PAST_DAYS = 90

        /* Whether DAVdroid sets the local calendar color to the value from service DB at every sync
       value = null (not existing)     true (default)
               "0"                     false */
        private val KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors"

        /**
         * Contact group method:
         * value = null (not existing)     groups as separate VCards (default)
         * "CATEGORIES"            groups are per-contact CATEGORIES
         */
        private val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        val SYNC_INTERVAL_MANUALLY: Long = -1

        // XXX: Workaround a bug in Android where passing a bundle to addAccountExplicitly doesn't work.
        fun setUserData(accountManager: AccountManager, account: Account, uri: URI, userName: String) {
            accountManager.setUserData(account, KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())
            accountManager.setUserData(account, KEY_USERNAME, userName)
            accountManager.setUserData(account, KEY_URI, uri.toString())
        }
    }
}
