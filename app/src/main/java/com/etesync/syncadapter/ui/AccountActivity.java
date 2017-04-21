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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
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

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.AccountUpdateService;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.ServiceEntity;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.ui.setup.SetupUserInfoFragment;
import com.etesync.syncadapter.utils.HintManager;
import com.etesync.syncadapter.utils.ShowcaseBuilder;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.ical4android.TaskProvider;
import at.bitfire.vcard4android.ContactsStorageException;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import tourguide.tourguide.ToolTip;

import static android.content.ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;

public class AccountActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener, LoaderManager.LoaderCallbacks<AccountActivity.AccountInfo>, Refreshable {
    public static final String EXTRA_ACCOUNT = "account";
    private static final String HINT_VIEW_COLLECTION = "ViewCollection";

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

        Drawable icMenu = ContextCompat.getDrawable(this, R.drawable.ic_menu_light);

        // CardDAV toolbar
        tbCardDAV = (Toolbar)findViewById(R.id.carddav_menu);
        tbCardDAV.setTitle(R.string.settings_carddav);

        // CalDAV toolbar
        tbCalDAV = (Toolbar)findViewById(R.id.caldav_menu);
        tbCalDAV.setOverflowIcon(icMenu);
        tbCalDAV.inflateMenu(R.menu.caldav_actions);
        tbCalDAV.setOnMenuItemClickListener(this);
        tbCalDAV.setTitle(R.string.settings_caldav);

        // load CardDAV/CalDAV journals
        getLoaderManager().initLoader(0, getIntent().getExtras(), this);

        if (!HintManager.getHintSeen(this, HINT_VIEW_COLLECTION)) {
            ShowcaseBuilder.getBuilder(this)
                    .setToolTip(new ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_view_collection)))
                    .playOn(tbCardDAV);
            HintManager.setHintSeen(this, HINT_VIEW_COLLECTION, true);
        }

        if (!SetupUserInfoFragment.hasUserInfo(this, account)) {
            SetupUserInfoFragment.newInstance(account).show(getSupportFragmentManager(), null);
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
            case R.id.show_fingerprint:
                View view = getLayoutInflater().inflate(R.layout.fingerprint_alertdialog, null);
                view.findViewById(R.id.body).setVisibility(View.GONE);
                ((TextView) view.findViewById(R.id.fingerprint)).setText(getFormattedFingerprint());
                AlertDialog dialog = new AlertDialog.Builder(AccountActivity.this)
                        .setIcon(R.drawable.ic_fingerprint_dark)
                        .setTitle(R.string.show_fingperprint_title)
                        .setView(view)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).create();
                dialog.show();
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
            final ArrayAdapter<JournalEntity> adapter = (ArrayAdapter)list.getAdapter();
            final JournalEntity journalEntity = adapter.getItem(position);
            final CollectionInfo info = journalEntity.getInfo();

            startActivity(ViewCollectionActivity.newIntent(AccountActivity.this, account, info));
        }
    };

    private String getFormattedFingerprint() {
        AccountSettings settings = null;
        try {
            settings = new AccountSettings(this, account);
            return Crypto.AsymmetricCryptoManager.getPrettyKeyFingerprint(settings.getKeyPair().getPublicKey());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* LOADERS AND LOADED DATA */

    protected static class AccountInfo {
        ServiceInfo carddav, caldav;

        public static class ServiceInfo {
            long id;
            boolean refreshing;

            List<JournalEntity> journals;
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

            final CollectionListAdapter adapter = new CollectionListAdapter(this, account);
            adapter.addAll(info.carddav.journals);
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

            final CollectionListAdapter adapter = new CollectionListAdapter(this, account);
            adapter.addAll(info.caldav.journals);
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

            EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();

            for (ServiceEntity serviceEntity : data.select(ServiceEntity.class).where(ServiceEntity.ACCOUNT.eq(account.name)).get()) {
                long id = serviceEntity.getId();
                CollectionInfo.Type service = serviceEntity.getType();
                if (service.equals(CollectionInfo.Type.ADDRESS_BOOK)) {
                    info.carddav = new AccountInfo.ServiceInfo();
                    info.carddav.id = id;
                    info.carddav.refreshing = (davService != null && davService.isRefreshing(id)) || ContentResolver.isSyncActive(account, App.getAddressBooksAuthority());
                    info.carddav.journals = JournalEntity.getJournals(data, serviceEntity);

                    AccountManager accountManager = AccountManager.get(getContext());
                    for (Account addrBookAccount : accountManager.getAccountsByType(App.getAddressBookAccountType())) {
                        LocalAddressBook addressBook = new LocalAddressBook(getContext(), addrBookAccount, null);
                        try {
                            if (account.equals(addressBook.getMainAccount()))
                                info.carddav.refreshing |= ContentResolver.isSyncActive(addrBookAccount, ContactsContract.AUTHORITY);
                        } catch(ContactsStorageException e) {
                        }
                    }
                } else if (service.equals(CollectionInfo.Type.CALENDAR)) {
                    info.caldav = new AccountInfo.ServiceInfo();
                    info.caldav.id = id;
                    info.caldav.refreshing = (davService != null && davService.isRefreshing(id)) ||
                            ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY) ||
                            ContentResolver.isSyncActive(account, TaskProvider.ProviderName.OpenTasks.authority);
                    info.caldav.journals = JournalEntity.getJournals(data, serviceEntity);
                }
            }
            return info;
        }
    }


    /* LIST ADAPTERS */

    public static class CollectionListAdapter extends ArrayAdapter<JournalEntity> {
        private Account account;

        public CollectionListAdapter(Context context, Account account) {
            super(context, R.layout.account_collection_item);
            this.account = account;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.account_collection_item, parent, false);

            final JournalEntity journalEntity = getItem(position);
            final CollectionInfo info = journalEntity.getInfo();

            TextView tv = (TextView)v.findViewById(R.id.title);
            tv.setText(TextUtils.isEmpty(info.displayName) ? info.uid : info.displayName);

            tv = (TextView)v.findViewById(R.id.description);
            if (TextUtils.isEmpty(info.description))
                tv.setVisibility(View.GONE);
            else {
                tv.setVisibility(View.VISIBLE);
                tv.setText(info.description);
            }

            final View vColor = v.findViewById(R.id.color);
            if (info.type.equals(CollectionInfo.Type.ADDRESS_BOOK)) {
                vColor.setVisibility(View.GONE);
            } else {
                if (info.color != null) {
                    vColor.setBackgroundColor(info.color);
                } else {
                    vColor.setBackgroundColor(LocalCalendar.defaultColor);
                }
            }

            View readOnly = v.findViewById(R.id.read_only);
            readOnly.setVisibility(journalEntity.isReadOnly() ? View.VISIBLE : View.GONE);

            final View shared = v.findViewById(R.id.shared);
            boolean isOwner = (journalEntity.getOwner() == null) || journalEntity.getOwner().equals(account.name);
            shared.setVisibility(isOwner ? View.GONE : View.VISIBLE);

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
                App.getAddressBooksAuthority(),
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
