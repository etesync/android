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
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.model.CollectionInfo;

import org.apache.commons.lang3.StringUtils;

import yuku.ambilwarna.AmbilWarnaDialog;

public class CreateCollectionActivity extends AppCompatActivity {
    static final String EXTRA_ACCOUNT = "account",
                        EXTRA_COLLECTION_INFO = "collectionInfo";

    protected Account account;
    protected CollectionInfo info;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, CreateCollectionActivity.class);
        intent.putExtra(CreateCollectionActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(CreateCollectionActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_create_collection);

        if (info.type == CollectionInfo.Type.CALENDAR) {
            setTitle(R.string.create_calendar);

            final View colorSquare = findViewById(R.id.color);
            colorSquare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AmbilWarnaDialog(CreateCollectionActivity.this, ((ColorDrawable) colorSquare.getBackground()).getColor(), true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
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
        } else {
            setTitle(R.string.create_addressbook);

            final View colorGroup = findViewById(R.id.color_group);
            colorGroup.setVisibility(View.GONE);
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

        EditText edit = (EditText) findViewById(R.id.display_name);
        info.displayName = edit.getText().toString();
        if (TextUtils.isEmpty(info.displayName)) {
            edit.setError(getString(R.string.create_collection_display_name_required));
            ok = false;
        }

        edit = (EditText) findViewById(R.id.description);
        info.description = StringUtils.trimToNull(edit.getText().toString());

        if (ok) {
            if (info.type == CollectionInfo.Type.CALENDAR) {
                View view = findViewById(R.id.color);
                info.color = ((ColorDrawable) view.getBackground()).getColor();

                info.supportsVEVENT = true;
            }

            info.selected = true;

            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }
}
