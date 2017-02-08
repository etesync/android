/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.widget.EditPassword;

public class EncryptionDetailsFragment extends Fragment {

    private static final String KEY_CONFIG = "config";
    EditPassword editPassword = null;


    public static EncryptionDetailsFragment newInstance(BaseConfigurationFinder.Configuration config) {
        EncryptionDetailsFragment frag = new EncryptionDetailsFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(KEY_CONFIG, config);
        frag.setArguments(args);
        return frag;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.login_encryption_details, container, false);

        Button btnBack = (Button)v.findViewById(R.id.back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().popBackStack();
            }
        });

        final BaseConfigurationFinder.Configuration config = (BaseConfigurationFinder.Configuration)getArguments().getSerializable(KEY_CONFIG);

        TextView accountName = (TextView)v.findViewById(R.id.account_name);
        accountName.setText(getString(R.string.login_encryption_account_label) + " " + config.userName);

        editPassword = (EditPassword) v.findViewById(R.id.encryption_password);

        Button btnCreate = (Button)v.findViewById(R.id.create_account);
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateEncryptionData(config) == null) {
                    return;
                }

                SetupEncryptionFragment.newInstance(config).show(getFragmentManager(), null);
            }
        });

        return v;
    }

    private BaseConfigurationFinder.Configuration validateEncryptionData(BaseConfigurationFinder.Configuration config) {
        boolean valid = true;
        String password = editPassword.getText().toString();
        if (password.isEmpty()) {
            editPassword.setError(getString(R.string.login_password_required));
            valid = false;
        }

        config.rawPassword = password;

        return valid ? config : null;
    }
}
