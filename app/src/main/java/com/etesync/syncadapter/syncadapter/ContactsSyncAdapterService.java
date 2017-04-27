/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.NotificationHelper;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;
import com.etesync.syncadapter.model.ServiceDB;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.ui.DebugInfoActivity;

import java.util.logging.Level;

import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import okhttp3.HttpUrl;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;

public class ContactsSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new ContactsSyncAdapter(this);
    }


    private static class ContactsSyncAdapter extends SyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);
            NotificationHelper notificationManager = new NotificationHelper(getContext(), "journals-contacts", Constants.NOTIFICATION_CONTACTS_SYNC);
            notificationManager.cancel();

            try {
                LocalAddressBook addressBook = new LocalAddressBook(getContext(), account, provider);

                AccountSettings settings;
                try {
                    settings = new AccountSettings(getContext(), addressBook.getMainAccount());
                } catch (InvalidAccountException|ContactsStorageException e) {
                    App.log.info("Skipping sync due to invalid account.");
                    App.log.info(e.getLocalizedMessage());
                    return;
                }

                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                App.log.info("Synchronizing address book: "  + addressBook.getURL());
                App.log.info("Taking settings from: "  + addressBook.getMainAccount());

                HttpUrl principal = HttpUrl.get(settings.getUri());
                ContactsSyncManager syncManager = new ContactsSyncManager(getContext(), account, settings, extras, authority, provider, syncResult, addressBook, principal);
                syncManager.performSync();
            } catch (Exception | OutOfMemoryError e) {
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

            App.log.info("Contacts sync complete");
        }
    }

}
