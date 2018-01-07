package com.etesync.syncadapter.ui.setup;

import android.accounts.Account;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.widget.TextView;

import com.etesync.syncadapter.AccountSettings;
import com.etesync.syncadapter.App;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.InvalidAccountException;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.journalmanager.Constants;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.journalmanager.UserInfoManager;

import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;

public class SetupUserInfoFragment extends DialogFragment {
    private Account account;
    private AccountSettings settings;

    public static SetupUserInfoFragment newInstance(Account account) {
        SetupUserInfoFragment frag = new SetupUserInfoFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(KEY_ACCOUNT, account);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setTitle(R.string.login_encryption_setup_title);
        progress.setMessage(getString(R.string.login_encryption_setup));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getArguments().getParcelable(KEY_ACCOUNT);

        try {
            settings = new AccountSettings(getContext(), account);
        } catch (Exception e) {
            e.printStackTrace();
        }

        new SetupUserInfo().execute(account);
    }

    public static boolean hasUserInfo(Context context, Account account) {
        AccountSettings settings;
        try {
            settings = new AccountSettings(context, account);
        } catch (InvalidAccountException e) {
            e.printStackTrace();
            return false;
        }
        return settings.getKeyPair() != null;
    }

    protected class SetupUserInfo extends AsyncTask<Account, Integer, SetupUserInfo.SetupUserInfoResult> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = (ProgressDialog) getDialog();
        }

        @Override
        protected SetupUserInfo.SetupUserInfoResult doInBackground(Account... accounts) {
            try {
                Crypto.CryptoManager cryptoManager;
                OkHttpClient httpClient = HttpClient.create(getContext(), settings);

                UserInfoManager userInfoManager = new UserInfoManager(httpClient, HttpUrl.get(settings.getUri()));
                UserInfoManager.UserInfo userInfo = userInfoManager.get(account.name);

                if (userInfo == null) {
                    App.log.info("Creating userInfo for " + account.name);
                    cryptoManager = new Crypto.CryptoManager(Constants.CURRENT_VERSION, settings.password(), "userInfo");
                    userInfo = UserInfoManager.UserInfo.generate(cryptoManager, account.name);
                    userInfoManager.create(userInfo);
                } else {
                    App.log.info("Fetched userInfo for " + account.name);
                    cryptoManager = new Crypto.CryptoManager(userInfo.getVersion(), settings.password(), "userInfo");
                    userInfo.verify(cryptoManager);
                }

                Crypto.AsymmetricKeyPair keyPair = new Crypto.AsymmetricKeyPair(userInfo.getContent(cryptoManager), userInfo.getPubkey());

                return new SetupUserInfoResult(keyPair, null);
            } catch (Exception e) {
                e.printStackTrace();
                return new SetupUserInfoResult(null, e);
            }
        }

        @Override
        protected void onPostExecute(SetupUserInfoResult result) {
            if (result.exception == null) {
                settings.setKeyPair(result.keyPair);
            } else {
                Dialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.login_user_info_error_title)
                        .setIcon(R.drawable.ic_error_dark)
                        .setMessage(result.exception.getLocalizedMessage())
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // dismiss
                            }
                        })
                        .create();
                dialog.show();
            }

            dismissAllowingStateLoss();
        }

        @RequiredArgsConstructor
        class SetupUserInfoResult {
            final Crypto.AsymmetricKeyPair keyPair;
            final Exception exception;
        }
    }
}
