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
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.PeriodicSync
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract

import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.utils.Base64

import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level

import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod

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

            App.log.fine("Account " + account.name + " has version " + version + ", current version: " + CURRENT_VERSION)

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
        App.log.info("Updating account " + account.name + " from version " + fromVersion + " to " + toVersion)
        try {
            updateInner(fromVersion)
            accountManager.setUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
        } catch (e: Exception) {
            App.log.log(Level.SEVERE, "Couldn't update account settings", e)
        }

    }

    @Throws(ContactsStorageException::class)
    private fun updateInner(fromVersion: Int) {
        if (fromVersion < 2) {
            var affected: Long = -1
            var newCount: Long = -1
            val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
                    ?: // no access to contacts provider
                    return

            // don't run syncs during the migration
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)
            ContentResolver.setIsSyncable(account, App.addressBooksAuthority, 0)
            ContentResolver.cancelSync(account, null)

            try {
                // get previous address book settings (including URL)
                val raw = ContactsContract.SyncState.get(provider, account)
                if (raw == null)
                    App.log.info("No contacts sync state, ignoring account")
                else {
                    val parcel = Parcel.obtain()
                    parcel.unmarshall(raw, 0, raw.size)
                    parcel.setDataPosition(0)
                    val params = parcel.readBundle()
                    parcel.recycle()
                    val url = params.getString("url")
                    if (url == null)
                        App.log.info("No address book URL, ignoring account")
                    else {
                        // create new address book
                        val info = CollectionInfo()
                        info.type = CollectionInfo.Type.ADDRESS_BOOK
                        info.uid = url
                        info.displayName = account.name
                        App.log.log(Level.INFO, "Creating new address book account", url)
                        val addressBookAccount = Account(LocalAddressBook.accountName(account, info), App.addressBookAccountType)
                        if (!accountManager.addAccountExplicitly(addressBookAccount, null, null))
                            throw ContactsStorageException("Couldn't create address book account")

                        LocalAddressBook.setUserData(accountManager, addressBookAccount, account, info.uid)
                        val newAddressBook = LocalAddressBook(context, addressBookAccount, provider)

                        // move contacts to new address book
                        App.log.info("Moving contacts from $account to $addressBookAccount")
                        val newAccount = ContentValues(2)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
                        affected = provider.update(ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
                                newAccount,
                                ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + "=?",
                                arrayOf(account.name, account.type)).toLong()
                        App.log.info(affected.toString() + " contacts moved to new address book")
                        newCount = newAddressBook.count()
                    }

                    ContactsContract.SyncState.set(provider, account, null)
                }
            } catch (e: RemoteException) {
                throw ContactsStorageException("Couldn't migrate contacts to new address book", e)
            }

            provider.release()

            // request sync of new address book account
            ContentResolver.setIsSyncable(account, App.addressBooksAuthority, 1)
            setSyncInterval(App.addressBooksAuthority!!, Constants.DEFAULT_SYNC_INTERVAL.toLong())

            // Error handling
            if (affected != -1L && affected != newCount) {
                val notificationHelper = NotificationHelper(context, "account-migration", Constants.NOTIFICATION_ACCOUNT_UPDATE)
                notificationHelper.setThrowable(AccountMigrationException("Failed to upgrade account"))
                notificationHelper.notify("Account upgrade failed", "upgrading account")
            }
        }
    }

    class AppUpdatedReceiver : BroadcastReceiver() {

        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            App.log.info("EteSync was updated, checking for AccountSettings version")

            // peek into AccountSettings to initiate a possible migration
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(App.accountType))
                try {
                    App.log.info("Checking account " + account.name)
                    AccountSettings(context, account)
                } catch (e: InvalidAccountException) {
                    App.log.log(Level.SEVERE, "Couldn't check for updated account settings", e)
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
