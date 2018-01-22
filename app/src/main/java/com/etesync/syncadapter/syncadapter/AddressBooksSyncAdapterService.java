/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.provider.ContactsContract;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.NotificationHelper;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.ui.DebugInfoActivity;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;

public class AddressBooksSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new AddressBooksSyncAdapter(this);
    }


	private static class AddressBooksSyncAdapter extends SyncAdapter {

        public AddressBooksSyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);

            NotificationHelper notificationManager = new NotificationHelper(getContext(), "journals-contacts", Constants.NOTIFICATION_CONTACTS_SYNC);
            notificationManager.cancel();

            try {
                ContentProviderClient contactsProvider = getContext().getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY);
                if (contactsProvider == null) {
                    App.log.severe("Couldn't access contacts provider");
                    syncResult.databaseError = true;
                    return;
                }

                AccountSettings settings = new AccountSettings(getContext(), account);
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                new RefreshCollections(account, CollectionInfo.Type.ADDRESS_BOOK).run();

                updateLocalAddressBooks(contactsProvider, account);

                contactsProvider.release();

                AccountManager accountManager = AccountManager.get(getContext());
                for (Account addressBookAccount : accountManager.getAccountsByType(App.getAddressBookAccountType())) {
                    App.log.log(Level.INFO, "Running sync for address book", addressBookAccount);
                    Bundle syncExtras = new Bundle(extras);
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
                    ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, syncExtras);
                }
            } catch (Exceptions.ServiceUnavailableException e) {
                syncResult.stats.numIoExceptions++;
                syncResult.delayUntil = (e.retryAfter > 0) ? e.retryAfter : Constants.DEFAULT_RETRY_DELAY;
            } catch (Exception | OutOfMemoryError e) {
                if (e instanceof ContactsStorageException || e instanceof SQLiteException) {
                    App.log.log(Level.SEVERE, "Couldn't prepare local address books", e);
                    syncResult.databaseError = true;
                }

                int syncPhase = R.string.sync_phase_journals;
                String title = getContext().getString(R.string.sync_error_contacts, account.name);

                notificationManager.setThrowable(e);

                final Intent detailsIntent = notificationManager.getDetailsIntent();
                detailsIntent.putExtra(KEY_ACCOUNT, account);
                if (!(e instanceof Exceptions.UnauthorizedException)) {
                    detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority);
                    detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase);
                }

                notificationManager.notify(title, getContext().getString(syncPhase));
            }

            App.log.info("Address book sync complete");
        }


        private void updateLocalAddressBooks(ContentProviderClient provider, Account account) throws ContactsStorageException, AuthenticatorException, OperationCanceledException, IOException {
            final Context context = getContext();
            EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();
            ServiceEntity service = JournalModel.Service.fetch(data, account.name, CollectionInfo.Type.ADDRESS_BOOK);

            Map<String, JournalEntity> remote = new HashMap<>();
            List<JournalEntity> remoteJournals = JournalEntity.getJournals(data, service);
            for (JournalEntity journalEntity : remoteJournals) {
                remote.put(journalEntity.getUid(), journalEntity);
            }

            LocalAddressBook[] local = LocalAddressBook.find(context, provider, account);

            // delete obsolete local address books
            for (LocalAddressBook addressBook : local) {
                String url = addressBook.getURL();
                if (!remote.containsKey(url)) {
                    App.log.fine("Deleting obsolete local address book " + url);
                    addressBook.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    JournalEntity journalEntity = remote.get(url);
                    App.log.fine("Updating local address book " + url + " with " + journalEntity);
                    addressBook.update(journalEntity);
                    // we already have a local collection for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local address books
            for (String url : remote.keySet()) {
                JournalEntity journalEntity = remote.get(url);
                App.log.info("Adding local address book " + journalEntity);
                LocalAddressBook.create(context, provider, account, journalEntity);
            }
        }
    }

}
