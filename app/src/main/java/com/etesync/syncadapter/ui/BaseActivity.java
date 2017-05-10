package com.etesync.syncadapter.ui;

import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.etesync.syncadapter.App;

public class BaseActivity extends AppCompatActivity {
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!getSupportFragmentManager().popBackStackImmediate()) {
                finish();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        App app = (App) getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        App app = (App) getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = false;
    }
}