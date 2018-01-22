package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalContact;

import java.util.ArrayList;
import java.util.List;

import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;

import static android.content.ContentValues.TAG;
import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;
import static com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO;

public class LocalContactImportFragment extends Fragment {

    private Account account;
    private CollectionInfo info;
    private RecyclerView recyclerView;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_contact_import, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity()));
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        importAccount();
    }

    protected void importAccount() {
        ContentProviderClient provider = getContext().getContentResolver().acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI);
        Cursor cursor;
        try {
            cursor = provider.query(ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE}
                    , null, null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " ASC, " + ContactsContract.RawContacts.ACCOUNT_TYPE);
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
                localAddressBooks.add(new LocalAddressBook(getContext(), account, provider));
            }
        }

        cursor.close();
        provider.release();

        recyclerView.setAdapter(new ImportContactAdapter(getContext(), localAddressBooks, new OnAccountSelected() {
            @Override
            public void accountSelected(int index) {
                new ImportContacts().execute(localAddressBooks.get(index));
            }
        }));
    }

    protected class ImportContacts extends AsyncTask<LocalAddressBook, Integer, ResultFragment.ImportResult> {
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
        protected ResultFragment.ImportResult doInBackground(LocalAddressBook... addressBooks) {
            return importContacts(addressBooks[0]);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null)
                progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(ResultFragment.ImportResult result) {
            progressDialog.dismiss();
            ((ResultFragment.OnImportCallback) getActivity()).onImportResult(result);
        }

        private ResultFragment.ImportResult importContacts(LocalAddressBook localAddressBook) {
            ResultFragment.ImportResult result = new ResultFragment.ImportResult();
            try {
                LocalAddressBook addressBook = LocalAddressBook.findByUid(getContext(),
                        getContext().getContentResolver().acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI),
                        account, info.uid);
                LocalContact[] localContacts = localAddressBook.getAll();
                int total = localContacts.length;
                progressDialog.setMax(total);
                result.total = total;
                int progress = 0;
                for (LocalContact currentLocalContact : localContacts) {
                    Contact contact = currentLocalContact.getContact();
                    (new LocalContact(addressBook, contact, null, null)).createAsDirty();

                    try {
                        LocalContact localContact = new LocalContact(addressBook, contact, null, null);
                        localContact.createAsDirty();
                        result.added++;
                    } catch (ContactsStorageException e) {
                        e.printStackTrace();
                        result.e = e;
                    }
                    publishProgress(++progress);
                }
            } catch (Exception e) {
                result.e = e;
            }
            return result;
        }
    }

    public static class ImportContactAdapter extends RecyclerView.Adapter<ImportContactAdapter.ViewHolder> {
        private static final String TAG = "ImportContactAdapter";

        private List<LocalAddressBook> mAddressBooks;
        private OnAccountSelected mOnAccountSelected;
        private AccountResolver accountResolver;

        /**
         * Provide a reference to the type of views that you are using (custom ViewHolder)
         */
        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView titleTextView;
            private final TextView descTextView;
            private final ImageView iconImageView;

            public ViewHolder(View v, final OnAccountSelected onAccountSelected) {
                super(v);
                // Define click listener for the ViewHolder's View.
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onAccountSelected.accountSelected(getAdapterPosition());
                    }
                });
                titleTextView = (TextView) v.findViewById(R.id.title);
                descTextView = (TextView) v.findViewById(R.id.description);
                iconImageView = (ImageView) v.findViewById(R.id.icon);
            }
        }

        /**
         * Initialize the dataset of the Adapter.
         *
         * @param addressBooks containing the data to populate views to be used by RecyclerView.
         */
        public ImportContactAdapter(Context context, List<LocalAddressBook> addressBooks, OnAccountSelected onAccountSelected) {
            mAddressBooks = addressBooks;
            mOnAccountSelected = onAccountSelected;
            accountResolver = new AccountResolver(context);
        }

        // Create new views (invoked by the layout manager)
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            // Create a new view.
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.import_content_list_account, viewGroup, false);

            return new ViewHolder(v, mOnAccountSelected);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            viewHolder.titleTextView.setText(mAddressBooks.get(position).account.name);
            AccountResolver.AccountInfo accountInfo = accountResolver.resolve(mAddressBooks.get(position).account.type);
            viewHolder.descTextView.setText(accountInfo.name);
            viewHolder.iconImageView.setImageDrawable(accountInfo.icon);
        }

        @Override
        public int getItemCount() {
            return mAddressBooks.size();
        }
    }

    private interface OnAccountSelected {
        void accountSelected(int index);
    }

    public static class DividerItemDecoration extends RecyclerView.ItemDecoration {

        private static final int[] ATTRS = new int[]{
                android.R.attr.listDivider
        };

        private Drawable mDivider;

        public DividerItemDecoration(Context context) {
            final TypedArray a = context.obtainStyledAttributes(ATTRS);
            mDivider = a.getDrawable(0);
            a.recycle();
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            drawVertical(c, parent);
        }

        public void drawVertical(Canvas c, RecyclerView parent) {
            final int left = parent.getPaddingLeft();
            final int right = parent.getWidth() - parent.getPaddingRight();

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child
                        .getLayoutParams();
                final int top = child.getBottom() + params.bottomMargin;
                final int bottom = top + mDivider.getIntrinsicHeight();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
        }
    }
}
