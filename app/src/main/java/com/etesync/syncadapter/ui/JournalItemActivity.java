package com.etesync.syncadapter.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.SyncEntry;

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

        setTitle(info.displayName);

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

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new TabsAdapter(getSupportFragmentManager(), info, syncEntry));

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    private static class TabsAdapter extends FragmentPagerAdapter {
        private CollectionInfo info;
        private SyncEntry syncEntry;
        public TabsAdapter(FragmentManager fm, CollectionInfo info, SyncEntry syncEntry) {
            super(fm);
            this.info = info;
            this.syncEntry = syncEntry;
        }

        @Override
        public int getCount() {
            // FIXME: Make it depend on info type (only have non-raw for known types)
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // FIXME: use string resources
            if (position == 0) {
                return "Main";
            } else {
                return "Raw";
            }
        }

        @Override
        public Fragment getItem(int position) {
            return TextFragment.newInstance(syncEntry);
        }
    }

    public static class TextFragment extends Fragment {
        public static TextFragment newInstance(SyncEntry syncEntry) {
            TextFragment frag = new TextFragment();
            Bundle args = new Bundle(1);
            args.putSerializable(KEY_SYNC_ENTRY, syncEntry);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.text_fragment, container, false);

            TextView tv = (TextView) v.findViewById(R.id.content);

            SyncEntry syncEntry = (SyncEntry) getArguments().getSerializable(KEY_SYNC_ENTRY);
            tv.setText(syncEntry.getContent());

            return v;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
