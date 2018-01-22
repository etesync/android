/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.EntryEntity;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.ui.importlocal.ImportActivity;
import com.etesync.syncadapter.ui.journalviewer.ListEntriesFragment;
import com.etesync.syncadapter.utils.HintManager;
import com.etesync.syncadapter.utils.ShowcaseBuilder;

import java.io.FileNotFoundException;
import java.util.Locale;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

public class ViewCollectionActivity extends BaseActivity implements Refreshable {
    private final static String HINT_IMPORT = "Import";
    public final static String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    private Account account;
    private JournalEntity journalEntity;
    protected CollectionInfo info;
    private boolean isOwner;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, ViewCollectionActivity.class);
        intent.putExtra(ViewCollectionActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(ViewCollectionActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    public void refresh() {
        EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();

        journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid);
        if ((journalEntity == null) || journalEntity.isDeleted()) {
            finish();
            return;
        }

        info = journalEntity.getInfo();
        isOwner = journalEntity.isOwner(account.name);

        final View colorSquare = findViewById(R.id.color);
        if (info.type == CollectionInfo.Type.CALENDAR) {
            if (info.color != null) {
                colorSquare.setBackgroundColor(info.color);
            } else {
                colorSquare.setBackgroundColor(LocalCalendar.defaultColor);
            }
        } else {
            colorSquare.setVisibility(View.GONE);
        }

        new LoadCountTask().execute();

        final TextView title = (TextView) findViewById(R.id.display_name);
        title.setText(info.displayName);

        final TextView desc = (TextView) findViewById(R.id.description);
        desc.setText(info.description);

        final TextView owner = (TextView) findViewById(R.id.owner);
        if (isOwner) {
            owner.setVisibility(View.GONE);
        } else {
            owner.setVisibility(View.VISIBLE);
            owner.setText(getString(R.string.account_owner, journalEntity.getOwner()));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_collection_activity);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.list_entries_container, ListEntriesFragment.newInstance(info))
                    .commit();
        }

        refresh();

        final TextView title = (TextView) findViewById(R.id.display_name);
        if (!HintManager.getHintSeen(this, HINT_IMPORT)) {
            TourGuide tourGuide = ShowcaseBuilder.getBuilder(this)
                    .setToolTip(new ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_import)).setGravity(Gravity.BOTTOM))
                    .setPointer(null);
            tourGuide.mOverlay.setHoleRadius(0);
            tourGuide.playOn(title);
            HintManager.setHintSeen(this, HINT_IMPORT, true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_view_collection, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    public void onEditCollection(MenuItem item) {
        if (isOwner) {
            startActivity(EditCollectionActivity.newIntent(this, account, info));
        } else {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(getString(R.string.edit_owner_only, journalEntity.getOwner()))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create();
            dialog.show();
        }
    }

    public void onImport(MenuItem item) {
        startActivity(ImportActivity.newIntent(ViewCollectionActivity.this, account, info));
    }

    public void onManageMembers(MenuItem item) {
        if (info.version < 2) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(R.string.members_old_journals_not_allowed)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create();
            dialog.show();
        } else if (isOwner) {
            startActivity(CollectionMembersActivity.newIntent(this, account, info));
        } else {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(getString(R.string.members_owner_only, journalEntity.getOwner()))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create();
            dialog.show();
        }
    }

    private class LoadCountTask extends AsyncTask<Void, Void, Long> {
        private int entryCount;

        @Override
        protected Long doInBackground(Void... aVoids) {
            EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();

            final JournalEntity journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid);

            entryCount = data.count(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value();
            long count;

            if (info.type == CollectionInfo.Type.CALENDAR) {
                try {
                    ContentProviderClient providerClient = getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI);
                    LocalCalendar resource = LocalCalendar.findByName(account, providerClient, LocalCalendar.Factory.INSTANCE, info.uid);
                    providerClient.release();
                    if (resource == null) {
                        return null;
                    }
                    count = resource.count();
                } catch (FileNotFoundException | CalendarStorageException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                try {
                    ContentProviderClient providerClient = getContentResolver().acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI);
                    LocalAddressBook resource = LocalAddressBook.findByUid(ViewCollectionActivity.this, providerClient, account, info.uid);
                    providerClient.release();
                    if (resource == null) {
                        return null;
                    }
                    count = resource.count();
                } catch (ContactsStorageException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            return count;
        }

        @Override
        protected void onPostExecute(Long result) {
            final TextView stats = (TextView) findViewById(R.id.stats);
            findViewById(R.id.progressBar).setVisibility(View.GONE);

            if (result == null) {
                stats.setText("Stats loading error.");
            } else {
                if (info.type == CollectionInfo.Type.CALENDAR) {
                    stats.setText(String.format(Locale.getDefault(), "Events: %d, Journal entries: %d",
                            result, entryCount));
                } else {
                    stats.setText(String.format(Locale.getDefault(), "Contacts: %d, Journal Entries: %d",
                            result, entryCount));
                }
            }
        }
    }
}
