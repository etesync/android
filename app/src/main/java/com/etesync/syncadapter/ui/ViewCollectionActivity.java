/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Objects;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

import static com.etesync.syncadapter.R.id.stats;

public class ViewCollectionActivity extends AppCompatActivity implements Refreshable {
    public final static String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    private Account account;
    protected CollectionInfo info;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, ViewCollectionActivity.class);
        intent.putExtra(ViewCollectionActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(ViewCollectionActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    public void refresh() {
        EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();

        final JournalEntity journalEntity = JournalEntity.fetch(data, info.url);
        if ((journalEntity == null) || journalEntity.isDeleted()) {
            finish();
            return;
        }

        info = journalEntity.getInfo();

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_view_collection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!getSupportFragmentManager().popBackStackImmediate()) {
                finish();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        App app = (App) getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = true;

        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();

        App app = (App) getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = false;
    }

    public void onEditCollection(MenuItem item) {
        startActivity(EditCollectionActivity.newIntent(this, account, info));
    }

    public void onImport(MenuItem item) {
        startActivity(ImportActivity.newIntent(ViewCollectionActivity.this, account, info));
    }

    private class LoadCountTask extends AsyncTask<Void, Void, Long> {
        private int entryCount;

        @Override
        protected Long doInBackground(Void... aVoids) {
            EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();

            final JournalEntity journalEntity = JournalEntity.fetch(data, info.url);

            entryCount = data.count(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value();
            long count;

            if (info.type == CollectionInfo.Type.CALENDAR) {
                try {
                    LocalCalendar resource = LocalCalendar.findByName(account, getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI), LocalCalendar.Factory.INSTANCE, info.url);
                    count = resource.count();
                } catch (FileNotFoundException | CalendarStorageException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                try {
                    LocalAddressBook resource = new LocalAddressBook(account, getContentResolver().acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI));
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
