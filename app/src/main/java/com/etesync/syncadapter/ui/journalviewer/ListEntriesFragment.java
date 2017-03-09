/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.journalviewer;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.EntryEntity;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;

import java.util.Locale;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

public class ListEntriesFragment extends ListFragment implements AdapterView.OnItemClickListener {
    protected static final String EXTRA_JOURNAL = "journal";

    private EntityDataStore<Persistable> data;
    private JournalEntity journalEntity;

    public static ListEntriesFragment newInstance(String journal) {
        ListEntriesFragment frag = new ListEntriesFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(EXTRA_JOURNAL, journal);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ((App) getContext().getApplicationContext()).getData();
        String name = getArguments().getString(EXTRA_JOURNAL);
        journalEntity = JournalModel.Journal.fetch(data, name);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(String.format(Locale.getDefault(), "%s (%d)", journalEntity.getInfo().displayName, data.count(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value()));
        return inflater.inflate(R.layout.journal_viewer_list_entries, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EntriesListAdapter listAdapter = new EntriesListAdapter(getContext());
        setListAdapter(listAdapter);

        listAdapter.addAll(data.select(EntryEntity.class).where(EntryEntity.JOURNAL.eq(journalEntity)).orderBy(EntryEntity.ID.desc()).get().toList());

        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EntryEntity entry = (EntryEntity) getListAdapter().getItem(position);
        new AlertDialog.Builder(getActivity())
                .setTitle("Raw dump: " + entry.getUid())
                .setMessage("Action: " + entry.getContent().getAction().toString() + "\nUid: " + entry.getUid() + "\n" + entry.getContent().getContent()).show();
    }

    static class EntriesListAdapter extends ArrayAdapter<EntryEntity> {
        public EntriesListAdapter(Context context) {
            super(context, R.layout.journal_viewer_list_journals_item);
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.journal_viewer_list_journals_item, parent, false);

            EntryEntity entryEntity = getItem(position);

            TextView tv = (TextView) v.findViewById(R.id.title);
            tv.setText(String.format(Locale.getDefault(), "%s: %s", entryEntity.getContent().getAction().toString(), entryEntity.getUid()));

            return v;
        }
    }
}
