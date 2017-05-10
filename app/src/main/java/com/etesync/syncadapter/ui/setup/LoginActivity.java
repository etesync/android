/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.ui.BaseActivity;
import com.etesync.syncadapter.ui.WebViewActivity;

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
public class LoginActivity extends BaseActivity {
    /**
     * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
     */
    public static final String EXTRA_USERNAME = "username";

    /**
     * When set, the password field will be set to this value.
     */
    public static final String EXTRA_PASSWORD = "password";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
            // first call, add fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new LoginCredentialsFragment())
                    .commit();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_login, menu);
        return true;
    }

    public void showHelp(MenuItem item) {
        WebViewActivity.openUrl(this, Constants.helpUri);
    }
}
