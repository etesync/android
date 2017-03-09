/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.journalviewer;

import android.accounts.Account;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.ServiceDB;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import lombok.Cleanup;

public class ListJournalsFragment extends ListFragment implements AdapterView.OnItemClickListener {
    private final static String ARG_ACCOUNT = "account";

    private Account account;

    public static ListJournalsFragment newInstance(Account account) {
        ListJournalsFragment frag = new ListJournalsFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(ARG_ACCOUNT, account);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        account = getArguments().getParcelable(ARG_ACCOUNT);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(R.string.change_journal_title);
        return inflater.inflate(R.layout.journal_viewer_list_journals, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CollectionsListAdapter listAdapter = new CollectionsListAdapter(getContext());
        setListAdapter(listAdapter);

        final EntityDataStore<Persistable> data = ((App) getContext().getApplicationContext()).getData();
        @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());

        Long service = dbHelper.getService(account, ServiceDB.Services.SERVICE_CARDDAV);
        listAdapter.add(new HeaderItem(getString(R.string.settings_carddav)));
        for (CollectionInfo info : JournalEntity.getCollections(data, service)) {
            listAdapter.add(new ListItem(info));
        }

        service = dbHelper.getService(account, ServiceDB.Services.SERVICE_CALDAV);
        listAdapter.add(new HeaderItem(getString(R.string.settings_caldav)));
        for (CollectionInfo info : JournalEntity.getCollections(data, service)) {
            listAdapter.add(new ListItem(info));
        }

        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Item item = (Item) getListAdapter().getItem(position);
        if (item.getViewType() == 1) {
            CollectionInfo info = ((ListItem) item).info;
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, ListEntriesFragment.newInstance(info.url))
                    .addToBackStack(null)
                    .commitAllowingStateLoss();
        }
    }

    private static class CollectionsListAdapter extends ArrayAdapter<Item> {
        CollectionsListAdapter(Context context) {
            super(context, 0);
        }

        @NonNull
        @Override
        public View getView(int position, View v, @NonNull ViewGroup parent) {
            Item item = getItem(position);
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (v == null) {
                v = item.getView(inflater, v, parent);
            }

            return v;
        }

        @Override
        public int getViewTypeCount() {
            return Item.Type.values().length;

        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getViewType();
        }
    }

    interface Item {
        enum Type {
            Header(0),
            Item(1);

            private final int value;

            Type(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        int getViewType();

        View getView(LayoutInflater inflater, View convertView, ViewGroup parent);
    }

    private static class HeaderItem implements Item {
        private String header;

        HeaderItem(String header) {
            this.header = header;
        }

        @Override
        public int getViewType() {
            return Type.Header.getValue();
        }

        @Override
        public View getView(LayoutInflater inflater, View v, ViewGroup parent) {
            if (v == null)
                v = inflater.inflate(R.layout.journal_viewer_list_journals_header, parent, false);

            TextView tv = (TextView) v.findViewById(R.id.title);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setText(header);

            return v;
        }
    }


    private static class ListItem implements Item {
        private CollectionInfo info;

        ListItem(CollectionInfo info) {
            this.info = info;
        }

        @Override
        public int getViewType() {
            return Type.Item.getValue();
        }

        @Override
        public View getView(LayoutInflater inflater, View v, ViewGroup parent) {
            if (v == null)
                v = inflater.inflate(R.layout.journal_viewer_list_journals_item, parent, false);

            TextView tv = (TextView) v.findViewById(R.id.title);
            tv.setText(info.displayName);

            return v;
        }
    }
}
