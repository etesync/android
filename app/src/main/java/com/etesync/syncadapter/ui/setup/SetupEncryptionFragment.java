/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.journalmanager.UserInfoManager;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.resource.LocalTaskList;
import com.etesync.syncadapter.ui.setup.BaseConfigurationFinder.Configuration;
import com.etesync.syncadapter.utils.AndroidCompat;

import java.util.logging.Level;

import at.bitfire.ical4android.TaskProvider;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class SetupEncryptionFragment extends DialogFragment {
    private static final String KEY_CONFIG = "config";

    public static SetupEncryptionFragment newInstance(BaseConfigurationFinder.Configuration config) {
        SetupEncryptionFragment frag = new SetupEncryptionFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(KEY_CONFIG, config);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setTitle(R.string.login_encryption_setup_title);
        progress.setMessage(getString(R.string.login_encryption_setup));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new SetupEncryptionLoader(getContext(), (Configuration) getArguments().getSerializable(KEY_CONFIG)).execute();
    }

    private class SetupEncryptionLoader extends AsyncTask<Void, Void, Configuration> {
        final Context context;
        final Configuration config;

        public SetupEncryptionLoader(Context context, Configuration config) {
            super();
            this.context = context;
            this.config = config;
        }

        @Override
        protected void onPostExecute(Configuration result) {
            if ((config.error != null) && (config.error instanceof Exceptions.IntegrityException)) {
                App.log.severe("Wrong encryption password.");
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.wrong_encryption_password)
                        .setIcon(R.drawable.ic_error_dark)
                        .setMessage(getString(R.string.wrong_encryption_password_content, config.error.getLocalizedMessage()))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // dismiss
                            }
                        }).show();
            } else {
                try {
                    if (createAccount(config.userName, config)) {
                        getActivity().setResult(Activity.RESULT_OK);
                        getActivity().finish();
                    }
                } catch (InvalidAccountException e) {
                    App.log.severe("Account creation failed!");
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.account_creation_failed)
                            .setIcon(R.drawable.ic_error_dark)
                            .setMessage(e.getLocalizedMessage())
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // dismiss
                                }
                            }).show();
                }
            }

            dismissAllowingStateLoss();
        }

        @Override
        protected Configuration doInBackground(Void... aVoids) {
            App.log.info("Started deriving key");
            config.password = Crypto.deriveKey(config.userName, config.rawPassword);
            App.log.info("Finished deriving key");
            config.error = null;

            try {
                Crypto.CryptoManager cryptoManager;
                OkHttpClient httpClient = HttpClient.create(getContext(), config.url, config.authtoken);

                UserInfoManager userInfoManager = new UserInfoManager(httpClient, HttpUrl.get(config.url));
                UserInfoManager.UserInfo userInfo = userInfoManager.get(config.userName);
                if (userInfo != null) {
                    App.log.info("Fetched userInfo for " + config.userName);
                    cryptoManager = new Crypto.CryptoManager(userInfo.getVersion(), config.password, "userInfo");
                    userInfo.verify(cryptoManager);
                    config.keyPair = new Crypto.AsymmetricKeyPair(userInfo.getContent(cryptoManager), userInfo.getPubkey());
                }
            } catch (Exception e) {
                e.printStackTrace();
                config.error = e;
            }

            return config;
        }
    }


    protected boolean createAccount(String accountName, BaseConfigurationFinder.Configuration config) throws InvalidAccountException {
        Account account = new Account(accountName, App.getAccountType());

        // create Android account
        App.log.log(Level.INFO, "Creating Android account with initial config", new Object[] { account, config.userName, config.url });

        AccountManager accountManager = AccountManager.get(getContext());
        if (!accountManager.addAccountExplicitly(account, config.password, null))
            return false;

        AccountSettings.setUserData(accountManager, account, config.url, config.userName);

        // add entries for account to service DB
        App.log.log(Level.INFO, "Writing account configuration to database", config);
        try {
            AccountSettings settings = new AccountSettings(getContext(), account);

            settings.setAuthToken(config.authtoken);
            if (config.keyPair != null) {
                settings.setKeyPair(config.keyPair);
            }

            if (config.cardDAV != null) {
                // insert CardDAV service
                insertService(accountName, CollectionInfo.Type.ADDRESS_BOOK, config.cardDAV);

                // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
                settings.setSyncInterval(App.getAddressBooksAuthority(), Constants.DEFAULT_SYNC_INTERVAL);
            } else {
                ContentResolver.setIsSyncable(account, App.getAddressBooksAuthority(), 0);
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                insertService(accountName, CollectionInfo.Type.CALENDAR, config.calDAV);

                // calendar sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_contacts.xml
                settings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL);

                // enable task sync if OpenTasks is installed
                // further changes will be handled by PackageChangedReceiver
                if (LocalTaskList.tasksProviderAvailable(getContext())) {
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1);
                    settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL);
                }
            } else {
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0);
            }

        } catch(InvalidAccountException e) {
            App.log.log(Level.SEVERE, "Couldn't access account settings", e);
            AndroidCompat.removeAccount(accountManager, account);
            throw e;
        }

        return true;
    }

    protected void insertService(String accountName, CollectionInfo.Type serviceType, BaseConfigurationFinder.Configuration.ServiceInfo info) {
        EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();

        // insert service
        ServiceEntity serviceEntity = new ServiceEntity();
        serviceEntity.setAccount(accountName);
        serviceEntity.setType(serviceType);
        data.upsert(serviceEntity);

        // insert collections
        for (CollectionInfo collection : info.collections.values()) {
            collection.serviceID = serviceEntity.getId();
            JournalEntity journalEntity = new JournalEntity(data, collection);
            data.insert(journalEntity);
        }
    }
}
