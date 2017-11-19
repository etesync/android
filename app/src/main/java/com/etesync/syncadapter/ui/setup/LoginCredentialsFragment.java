/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.ui.WebViewActivity;
import com.etesync.syncadapter.ui.widget.EditPassword;

import net.cachapa.expandablelayout.ExpandableLayout;

import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.HttpUrl;

public class LoginCredentialsFragment extends Fragment {
    EditText editUserName;
    EditPassword editUrlPassword;

    CheckedTextView showAdvanced;
    EditText customServer;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.login_credentials_fragment, container, false);

        editUserName = (EditText) v.findViewById(R.id.user_name);
        editUrlPassword = (EditPassword) v.findViewById(R.id.url_password);
        showAdvanced = (CheckedTextView) v.findViewById(R.id.show_advanced);
        customServer = (EditText) v.findViewById(R.id.custom_server);

        if (savedInstanceState == null) {
            Activity activity = getActivity();
            Intent intent = (activity != null) ? activity.getIntent() : null;
            if (intent != null) {
                // we've got initial login data
                String username = intent.getStringExtra(LoginActivity.EXTRA_USERNAME),
                        password = intent.getStringExtra(LoginActivity.EXTRA_PASSWORD);

                editUserName.setText(username);
                editUrlPassword.setText(password);
            }
        }

        final Button createAccount = (Button) v.findViewById(R.id.create_account);
        createAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri createUri = Constants.registrationUrl.buildUpon().appendQueryParameter("email", editUserName.getText().toString()).build();
                WebViewActivity.openUrl(getContext(), createUri);
            }
        });

        final Button login = (Button) v.findViewById(R.id.login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginCredentials credentials = validateLoginData();
                if (credentials != null)
                    DetectConfigurationFragment.newInstance(credentials).show(getFragmentManager(), null);
            }
        });

        final TextView forgotPassword = (TextView) v.findViewById(R.id.forgot_password);
        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebViewActivity.openUrl(getContext(), Constants.forgotPassword);
            }
        });

        final ExpandableLayout advancedLayout = (ExpandableLayout) v.findViewById(R.id.advanced_layout);

        showAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (showAdvanced.isChecked()) {
                    showAdvanced.setChecked(false);
                    advancedLayout.collapse();
                } else {
                    showAdvanced.setChecked(true);
                    advancedLayout.expand();
                }
            }
        });

        return v;
    }

    protected LoginCredentials validateLoginData() {
        boolean valid = true;

        String userName = editUserName.getText().toString();
        if (userName.isEmpty()) {
            editUserName.setError(getString(R.string.login_email_address_error));
            valid = false;
        }

        String password = editUrlPassword.getText().toString();
        if (password.isEmpty()) {
            editUrlPassword.setError(getString(R.string.login_password_required));
            valid = false;
        }

        URI uri = null;
        if (showAdvanced.isChecked()) {
            String server = customServer.getText().toString();
            // If this field is null, just use the default
            if (!server.isEmpty()) {
                HttpUrl url = HttpUrl.parse(server);
                if (url != null) {
                    uri = url.uri();
                } else {
                    customServer.setError(getString(R.string.login_custom_server_error));
                    valid = false;
                }
            }
        }

        return valid ? new LoginCredentials(uri, userName, password) : null;
    }
}
