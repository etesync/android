package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalContact;

import java.util.ArrayList;
import java.util.List;

import at.bitfire.vcard4android.Contact;

import static android.content.ContentValues.TAG;
import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;
import static com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO;

public class LocalContactImportFragment extends Fragment {

    private Account account;
    private CollectionInfo info;
    private ResultFragment.OnImportCallback importCallback;

    public static LocalContactImportFragment newInstance(Account account, CollectionInfo info) {
        LocalContactImportFragment frag = new LocalContactImportFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(KEY_ACCOUNT, account);
        args.putSerializable(KEY_COLLECTION_INFO, info);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        
        account = getArguments().getParcelable(KEY_ACCOUNT);
        info = (CollectionInfo) getArguments().getSerializable(KEY_COLLECTION_INFO);
    }

    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_contact_import, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        importAccount();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            importCallback = (ResultFragment.OnImportCallback) getActivity();
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
            importCallback = (ResultFragment.OnImportCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement MyInterface ");
        }
    }

    protected void importAccount() {
        ContentProviderClient provider = getContext().getContentResolver().acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI);
        Cursor cursor;
        try {
            cursor = provider.query(ContactsContract.RawContacts.CONTENT_URI,
                    new String[] { ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE }
                    , null, null, ContactsContract.RawContacts.ACCOUNT_NAME + " ASC");
        } catch (Exception except) {
            Log.w(TAG, "Calendar provider is missing columns, continuing anyway");

            except.printStackTrace();
            return;
        }

        final List<LocalAddressBook> localAddressBooks = new ArrayList<>();
        Account account = null;
        int accountNameIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME);
        int accountTypeIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE);
        while (cursor.moveToNext()) {
            String accountName = cursor.getString(accountNameIndex);
            String accountType = cursor.getString(accountTypeIndex);
            if (account == null || (!account.name.equals(accountName) || !account.type.equals(accountType))) {
                account = new Account(accountName, accountType);
                localAddressBooks.add(new LocalAddressBook(account, provider));
            }
        }

        recyclerView.setAdapter(new ImportContactAdapter(localAddressBooks, new OnAccountSelected() {
            @Override
            public void accountSelected(int index) {
                new ImportContacts().execute(localAddressBooks.get(index));
            }
        }));
    }

    protected class ImportContacts extends AsyncTask<LocalAddressBook, Integer, Boolean> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setTitle(R.string.import_dialog_title);
            progressDialog.setMessage(getString(R.string.import_dialog_adding_entries));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(false);
            progressDialog.setIndeterminate(false);
            progressDialog.setIcon(R.drawable.ic_import_export_black);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(LocalAddressBook... addressBooks) {
            return importContacts(addressBooks[0]);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null)
                progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressDialog.dismiss();
            importCallback.onImportResult(new ResultFragment.ImportResult());
        }

        private boolean importContacts(LocalAddressBook localAddressBook) {
            try {
                LocalAddressBook addressBook = new LocalAddressBook(account,
                        getContext().getContentResolver().acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI));
                LocalContact[] localContacts = localAddressBook.getAll();
                int total = localContacts.length;
                progressDialog.setMax(total);
                int progress = 0;
                for (LocalContact currentLocalContact : localContacts) {
                    Contact contact = currentLocalContact.getContact();
                    (new LocalContact(addressBook, contact, contact.uid, null)).createAsDirty();
                    publishProgress(++progress);
                }
                return true;
            } catch (Exception aE) {
                aE.printStackTrace();
                return false;
            }
        }
    }

    public static class ImportContactAdapter extends RecyclerView.Adapter<ImportContactAdapter.ViewHolder> {
        private static final String TAG = "ImportContactAdapter";

        private List<LocalAddressBook> mAddressBooks;
        private OnAccountSelected mOnAccountSelected;

        /**
         * Provide a reference to the type of views that you are using (custom ViewHolder)
         */
        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textView;

            public ViewHolder(View v, final OnAccountSelected onAccountSelected) {
                super(v);
                // Define click listener for the ViewHolder's View.
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onAccountSelected.accountSelected(getAdapterPosition());
                    }
                });
                textView = (TextView) v.findViewById(R.id.listItemText);
            }

            public TextView getTextView() {
                return textView;
            }
        }

        /**
         * Initialize the dataset of the Adapter.
         *
         * @param addressBooks containing the data to populate views to be used by RecyclerView.
         */
        public ImportContactAdapter(List<LocalAddressBook> addressBooks, OnAccountSelected onAccountSelected) {
            mAddressBooks = addressBooks;
            mOnAccountSelected = onAccountSelected;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            // Create a new view.
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.list_item, viewGroup, false);

            return new ViewHolder(v, mOnAccountSelected);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            viewHolder.getTextView().setText(mAddressBooks.get(position).account.name);
        }

        @Override
        public int getItemCount() {
            return mAddressBooks.size();
        }
    }

    private interface OnAccountSelected {
        void accountSelected(int index);
    }
}
