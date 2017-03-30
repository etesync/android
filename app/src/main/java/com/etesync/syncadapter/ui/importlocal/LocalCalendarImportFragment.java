package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.CalendarAccount;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalEvent;

import java.util.List;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;
import static com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO;

public class LocalCalendarImportFragment extends ListFragment {

    private Account account;
    private CollectionInfo info;
    private OnImportCallback importCallback;

    public static LocalCalendarImportFragment newInstance(Account account, CollectionInfo info) {
        LocalCalendarImportFragment frag = new LocalCalendarImportFragment();
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
        return inflater.inflate(R.layout.fragment_local_import, container, false);
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
            importCallback = (OnImportCallback) getActivity();
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
            importCallback = (OnImportCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement MyInterface ");
        }
    }

    protected void importAccount() {
        final List<CalendarAccount> calendarAccountList = CalendarAccount.loadAll(getContext().getContentResolver());

        ExpandableListView listCalendar = (ExpandableListView) getListView();

        final LocalCalendarImportFragment.ExpandableListAdapter adapter =
                new LocalCalendarImportFragment.ExpandableListAdapter(getContext(), calendarAccountList);
        listCalendar.setAdapter(adapter);

        listCalendar.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView aExpandableListView, View aView, int groupPosition, int childPosition, long aL) {
                new ImportEvents().execute(calendarAccountList.get(groupPosition).calendars.get(childPosition));
                return false;
            }
        });
    }


    private class ExpandableListAdapter extends BaseExpandableListAdapter {

        private Context context;
        private List<CalendarAccount> calendarAccounts;

        public ExpandableListAdapter(Context context, List<CalendarAccount> calendarAccounts) {
            this.context = context;
            this.calendarAccounts = calendarAccounts;
        }

        @Override
        public Object getChild(int groupPosition, int childPosititon) {
            return calendarAccounts.get(groupPosition).calendars
                    .get(childPosititon).getDisplayName();
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {

            final String childText = (String) getChild(groupPosition, childPosition);

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_item, null);
            }
            //Todo add viewholder after we decide about the UI

            TextView txtListChild = (TextView) convertView
                    .findViewById(R.id.listItemText);

            txtListChild.setText(childText);
            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return calendarAccounts.get(groupPosition).calendars
                    .size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return calendarAccounts.get(groupPosition).toString();
        }

        @Override
        public int getGroupCount() {
            return calendarAccounts.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            String headerTitle = (String) getGroup(groupPosition);
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_group, null);
            }
            //Todo add viewholder after we decide about the UI

            TextView lblListHeader = (TextView) convertView
                    .findViewById(R.id.lblListHeader);
            lblListHeader.setTypeface(null, Typeface.BOLD);
            lblListHeader.setText(headerTitle);

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    protected class ImportEvents extends AsyncTask<LocalCalendar, Integer, Boolean> {
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
        protected Boolean doInBackground(LocalCalendar... calendars) {
            return importEvents(calendars[0]);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null)
                progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressDialog.dismiss();
            if (result) importCallback.onImportSuccess();
            else importCallback.onImportFailed();
        }

        private boolean importEvents(LocalCalendar fromCalendar) {
            try {
                LocalCalendar localCalendar = LocalCalendar.findByName(account,
                        getContext().getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI),
                        LocalCalendar.Factory.INSTANCE, info.url);
                LocalEvent[] localEvents = fromCalendar.getAll();
                progressDialog.setMax(localEvents.length);
                int progress = 0;
                for (LocalEvent currentLocalEvent : localEvents) {
                    Event event = currentLocalEvent.getEvent();
                    try {
                        LocalEvent localEvent = new LocalEvent(localCalendar, event, event.uid, null);
                        localEvent.addAsDirty();
                    } catch (CalendarStorageException e) {
                        e.printStackTrace();
                    }
                    publishProgress(++progress);
                }
                return true;
            } catch (Exception aE) {
                aE.printStackTrace();
                return false;
            }
        }
    }
}
