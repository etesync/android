/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.journalviewer;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.EntryEntity;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;
import com.etesync.syncadapter.model.SyncEntry;
import com.etesync.syncadapter.ui.JournalItemActivity;

import java.util.List;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

public class ListEntriesFragment extends ListFragment implements AdapterView.OnItemClickListener {
    protected static final String EXTRA_COLLECTION_INFO = "collectionInfo";

    private EntityDataStore<Persistable> data;
    private CollectionInfo info;
    private JournalEntity journalEntity;
    private AsyncTask asyncTask;

    private TextView emptyTextView;

    public static ListEntriesFragment newInstance(CollectionInfo info) {
        ListEntriesFragment frag = new ListEntriesFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(EXTRA_COLLECTION_INFO, info);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ((App) getContext().getApplicationContext()).getData();
        info = (CollectionInfo) getArguments().getSerializable(EXTRA_COLLECTION_INFO);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(info.displayName);
        View view = inflater.inflate(R.layout.journal_viewer_list, container, false);

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = (TextView) view.findViewById(android.R.id.empty);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        asyncTask = new JournalFetch().execute();

        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (asyncTask != null)
            asyncTask.cancel(true);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EntryEntity entry = (EntryEntity) getListAdapter().getItem(position);
        startActivity(JournalItemActivity.newIntent(getContext(), info, entry.getContent()));
    }

    class EntriesListAdapter extends ArrayAdapter<EntryEntity> {
        EntriesListAdapter(Context context) {
            super(context, R.layout.journal_viewer_list_item);
        }

        @Override
        @NonNull
        public View getView(int position, View v, @NonNull ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.journal_viewer_list_item, parent, false);

            EntryEntity entryEntity = getItem(position);

            TextView tv = (TextView) v.findViewById(R.id.title);

            // FIXME: hacky way to make it show sensible info
            CollectionInfo info = journalEntity.getInfo();
            setJournalEntryView(v, info, entryEntity.getContent());

            return v;
        }
    }

    private static String getLine(String content, String prefix) {
        if (content == null) {
            return null;
        }

        int start = content.indexOf(prefix);
        if (start >= 0) {
            int end = content.indexOf("\n", start);
            content = content.substring(start + prefix.length(), end);
        } else {
            content = null;
        }
        return content;
    }

    public static void setJournalEntryView(View v, CollectionInfo info, SyncEntry syncEntry) {

        TextView tv = (TextView) v.findViewById(R.id.title);

        // FIXME: hacky way to make it show sensible info
        String fullContent = syncEntry.getContent();
        String prefix;
        if (info.type == CollectionInfo.Type.CALENDAR) {
            prefix = "SUMMARY:";
        } else if (info.type == CollectionInfo.Type.TASK_LIST) {
            prefix = "SUMMARY:";
        } else {
            prefix = "FN:";
        }
        String content = getLine(fullContent, prefix);
        content = (content != null) ? content : "Not found";
        tv.setText(content);

        tv = (TextView) v.findViewById(R.id.description);
        content = getLine(fullContent, "UID:");
        content = "UID: " + ((content != null) ? content : "Not found");
        tv.setText(content);

        ImageView action = (ImageView) v.findViewById(R.id.action);
        switch (syncEntry.getAction()) {
            case ADD:
                action.setImageResource(R.drawable.action_add);
                break;
            case CHANGE:
                action.setImageResource(R.drawable.action_change);
                break;
            case DELETE:
                action.setImageResource(R.drawable.action_delete);
                break;
        }
    }

    private class JournalFetch extends AsyncTask<Void, Void, List<EntryEntity>> {

        @Override
        protected List<EntryEntity> doInBackground(Void... voids) {
            journalEntity = JournalModel.Journal.fetch(data, info.getServiceEntity(data), info.uid);
            return data.select(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).orderBy(EntryEntity.ID.desc()).get().toList();
        }

        @Override
        protected void onPostExecute(List<EntryEntity> result) {
            EntriesListAdapter listAdapter = new EntriesListAdapter(getContext());
            setListAdapter(listAdapter);

            listAdapter.addAll(result);

            emptyTextView.setText(getString(R.string.journal_entries_list_empty));
        }
    }
}
