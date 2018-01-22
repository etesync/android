package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.ui.BaseActivity;

public class ImportActivity extends BaseActivity implements SelectImportMethod, ResultFragment.OnImportCallback, DialogInterface {
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(getString(R.string.import_dialog_title));

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new ImportActivity.SelectImportFragment())
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
                    .replace(android.R.id.content,
                            LocalCalendarImportFragment.newInstance(account, info))
                    .addToBackStack(LocalCalendarImportFragment.class.getName())
                    .commit();
        } else if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content,
                            LocalContactImportFragment.newInstance(account, info))
                    .addToBackStack(LocalContactImportFragment.class.getName())
                    .commit();
        }
        setTitle(getString(R.string.import_select_account));
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
    public void onImportResult(ResultFragment.ImportResult importResult) {
        ResultFragment fragment = ResultFragment.newInstance(importResult);
        fragment.show(getSupportFragmentManager(), "importResult");
    }

    @Override
    public void cancel() {
        finish();
    }

    @Override
    public void dismiss() {
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
            View v = inflater.inflate(R.layout.import_actions_list, container, false);

            View card = v.findViewById(R.id.import_file);
            ImageView img = (ImageView) card.findViewById(R.id.action_icon);
            TextView text = (TextView) card.findViewById(R.id.action_text);
            img.setImageResource(R.drawable.ic_file_white);
            text.setText(R.string.import_button_file);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View aView) {
                    mSelectImportMethod.importFile();
                }
            });

            card = v.findViewById(R.id.import_account);
            img = (ImageView) card.findViewById(R.id.action_icon);
            text = (TextView) card.findViewById(R.id.action_text);
            img.setImageResource(R.drawable.ic_account_circle_white);
            text.setText(R.string.import_button_local);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View aView) {
                    mSelectImportMethod.importAccount();
                }
            });

            return v;
        }
    }
}
