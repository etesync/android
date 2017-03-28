package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.CalendarAccount;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalEvent;
import com.etesync.syncadapter.ui.ImportFragment;

import java.util.List;

import static com.etesync.syncadapter.resource.CalendarAccount.loadAll;

public class ImportActivity extends AppCompatActivity {
    public final static String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    private Account account;
    protected CollectionInfo info;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, ImportActivity.class);
        intent.putExtra(ImportActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(ImportActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        findViewById(R.id.import_button_account).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View aView) {
                importAccount();
            }
        });

        findViewById(R.id.import_button_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View aView) {
                importFile();
            }
        });
    }

    private void importFile() {
        getSupportFragmentManager().beginTransaction()
                .add(ImportFragment.newInstance(account, info), null)
                .commit();

    }

    private void importAccount() {
        final List<CalendarAccount> calendarAccountList = CalendarAccount.loadAll(getContentResolver());

        ExpandableListView listCalendar = (ExpandableListView) findViewById(R.id.calendars);

        final ExpandableListAdapter adapter = new ExpandableListAdapter(this, calendarAccountList);
        listCalendar.setAdapter(adapter);

        listCalendar.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView aExpandableListView, View aView, int groupPosition, int childPosition, long aL) {
                Toast.makeText(ImportActivity.this,
                        calendarAccountList.get(groupPosition).calendars.get(childPosition).toString(),
                        Toast.LENGTH_SHORT).show();
                //todo import
                LocalCalendar localCalendar = (LocalCalendar) calendarAccountList.get(groupPosition).calendars.get(childPosition);
                
                return false;
            }
        });
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
    }

    @Override
    protected void onPause() {
        super.onPause();

        App app = (App) getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = false;
    }
}
