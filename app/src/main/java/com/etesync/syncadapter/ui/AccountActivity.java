/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.etesync.syncadapter.AccountUpdateService;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.ServiceDB.OpenHelper;
import com.etesync.syncadapter.model.ServiceDB.Services;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.utils.HintManager;
import com.etesync.syncadapter.utils.ShowcaseBuilder;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.ical4android.TaskProvider;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import lombok.Cleanup;
import tourguide.tourguide.ToolTip;

import static android.content.ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;

public class AccountActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener, LoaderManager.LoaderCallbacks<AccountActivity.AccountInfo>, Refreshable {
    public static final String EXTRA_ACCOUNT = "account";
    private static final HintManager.Hint HINT_VIEW_COLLECTION = HintManager.registerHint("ViewCollection");

    private Account account;
    private AccountInfo accountInfo;

    ListView listCalDAV, listCardDAV;
    Toolbar tbCardDAV, tbCalDAV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);
        setTitle(account.name);

        setContentView(R.layout.activity_account);

        Drawable icMenu = Build.VERSION.SDK_INT >= 21 ? getDrawable(R.drawable.ic_menu_light) :
                getResources().getDrawable(R.drawable.ic_menu_light);

        // CardDAV toolbar
        tbCardDAV = (Toolbar)findViewById(R.id.carddav_menu);
        tbCardDAV.setTitle(R.string.settings_carddav);

        // CalDAV toolbar
        tbCalDAV = (Toolbar)findViewById(R.id.caldav_menu);
        tbCalDAV.setOverflowIcon(icMenu);
        tbCalDAV.inflateMenu(R.menu.caldav_actions);
        tbCalDAV.setOnMenuItemClickListener(this);
        tbCalDAV.setTitle(R.string.settings_caldav);

        // load CardDAV/CalDAV collections
        getLoaderManager().initLoader(0, getIntent().getExtras(), this);

        if (!HintManager.getHintSeen(this, HINT_VIEW_COLLECTION)) {
            ShowcaseBuilder.getBuilder(this)
                    .setToolTip(new ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_view_collection)))
                    .playOn(tbCardDAV);
            HintManager.setHintSeen(this, HINT_VIEW_COLLECTION, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CustomCertManager certManager = ((App)getApplicationContext()).getCertManager();
        if (certManager != null)
            certManager.appInForeground = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomCertManager certManager = ((App)getApplicationContext()).getCertManager();
        if (certManager != null)
            certManager.appInForeground = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_account, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_now:
                requestSync();
                break;
            case R.id.settings:
                Intent intent = new Intent(this, AccountSettingsActivity.class);
                intent.putExtra(Constants.KEY_ACCOUNT, account);
                startActivity(intent);
                break;
            case R.id.delete_account:
                new AlertDialog.Builder(AccountActivity.this)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.account_delete_confirmation_title)
                        .setMessage(R.string.account_delete_confirmation_text)
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteAccount();
                            }
                        })
                        .show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create_calendar:
                CollectionInfo info = new CollectionInfo();
                info.type = CollectionInfo.Type.CALENDAR;
                startActivity(CreateCollectionActivity.newIntent(AccountActivity.this, account, info));
                break;
        }
        return false;
    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final ListView list = (ListView)parent;
            final ArrayAdapter<CollectionInfo> adapter = (ArrayAdapter)list.getAdapter();
            final CollectionInfo info = adapter.getItem(position);

            startActivity(ViewCollectionActivity.newIntent(AccountActivity.this, account, info));
        }
    };

    /* LOADERS AND LOADED DATA */

    protected static class AccountInfo {
        ServiceInfo carddav, caldav;

        public static class ServiceInfo {
            long id;
            boolean refreshing;

            List<CollectionInfo> collections;
        }
    }

    @Override
    public Loader<AccountInfo> onCreateLoader(int id, Bundle args) {
        return new AccountLoader(this, account);
    }

    @Override
    public void refresh() {
        getLoaderManager().restartLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public void onLoadFinished(Loader<AccountInfo> loader, final AccountInfo info) {
        accountInfo = info;

        CardView card = (CardView)findViewById(R.id.carddav);
        if (info.carddav != null) {
            ProgressBar progress = (ProgressBar)findViewById(R.id.carddav_refreshing);
            progress.setVisibility(info.carddav.refreshing ? View.VISIBLE : View.GONE);

            listCardDAV = (ListView)findViewById(R.id.address_books);
            listCardDAV.setEnabled(!info.carddav.refreshing);
            listCardDAV.setAlpha(info.carddav.refreshing ? 0.5f : 1);

            AddressBookAdapter adapter = new AddressBookAdapter(this);
            adapter.addAll(info.carddav.collections);
            listCardDAV.setAdapter(adapter);
            listCardDAV.setOnItemClickListener(onItemClickListener);
        } else
            card.setVisibility(View.GONE);

        card = (CardView)findViewById(R.id.caldav);
        if (info.caldav != null) {
            ProgressBar progress = (ProgressBar)findViewById(R.id.caldav_refreshing);
            progress.setVisibility(info.caldav.refreshing ? View.VISIBLE : View.GONE);

            listCalDAV = (ListView)findViewById(R.id.calendars);
            listCalDAV.setEnabled(!info.caldav.refreshing);
            listCalDAV.setAlpha(info.caldav.refreshing ? 0.5f : 1);

            final CalendarAdapter adapter = new CalendarAdapter(this);
            adapter.addAll(info.caldav.collections);
            listCalDAV.setAdapter(adapter);
            listCalDAV.setOnItemClickListener(onItemClickListener);
        } else
            card.setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<AccountInfo> loader) {
        if (listCardDAV != null)
            listCardDAV.setAdapter(null);

        if (listCalDAV != null)
            listCalDAV.setAdapter(null);
    }


    private static class AccountLoader extends AsyncTaskLoader<AccountInfo> implements AccountUpdateService.RefreshingStatusListener, ServiceConnection, SyncStatusObserver {
        private final Account account;
        private AccountUpdateService.InfoBinder davService;
        private Object syncStatusListener;

        public AccountLoader(Context context, Account account) {
            super(context);
            this.account = account;
        }

        @Override
        protected void onStartLoading() {
            syncStatusListener = ContentResolver.addStatusChangeListener(SYNC_OBSERVER_TYPE_ACTIVE, this);

            getContext().bindService(new Intent(getContext(), AccountUpdateService.class), this, Context.BIND_AUTO_CREATE);
        }

        @Override
        protected void onStopLoading() {
            davService.removeRefreshingStatusListener(this);
            getContext().unbindService(this);

            if (syncStatusListener != null)
                ContentResolver.removeStatusChangeListener(syncStatusListener);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            davService = (AccountUpdateService.InfoBinder)service;
            davService.addRefreshingStatusListener(this, false);

            forceLoad();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            davService = null;
        }

        @Override
        public void onDavRefreshStatusChanged(long id, boolean refreshing) {
            forceLoad();
        }

        @Override
        public void onStatusChanged(int which) {
            forceLoad();
        }

        @Override
        public AccountInfo loadInBackground() {
            AccountInfo info = new AccountInfo();

            @Cleanup OpenHelper dbHelper = new OpenHelper(getContext());
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();

            @Cleanup Cursor cursor = db.query(
                    Services._TABLE,
                    new String[] { Services.ID, Services.SERVICE },
                    Services.ACCOUNT_NAME + "=?", new String[] { account.name },
                    null, null, null);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String service = cursor.getString(1);
                if (Services.SERVICE_CARDDAV.equals(service)) {
                    info.carddav = new AccountInfo.ServiceInfo();
                    info.carddav.id = id;
                    info.carddav.refreshing = (davService != null && davService.isRefreshing(id)) || ContentResolver.isSyncActive(account, ContactsContract.AUTHORITY);
                    info.carddav.collections = JournalEntity.getCollections(data, id);
                } else if (Services.SERVICE_CALDAV.equals(service)) {
                    info.caldav = new AccountInfo.ServiceInfo();
                    info.caldav.id = id;
                    info.caldav.refreshing = (davService != null && davService.isRefreshing(id)) ||
                            ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY) ||
                            ContentResolver.isSyncActive(account, TaskProvider.ProviderName.OpenTasks.authority);
                    info.caldav.collections = JournalEntity.getCollections(data, id);
                }
            }
            return info;
        }
    }


    /* LIST ADAPTERS */

    public static class AddressBookAdapter extends ArrayAdapter<CollectionInfo> {
        public AddressBookAdapter(Context context) {
            super(context, R.layout.account_carddav_item);
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.account_carddav_item, parent, false);

            final CollectionInfo info = getItem(position);

            TextView tv = (TextView)v.findViewById(R.id.title);
            tv.setText(TextUtils.isEmpty(info.displayName) ? info.url : info.displayName);

            tv = (TextView)v.findViewById(R.id.description);
            if (TextUtils.isEmpty(info.description))
                tv.setVisibility(View.GONE);
            else {
                tv.setVisibility(View.VISIBLE);
                tv.setText(info.description);
            }

            tv = (TextView)v.findViewById(R.id.read_only);
            tv.setVisibility(info.readOnly ? View.VISIBLE : View.GONE);

            return v;
        }
    }

    public static class CalendarAdapter extends ArrayAdapter<CollectionInfo> {
        public CalendarAdapter(Context context) {
            super(context, R.layout.account_caldav_item);
        }

        @Override
        public View getView(final int position, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.account_caldav_item, parent, false);

            final CollectionInfo info = getItem(position);

            View vColor = v.findViewById(R.id.color);
            if (info.color != null) {
                vColor.setBackgroundColor(info.color);
            } else {
                vColor.setBackgroundColor(LocalCalendar.defaultColor);
            }

            TextView tv = (TextView)v.findViewById(R.id.title);
            tv.setText(TextUtils.isEmpty(info.displayName) ? info.url : info.displayName);

            tv = (TextView)v.findViewById(R.id.description);
            if (TextUtils.isEmpty(info.description))
                tv.setVisibility(View.GONE);
            else {
                tv.setVisibility(View.VISIBLE);
                tv.setText(info.description);
            }

            tv = (TextView)v.findViewById(R.id.read_only);
            tv.setVisibility(info.readOnly ? View.VISIBLE : View.GONE);

            return v;
        }
    }

    /* USER ACTIONS */

    private void deleteAccount() {
        AccountManager accountManager = AccountManager.get(this);

        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        if (future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                            finish();
                    } catch(OperationCanceledException|IOException|AuthenticatorException e) {
                        App.log.log(Level.SEVERE, "Couldn't remove account", e);
                    }
                }
            }, null);
        else
            accountManager.removeAccount(account, new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    try {
                        if (future.getResult())
                            finish();
                    } catch (OperationCanceledException|IOException|AuthenticatorException e) {
                        App.log.log(Level.SEVERE, "Couldn't remove account", e);
                    }
                }
            }, null);
    }

    protected static void requestSync(Account account) {
        String authorities[] = {
                ContactsContract.AUTHORITY,
                CalendarContract.AUTHORITY,
                TaskProvider.ProviderName.OpenTasks.authority
        };

        for (String authority : authorities) {
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);        // manual sync
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);     // run immediately (don't queue)
            ContentResolver.requestSync(account, authority, extras);
        }
    }

    private void requestSync() {
        requestSync(account);
        Snackbar.make(findViewById(R.id.parent), R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show();
    }

}
