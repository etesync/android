package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.ui.ImportFragment;

public class ImportActivity extends AppCompatActivity implements SelectImportMethod, OnImportCallback {
    public final static String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    private Account account;
    protected CollectionInfo info;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, ImportActivity.class);
        intent.putExtra(ImportActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(ImportActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new ImportActivity.SelectImportFragment())
                    .commit();
    }

    @Override
    public void importFile() {
        getSupportFragmentManager().beginTransaction()
                .add(ImportFragment.newInstance(account, info), null)
                .commit();

    }

    @Override
    public void importAccount() {
        if (info.type == CollectionInfo.Type.CALENDAR) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container,
                            LocalCalendarImportFragment.newInstance(account, info))
                    .addToBackStack(LocalCalendarImportFragment.class.getName())
                    .commit();
        } else if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container,
                            LocalContactImportFragment.newInstance(account, info))
                    .addToBackStack(LocalContactImportFragment.class.getName())
                    .commit();
        }
    }

    private void popBackStack() {
        if (!getSupportFragmentManager().popBackStackImmediate()) {
            finish();
        } else {
            setTitle(getString(R.string.import_dialog_title));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            popBackStack();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            popBackStack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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

    @Override
    public void onImportSuccess() {
        //todo tom what would you like to do?
        finish();
    }

    @Override
    public void onImportFailed() {
        //todo tom what would you like to do?
        finish();
    }

    public static class SelectImportFragment extends Fragment {

        private SelectImportMethod mSelectImportMethod;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            // This makes sure that the container activity has implemented
            // the callback interface. If not, it throws an exception
            try {
                mSelectImportMethod = (SelectImportMethod) getActivity();
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity().toString()
                        + " must implement MyInterface ");
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            // This makes sure that the container activity has implemented
            // the callback interface. If not, it throws an exception
            try {
                mSelectImportMethod = (SelectImportMethod) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString()
                        + " must implement MyInterface ");
            }
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_import, container, false);
            v.findViewById(R.id.import_button_account).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View aView) {
                    mSelectImportMethod.importAccount();
                }
            });

            v.findViewById(R.id.import_button_file).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View aView) {
                    mSelectImportMethod.importFile();
                }
            });
            return v;
        }
    }
}
