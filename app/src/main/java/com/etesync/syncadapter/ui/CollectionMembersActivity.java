package com.etesync.syncadapter.ui;

import android.accounts.Account;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.resource.LocalCalendar;

import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

public class CollectionMembersActivity extends BaseActivity implements Refreshable {
    public final static String EXTRA_ACCOUNT = "account",
            EXTRA_COLLECTION_INFO = "collectionInfo";

    private Account account;
    private JournalEntity journalEntity;
    private CollectionMembersListFragment listFragment;
    protected CollectionInfo info;

    public static Intent newIntent(Context context, Account account, CollectionInfo info) {
        Intent intent = new Intent(context, CollectionMembersActivity.class);
        intent.putExtra(CollectionMembersActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(CollectionMembersActivity.EXTRA_COLLECTION_INFO, info);
        return intent;
    }

    @Override
    public void refresh() {
        EntityDataStore<Persistable> data = ((App) getApplicationContext()).getData();

        journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid);
        if ((journalEntity == null) || journalEntity.isDeleted()) {
            finish();
            return;
        }

        info = journalEntity.getInfo();

        setTitle(R.string.collection_members_title);

        final View colorSquare = findViewById(R.id.color);
        if (info.type == CollectionInfo.Type.CALENDAR) {
            if (info.color != null) {
                colorSquare.setBackgroundColor(info.color);
            } else {
                colorSquare.setBackgroundColor(LocalCalendar.defaultColor);
            }
        } else {
            colorSquare.setVisibility(View.GONE);
        }
        findViewById(R.id.progressBar).setVisibility(View.GONE);

        final TextView title = (TextView) findViewById(R.id.display_name);
        title.setText(info.displayName);

        final TextView desc = (TextView) findViewById(R.id.description);
        desc.setText(info.description);

        if (listFragment != null) {
            listFragment.refresh();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_collection_members);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        account = getIntent().getExtras().getParcelable(EXTRA_ACCOUNT);
        info = (CollectionInfo) getIntent().getExtras().getSerializable(EXTRA_COLLECTION_INFO);

        refresh();

        // We refresh before this, so we don't refresh the list before it was fully created.
        if (savedInstanceState == null) {
            listFragment = CollectionMembersListFragment.newInstance(account, info);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.list_entries_container, listFragment)
                    .commit();
        }
    }

    public void onAddMemberClicked(View v) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.collection_members_add)
                .setIcon(R.drawable.ic_account_add_dark)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DialogFragment frag = AddMemberFragment.newInstance(account, info, input.getText().toString());
                        frag.show(getSupportFragmentManager(), null);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        dialog.setView(input);
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
