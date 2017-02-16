/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.TimeZone;

import org.apache.commons.lang3.StringUtils;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.ical4android.DateUtils;
import yuku.ambilwarna.AmbilWarnaDialog;

public class CreateCalendarActivity extends AppCompatActivity {
    public static final String EXTRA_ACCOUNT = "account";

    protected Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);

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

        Spinner spinner = (Spinner) findViewById(R.id.time_zone);
        String[] timeZones = TimeZone.getAvailableIDs();
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, timeZones));

        // select system time zone
        String defaultTimeZone = TimeZone.getDefault().getID();
        for (int i = 0; i < timeZones.length; i++) {
            if (timeZones[i].equals(defaultTimeZone)) {
                spinner.setSelection(i);
                break;
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
        CollectionInfo info = new CollectionInfo();

        Spinner spinner;

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

        spinner = (Spinner) findViewById(R.id.time_zone);
        net.fortuna.ical4j.model.TimeZone tz = DateUtils.tzRegistry.getTimeZone((String) spinner.getSelectedItem());
        if (tz != null) {
            Calendar cal = new Calendar();
            cal.getComponents().add(tz.getVTimeZone());
            info.timeZone = cal.toString();
        }

        if (ok) {
            info.type = CollectionInfo.Type.CALENDAR;
            info.supportsVEVENT = true;
            info.selected = true;

            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }
}
