/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.EntryEntity;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.resource.LocalCalendar;

import java.util.Locale;

import at.bitfire.ical4android.CalendarStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import yuku.ambilwarna.AmbilWarnaDialog;

public class CreateCalendarActivity extends AppCompatActivity {
    public static final String EXTRA_ACCOUNT = "account",
                               EXTRA_COLLECTION_INFO = "collectionInfo",
                               EXTRA_ALLOW_DELETE = "allowDelete";

    protected Account account;
    protected CollectionInfo info;
    protected Boolean allowDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);
        allowDelete = (Boolean) getIntent().getExtras().get(EXTRA_ALLOW_DELETE);
        allowDelete = (allowDelete == null) ? false : allowDelete;

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_create_calendar);
        final View colorSquare = findViewById(R.id.color);
        colorSquare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AmbilWarnaDialog(CreateCalendarActivity.this, ((ColorDrawable) colorSquare.getBackground()).getColor(), true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                    @Override
                    public void onCancel(AmbilWarnaDialog dialog) {
                    }

                    @Override
                    public void onOk(AmbilWarnaDialog dialog, int color) {
                        colorSquare.setBackgroundColor(color);
                    }
                }).show();
            }
        });

        if (info != null) {
            final EditText edit = (EditText) findViewById(R.id.display_name);
            edit.setText(info.displayName);

            final EditText desc = (EditText) findViewById(R.id.description);
            desc.setText(info.description);

            if (info.color != null) {
                colorSquare.setBackgroundColor(info.color);
            } else {
                colorSquare.setBackgroundColor(LocalCalendar.defaultColor);
            }

            final TextView stats = (TextView) findViewById(R.id.stats);
            final View statsGroup = findViewById(R.id.stats_group);
            try {
                LocalCalendar resource = (LocalCalendar) LocalCalendar.find(account, this.getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI),
                        LocalCalendar.Factory.INSTANCE, CalendarContract.Calendars.NAME + "=?", new String[]{info.url})[0];
                long count = resource.count();
                EntityDataStore<Persistable> data = ((App) getApplication()).getData();
                int entryCount = -1;
                final JournalEntity journalEntity = data.select(JournalEntity.class).where(JournalEntity.UID.eq(info.url)).limit(1).get().firstOrNull();
                if (journalEntity != null) {
                    entryCount = data.count(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value();
                }
                stats.setText(String.format(Locale.getDefault(), "Events: %d, Journal entries: %d", count, entryCount));
                statsGroup.setVisibility(View.VISIBLE);
            } catch (CalendarStorageException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (info == null) {
            getMenuInflater().inflate(R.menu.activity_create_collection, menu);
        } else {
            getMenuInflater().inflate(R.menu.activity_edit_collection, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
            NavUtils.navigateUpTo(this, intent);
            return true;
        }
        return false;
    }

    public void onDeleteCollection(MenuItem item) {
        if (!allowDelete) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_error_dark)
                    .setTitle(R.string.account_delete_collection_last_title)
                    .setMessage(R.string.account_delete_collection_last_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            DeleteCollectionFragment.ConfirmDeleteCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }

    public void onCreateCollection(MenuItem item) {
        boolean ok = true;
        if (info == null) {
            info = new CollectionInfo();
        }

        EditText edit = (EditText) findViewById(R.id.display_name);
        info.displayName = edit.getText().toString();
        if (TextUtils.isEmpty(info.displayName)) {
            edit.setError(getString(R.string.create_collection_display_name_required));
            ok = false;
        }

        edit = (EditText) findViewById(R.id.description);
        info.description = StringUtils.trimToNull(edit.getText().toString());

        View view = findViewById(R.id.color);
        info.color = ((ColorDrawable) view.getBackground()).getColor();

        if (ok) {
            info.type = CollectionInfo.Type.CALENDAR;
            info.supportsVEVENT = true;
            info.selected = true;

            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }
}
