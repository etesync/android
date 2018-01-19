package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.JournalManager;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.JournalModel;

import java.util.List;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class CollectionMembersListFragment extends ListFragment implements AdapterView.OnItemClickListener, Refreshable {
    private EntityDataStore<Persistable> data;
    private Account account;
    private CollectionInfo info;
    private JournalEntity journalEntity;
    private AsyncTask asyncTask;

    private TextView emptyTextView;

    public static CollectionMembersListFragment newInstance(Account account, CollectionInfo info) {
        CollectionMembersListFragment frag = new CollectionMembersListFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(Constants.KEY_ACCOUNT, account);
        args.putSerializable(Constants.KEY_COLLECTION_INFO, info);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ((App) getContext().getApplicationContext()).getData();
        account = getArguments().getParcelable(Constants.KEY_ACCOUNT);
        info = (CollectionInfo) getArguments().getSerializable(Constants.KEY_COLLECTION_INFO);
        journalEntity = JournalModel.Journal.fetch(data, info.getServiceEntity(data), info.uid);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.collection_members_list, container, false);

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = (TextView) view.findViewById(android.R.id.empty);
        return view;
    }

    @Override
    public void refresh() {
        asyncTask = new JournalMembersFetch().execute();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        refresh();

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
        final JournalManager.Member member = (JournalManager.Member) getListAdapter().getItem(position);

        new AlertDialog.Builder(getActivity())
                .setIcon(R.drawable.ic_info_dark)
                .setTitle(R.string.collection_members_remove_title)
                .setMessage(getString(R.string.collection_members_remove, member.getUser()))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DialogFragment frag = RemoveMemberFragment.newInstance(account, info, member.getUser());
                        frag.show(getFragmentManager(), null);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }

    class MembersListAdapter extends ArrayAdapter<JournalManager.Member> {
        MembersListAdapter(Context context) {
            super(context, R.layout.collection_members_list_item);
        }

        @Override
        @NonNull
        public View getView(int position, View v, @NonNull ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.collection_members_list_item, parent, false);

            JournalManager.Member member = getItem(position);

            TextView tv = (TextView) v.findViewById(R.id.title);
            tv.setText(member.getUser());
            return v;
        }
    }

    private class JournalMembersFetch extends AsyncTask<Void, Void, JournalMembersFetch.MembersResult> {
        @Override
        protected MembersResult doInBackground(Void... voids) {
            try {
                AccountSettings settings = new AccountSettings(getContext(), account);
                OkHttpClient httpClient = HttpClient.create(getContext(), settings);
                JournalManager journalsManager = new JournalManager(httpClient, HttpUrl.get(settings.getUri()));

                JournalManager.Journal journal = JournalManager.Journal.fakeWithUid(journalEntity.getUid());
                return new MembersResult(journalsManager.listMembers(journal), null);
            } catch (Exception e) {
                return new MembersResult(null, e);
            }
        }

        @Override
        protected void onPostExecute(MembersResult result) {
            if (result.throwable == null) {
                MembersListAdapter listAdapter = new MembersListAdapter(getContext());
                setListAdapter(listAdapter);

                listAdapter.addAll(result.members);

                emptyTextView.setText(R.string.collection_members_list_empty);
            } else {
                emptyTextView.setText(result.throwable.getLocalizedMessage());
            }
        }

        class MembersResult {
            final List<JournalManager.Member> members;
            final Throwable throwable;

            MembersResult(final List<JournalManager.Member> members, final Throwable throwable) {
                this.members = members;
                this.throwable = throwable;
            }
        }
    }
}
