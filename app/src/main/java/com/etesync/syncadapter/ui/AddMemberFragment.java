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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.journalmanager.JournalManager;
import com.etesync.syncadapter.journalmanager.UserInfoManager;
import com.etesync.syncadapter.model.CollectionInfo;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class AddMemberFragment extends DialogFragment {
    final static private String KEY_MEMBER = "memberEmail";
    private Account account;
    private AccountSettings settings;
    private OkHttpClient httpClient;
    private HttpUrl remote;
    private CollectionInfo info;
    private String memberEmail;
    private byte[] memberPubKey;

    public static AddMemberFragment newInstance(Account account, CollectionInfo info, String email) {
        AddMemberFragment frag = new AddMemberFragment();
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
        account = getArguments().getParcelable(Constants.KEY_ACCOUNT);
        info = (CollectionInfo) getArguments().getSerializable(Constants.KEY_COLLECTION_INFO);
        memberEmail = getArguments().getString(KEY_MEMBER);
        try {
            settings = new AccountSettings(getContext(), account);
            httpClient = HttpClient.create(getContext(), settings);
        } catch (InvalidAccountException e) {
            e.printStackTrace();
        }
        remote = HttpUrl.get(settings.getUri());

        new MemberAdd().execute();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getContext());
        progress.setTitle(R.string.collection_members_adding);
        progress.setMessage(getString(R.string.please_wait));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }

    private class MemberAdd extends AsyncTask<Void, Void, MemberAdd.AddResult> {
        @Override
        protected AddResult doInBackground(Void... voids) {
            try {
                UserInfoManager userInfoManager = new UserInfoManager(httpClient, remote);

                UserInfoManager.UserInfo userInfo = userInfoManager.get(memberEmail);
                if (userInfo == null) {
                    throw new Exception(getString(R.string.collection_members_error_user_not_found, memberEmail));
                }
                memberPubKey = userInfo.getPubkey();
                return new AddResult(null);
            } catch (Exception e) {
                return new AddResult(e);
            }
        }

        @Override
        protected void onPostExecute(AddResult result) {
            if (result.throwable == null) {
                String fingerprint = Crypto.AsymmetricCryptoManager.getPrettyKeyFingerprint(memberPubKey);
                View view = LayoutInflater.from(getContext()).inflate(R.layout.fingerprint_alertdialog, null);
                ((TextView) view.findViewById(R.id.body)).setText(getString(R.string.trust_fingerprint_body, memberEmail));
                ((TextView) view.findViewById(R.id.fingerprint)).setText(fingerprint);
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_fingerprint_dark)
                        .setTitle(R.string.trust_fingerprint_title)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new MemberAddSecond().execute();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                        }).show();
            } else {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_add_error)
                        .setMessage(result.throwable.getMessage())
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
                dismiss();
            }
        }

        class AddResult {
            final Throwable throwable;

            AddResult(final Throwable throwable) {
                this.throwable = throwable;
            }
        }
    }

    private class MemberAddSecond extends AsyncTask<Void, Void, MemberAddSecond.AddResultSecond> {
        @Override
        protected AddResultSecond doInBackground(Void... voids) {
            try {
                JournalManager journalsManager = new JournalManager(httpClient, remote);

                JournalManager.Journal journal = JournalManager.Journal.fakeWithUid(info.uid);
                Crypto.CryptoManager crypto = new Crypto.CryptoManager(info.version, settings.password(), info.uid);

                byte[] encryptedKey = crypto.getEncryptedKey(settings.getKeyPair(), memberPubKey);
                JournalManager.Member member = new JournalManager.Member(memberEmail, encryptedKey);
                journalsManager.addMember(journal, member);
                return new AddResultSecond(null);
            } catch (Exception e) {
                return new AddResultSecond(e);
            }
        }

        @Override
        protected void onPostExecute(AddResultSecond result) {
            if (result.throwable == null) {
                ((Refreshable) getActivity()).refresh();
            } else {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_add_error)
                        .setMessage(result.throwable.getMessage())
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
            dismiss();
        }

        class AddResultSecond {
            final Throwable throwable;

            AddResultSecond(final Throwable throwable) {
                this.throwable = throwable;
            }
        }
    }
}
