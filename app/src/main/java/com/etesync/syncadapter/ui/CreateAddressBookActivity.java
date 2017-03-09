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
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.LocalAddressBook;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Text;

import java.util.Locale;

import at.bitfire.vcard4android.ContactsStorageException;

public class CreateAddressBookActivity extends AppCompatActivity {
    public static final String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    protected Account account;
    protected CollectionInfo info;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_create_address_book);

        if (info != null) {
            final EditText edit = (EditText) findViewById(R.id.display_name);
            edit.setText(info.displayName);

            final EditText desc = (EditText) findViewById(R.id.description);
            desc.setText(info.description);

            final TextView stats = (TextView) findViewById(R.id.stats);
            final View statsGroup = findViewById(R.id.stats_group);
            try {
                LocalAddressBook resource = new LocalAddressBook(account, this.getContentResolver().acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI));
                long count = resource.count();
                stats.setText(String.format(Locale.getDefault(), "Contacts: %d", count));
                statsGroup.setVisibility(View.VISIBLE);
            } catch (ContactsStorageException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_create_collection, menu);
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

    public void onCreateCollection(MenuItem item) {
        boolean ok = true;
        if (info == null) {
            info = new CollectionInfo();
        }

        EditText edit = (EditText)findViewById(R.id.display_name);
        info.displayName = edit.getText().toString();
        if (TextUtils.isEmpty(info.displayName)) {
            edit.setError(getString(R.string.create_collection_display_name_required));
            ok = false;
        }

        edit = (EditText)findViewById(R.id.description);
        info.description = StringUtils.trimToNull(edit.getText().toString());

        if (ok) {
            info.type = CollectionInfo.Type.ADDRESS_BOOK;

            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }
}
