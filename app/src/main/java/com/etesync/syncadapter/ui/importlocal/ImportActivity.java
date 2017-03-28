package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.CalendarAccount;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalEvent;
import com.etesync.syncadapter.ui.ImportFragment;

import java.util.List;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;

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
                new ImportEvents().execute(calendarAccountList.get(groupPosition).calendars.get(childPosition));
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

    public class ExpandableListAdapter extends BaseExpandableListAdapter {

        private Context context;
        private List<CalendarAccount> calendarAccounts;

        public ExpandableListAdapter(Context context, List<CalendarAccount> calendarAccounts) {
            this.context = context;
            this.calendarAccounts = calendarAccounts;
        }

        @Override
        public Object getChild(int groupPosition, int childPosititon) {
            return calendarAccounts.get(groupPosition).calendars
                    .get(childPosititon).getDisplayName();
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {

            final String childText = (String) getChild(groupPosition, childPosition);

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_item, null);
            }
            //Todo add viewholder after we decide about the UI

            TextView txtListChild = (TextView) convertView
                    .findViewById(R.id.lblListItem);

            txtListChild.setText(childText);
            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return calendarAccounts.get(groupPosition).calendars
                    .size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return calendarAccounts.get(groupPosition).toString();
        }

        @Override
        public int getGroupCount() {
            return calendarAccounts.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            String headerTitle = (String) getGroup(groupPosition);
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_group, null);
            }
            //Todo add viewholder after we decide about the UI

            TextView lblListHeader = (TextView) convertView
                    .findViewById(R.id.lblListHeader);
            lblListHeader.setTypeface(null, Typeface.BOLD);
            lblListHeader.setText(headerTitle);

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    protected class ImportEvents extends AsyncTask<LocalCalendar, Integer, Boolean> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(ImportActivity.this);
            progressDialog.setTitle(R.string.import_dialog_title);
            progressDialog.setMessage(getString(R.string.import_dialog_adding_entries));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setIndeterminate(false);
            progressDialog.setIcon(R.drawable.ic_import_export_black);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(LocalCalendar... calendars) {
            return importEvents(calendars[0]);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null)
                progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressDialog.dismiss();
        }

        private boolean importEvents(LocalCalendar fromCalendar) {
            try {
                LocalCalendar localCalendar = LocalCalendar.findByName(account,
                        ImportActivity.this.getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI),
                        LocalCalendar.Factory.INSTANCE, info.url);
                LocalEvent[] localEvents = fromCalendar.getAll();
                progressDialog.setMax(localEvents.length);
                int progress = 0;
                for (LocalEvent currentLocalEvent : localEvents) {
                    Event event = currentLocalEvent.getEvent();
                    try {
                        LocalEvent localEvent = new LocalEvent(localCalendar, event, event.uid, null);
                        localEvent.addAsDirty();
                    } catch (CalendarStorageException e) {
                        e.printStackTrace();
                    }
                    publishProgress(++progress);
                }
                return true;
            } catch (Exception aE) {
                aE.printStackTrace();
                return false;
            }
        }
    }
}
