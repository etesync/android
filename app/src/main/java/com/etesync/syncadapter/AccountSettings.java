/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.PeriodicSync;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.utils.Base64;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.vcard4android.GroupMethod;

public class AccountSettings {
    private final static int CURRENT_VERSION = 1;
    private final static String
            KEY_SETTINGS_VERSION = "version",
            KEY_URI = "uri",
            KEY_USERNAME = "user_name",
            KEY_TOKEN = "auth_token",
            KEY_ASYMMETRIC_PRIVATE_KEY = "asymmetric_private_key",
            KEY_ASYMMETRIC_PUBLIC_KEY = "asymmetric_public_key",
            KEY_WIFI_ONLY = "wifi_only",            // sync on WiFi only (default: false)
            KEY_WIFI_ONLY_SSID = "wifi_only_ssid";  // restrict sync to specific WiFi SSID

    /**
     * Time range limitation to the past [in days]
     * value = null            default value (DEFAULT_TIME_RANGE_PAST_DAYS)
     * < 0 (-1)          no limit
     * >= 0              entries more than n days in the past won't be synchronized
     */
    private final static String KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days";
    private final static int DEFAULT_TIME_RANGE_PAST_DAYS = 90;

    /* Whether DAVdroid sets the local calendar color to the value from service DB at every sync
       value = null (not existing)     true (default)
               "0"                     false */
    private final static String KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors";

    /**
     * Contact group method:
     * value = null (not existing)     groups as separate VCards (default)
     * "CATEGORIES"            groups are per-contact CATEGORIES
     */
    private final static String KEY_CONTACT_GROUP_METHOD = "contact_group_method";

    public final static long SYNC_INTERVAL_MANUALLY = -1;

    final Context context;
    final AccountManager accountManager;
    final Account account;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AccountSettings(@NonNull Context context, @NonNull Account account) throws InvalidAccountException {
        this.context = context;
        this.account = account;

        accountManager = AccountManager.get(context);

        synchronized (AccountSettings.class) {
            String versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION);
            if (versionStr == null)
                throw new InvalidAccountException(account);

            int version = 0;
            try {
                version = Integer.parseInt(versionStr);
            } catch (NumberFormatException ignored) {
            }
            App.log.fine("Account " + account.name + " has version " + version + ", current version: " + CURRENT_VERSION);

            if (version < CURRENT_VERSION)
                update(version);
        }
    }

    public static Bundle initialUserData(URI uri, String userName) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_SETTINGS_VERSION, String.valueOf(CURRENT_VERSION));
        bundle.putString(KEY_USERNAME, userName);
        bundle.putString(KEY_URI, uri.toString());
        return bundle;
    }


    // authentication settings

    public URI getUri() {
        try {
            return new URI(accountManager.getUserData(account, KEY_URI));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public void setUri(@NonNull URI uri) {
        accountManager.setUserData(account, KEY_URI, uri.toString());
    }

    public String getAuthToken() {
        return accountManager.getUserData(account, KEY_TOKEN);
    }

    public void setAuthToken(@NonNull String token) {
        accountManager.setUserData(account, KEY_TOKEN, token);
    }


    public Crypto.AsymmetricKeyPair getKeyPair() {
        if (accountManager.getUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY) != null) {
            byte[] pubkey = Base64.decode(accountManager.getUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY), Base64.NO_WRAP);
            byte[] privkey = Base64.decode(accountManager.getUserData(account, KEY_ASYMMETRIC_PRIVATE_KEY), Base64.NO_WRAP);

            return new Crypto.AsymmetricKeyPair(privkey, pubkey);
        }

        return null;
    }

    public void setKeyPair(@NonNull Crypto.AsymmetricKeyPair keyPair) {
        accountManager.setUserData(account, KEY_ASYMMETRIC_PUBLIC_KEY, Base64.encodeToString(keyPair.getPublicKey(), Base64.NO_WRAP));
        accountManager.setUserData(account, KEY_ASYMMETRIC_PRIVATE_KEY, Base64.encodeToString(keyPair.getPrivateKey(), Base64.NO_WRAP));
    }

    public String username() {
        return accountManager.getUserData(account, KEY_USERNAME);
    }

    public void username(@NonNull String userName) {
        accountManager.setUserData(account, KEY_USERNAME, userName);
    }

    public String password() {
        return accountManager.getPassword(account);
    }

    public void password(@NonNull String password) {
        accountManager.setPassword(account, password);
    }


    // sync. settings

    public Long getSyncInterval(@NonNull String authority) {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null;

        if (ContentResolver.getSyncAutomatically(account, authority)) {
            List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(account, authority);
            if (syncs.isEmpty())
                return SYNC_INTERVAL_MANUALLY;
            else
                return syncs.get(0).period;
        } else
            return SYNC_INTERVAL_MANUALLY;
    }

    public void setSyncInterval(@NonNull String authority, long seconds) {
        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false);
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true);
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), seconds);
        }
    }

    public boolean getSyncWifiOnly() {
        return accountManager.getUserData(account, KEY_WIFI_ONLY) != null;
    }

    public void setSyncWiFiOnly(boolean wiFiOnly) {
        accountManager.setUserData(account, KEY_WIFI_ONLY, wiFiOnly ? "1" : null);
    }

    @Nullable
    public String getSyncWifiOnlySSID() {
        return accountManager.getUserData(account, KEY_WIFI_ONLY_SSID);
    }

    public void setSyncWifiOnlySSID(String ssid) {
        accountManager.setUserData(account, KEY_WIFI_ONLY_SSID, ssid);
    }


    // CalDAV settings

    public boolean getManageCalendarColors() {
        return accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null;
    }

    public void setManageCalendarColors(boolean manage) {
        accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, manage ? null : "0");
    }


    // CardDAV settings

    @NonNull
    public GroupMethod getGroupMethod() {
        final String name = accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD);
        return name != null ?
                GroupMethod.valueOf(name) :
                GroupMethod.GROUP_VCARDS;
    }

    public void setGroupMethod(@NonNull GroupMethod method) {
        final String name = method == GroupMethod.GROUP_VCARDS ? null : method.name();
        accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, name);
    }


    // update from previous account settings

    private void update(int fromVersion) {
        for (int toVersion = fromVersion + 1; toVersion <= CURRENT_VERSION; toVersion++) {
            App.log.info("Updating account " + account.name + " from version " + fromVersion + " to " + toVersion);
            try {
                Method updateProc = getClass().getDeclaredMethod("update_" + fromVersion + "_" + toVersion);
                updateProc.invoke(this);
                accountManager.setUserData(account, KEY_SETTINGS_VERSION, String.valueOf(toVersion));
            } catch (Exception e) {
                App.log.log(Level.SEVERE, "Couldn't update account settings", e);
            }
            fromVersion = toVersion;
        }
    }

    public static class AppUpdatedReceiver extends BroadcastReceiver {

        @Override
        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        public void onReceive(Context context, Intent intent) {
            App.log.info("EteSync was updated, checking for AccountSettings version");

            // peek into AccountSettings to initiate a possible migration
            AccountManager accountManager = AccountManager.get(context);
            for (Account account : accountManager.getAccountsByType(Constants.ACCOUNT_TYPE))
                try {
                    App.log.info("Checking account " + account.name);
                    new AccountSettings(context, account);
                } catch (InvalidAccountException e) {
                    App.log.log(Level.SEVERE, "Couldn't check for updated account settings", e);
                }
        }

    }

}
