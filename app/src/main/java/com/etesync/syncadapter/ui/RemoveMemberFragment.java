package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.JournalManager;
import com.etesync.syncadapter.model.CollectionInfo;

import org.apache.commons.codec.Charsets;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class RemoveMemberFragment extends DialogFragment {
    final static private String KEY_MEMBER = "memberEmail";
    private AccountSettings settings;
    private OkHttpClient httpClient;
    private HttpUrl remote;
    private CollectionInfo info;
    private String memberEmail;

    public static RemoveMemberFragment newInstance(Account account, CollectionInfo info, String email) {
        RemoveMemberFragment frag = new RemoveMemberFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(Constants.KEY_ACCOUNT, account);
        args.putSerializable(Constants.KEY_COLLECTION_INFO, info);
        args.putString(KEY_MEMBER, email);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Account account = getArguments().getParcelable(Constants.KEY_ACCOUNT);
        info = (CollectionInfo) getArguments().getSerializable(Constants.KEY_COLLECTION_INFO);
        memberEmail = getArguments().getString(KEY_MEMBER);
        try {
            settings = new AccountSettings(getContext(), account);
            httpClient = HttpClient.create(getContext(), settings);
        } catch (InvalidAccountException e) {
            e.printStackTrace();
        }
        remote = HttpUrl.get(settings.getUri());

        new MemberRemove().execute();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getContext());
        progress.setTitle(R.string.collection_members_removing);
        progress.setMessage(getString(R.string.please_wait));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }

    private class MemberRemove extends AsyncTask<Void, Void, MemberRemove.RemoveResult> {
        @Override
        protected RemoveResult doInBackground(Void... voids) {
            try {
                JournalManager journalsManager = new JournalManager(httpClient, remote);
                JournalManager.Journal journal = JournalManager.Journal.fakeWithUid(info.uid);

                JournalManager.Member member = new JournalManager.Member(memberEmail, "placeholder".getBytes(Charsets.UTF_8));
                journalsManager.deleteMember(journal, member);

                return new RemoveResult(null);
            } catch (Exception e) {
                return new RemoveResult(e);
            }
        }

        @Override
        protected void onPostExecute(RemoveResult result) {
            if (result.throwable == null) {
                ((Refreshable) getActivity()).refresh();
            } else {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_remove_error)
                        .setMessage(result.throwable.getMessage())
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
            dismiss();
        }

        class RemoveResult {
            final Throwable throwable;

            RemoveResult(final Throwable throwable) {
                this.throwable = throwable;
            }
        }
    }
}
