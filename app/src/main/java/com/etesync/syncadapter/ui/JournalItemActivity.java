package com.etesync.syncadapter.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.SyncEntry;
import com.etesync.syncadapter.resource.LocalCalendar;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

import static com.etesync.syncadapter.ui.journalviewer.ListEntriesFragment.setJournalEntryView;

public class JournalItemActivity extends BaseActivity implements Refreshable {
    private static final String KEY_SYNC_ENTRY = "syncEntry";
    private JournalEntity journalEntity;
    protected CollectionInfo info;
    private SyncEntry syncEntry;

    public static Intent newIntent(Context context, CollectionInfo info, SyncEntry syncEntry) {
        Intent intent = new Intent(context, JournalItemActivity.class);
        intent.putExtra(Constants.KEY_COLLECTION_INFO, info);
        intent.putExtra(KEY_SYNC_ENTRY, syncEntry);
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

        setTitle(R.string.journal_item_title);

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

        final TextView title = (TextView) findViewById(R.id.display_name);
        title.setText(info.displayName);

        final TextView desc = (TextView) findViewById(R.id.description);
        desc.setText(info.description);

        final TextView content = (TextView) findViewById(R.id.content);
        content.setText(syncEntry.getContent());

        setJournalEntryView(findViewById(R.id.journal_list_item), info, syncEntry);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.journal_item_activity);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        info = (CollectionInfo) getIntent().getExtras().getSerializable(Constants.KEY_COLLECTION_INFO);
        syncEntry = (SyncEntry) getIntent().getExtras().getSerializable(KEY_SYNC_ENTRY);

        refresh();

        // We refresh before this, so we don't refresh the list before it was fully created.
        if (savedInstanceState == null) {
            /*
            listFragment = CollectionMembersListFragment.newInstance(account, info);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.list_entries_container, listFragment)
                    .commit();
                    */
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
