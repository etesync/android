package com.etesync.syncadapter.ui.importlocal;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
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
        return inflater.inflate(R.layout.fragment_local_calendar_import, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        importAccount();
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
                new ImportEvents().execute(calendarAccountList.get(groupPosition).getCalendars().get(childPosition));
                return false;
            }
        });
    }


    private class ExpandableListAdapter extends BaseExpandableListAdapter {

        private Context context;
        private List<CalendarAccount> calendarAccounts;
        private AccountResolver accountResolver;

        public ExpandableListAdapter(Context context, List<CalendarAccount> calendarAccounts) {
            this.context = context;
            this.calendarAccounts = calendarAccounts;
            this.accountResolver = new AccountResolver(context);
        }

        private class ChildViewHolder {
            TextView textView;
        }

        private class GroupViewHolder {
            TextView titleTextView;
            TextView descriptionTextView;
            ImageView iconImageView;
        }

        @Override
        public Object getChild(int groupPosition, int childPosititon) {
            return calendarAccounts.get(groupPosition).getCalendars()
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
            ChildViewHolder viewHolder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.import_calendars_list_item, null);
            }

            if (convertView.getTag() != null) {
                viewHolder =  (ChildViewHolder) convertView.getTag();
            } else {
                viewHolder = new ChildViewHolder();
                viewHolder.textView = (TextView) convertView
                        .findViewById(R.id.listItemText);
                convertView.setTag(viewHolder);
            }
            viewHolder.textView.setText(childText);
            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return calendarAccounts.get(groupPosition).getCalendars()
                    .size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return calendarAccounts.get(groupPosition);
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
            CalendarAccount calendarAccount = (CalendarAccount) getGroup(groupPosition);
            GroupViewHolder viewHolder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.import_content_list_header, null);
            }
            if (convertView.getTag() != null) {
                viewHolder =  (GroupViewHolder) convertView.getTag();
            } else {
                viewHolder = new GroupViewHolder();
                viewHolder.titleTextView = (TextView) convertView
                        .findViewById(R.id.title);
                viewHolder.descriptionTextView = (TextView) convertView
                        .findViewById(R.id.description);
                viewHolder.iconImageView = (ImageView) convertView.findViewById(R.id.icon);
                convertView.setTag(viewHolder);
            }

            viewHolder.titleTextView.setText(calendarAccount.getAccountName());
            AccountResolver.AccountInfo accountInfo = accountResolver.resolve(calendarAccount.getAccountType());
            viewHolder.descriptionTextView.setText(accountInfo.name);
            viewHolder.iconImageView.setImageDrawable(accountInfo.icon);

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

    protected class ImportEvents extends AsyncTask<LocalCalendar, Integer, ResultFragment.ImportResult> {
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
        protected ResultFragment.ImportResult doInBackground(LocalCalendar... calendars) {
            return importEvents(calendars[0]);
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

        private ResultFragment.ImportResult importEvents(LocalCalendar fromCalendar) {
            ResultFragment.ImportResult result = new ResultFragment.ImportResult();
            try {
                LocalCalendar localCalendar = LocalCalendar.findByName(account,
                        getContext().getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI),
                        LocalCalendar.Factory.INSTANCE, info.uid);
                LocalEvent[] localEvents = fromCalendar.getAll();
                int total = localEvents.length;
                progressDialog.setMax(total);
                result.total = total;
                int progress = 0;
                for (LocalEvent currentLocalEvent : localEvents) {
                    Event event = currentLocalEvent.getEvent();
                    try {
                        LocalEvent localEvent = new LocalEvent(localCalendar, event, null, null);
                        localEvent.addAsDirty();
                        result.added++;
                    } catch (CalendarStorageException e) {
                        e.printStackTrace();

                    }
                    publishProgress(++progress);
                }
            } catch (Exception e) {
                e.printStackTrace();
                result.e = e;
            }
            return result;
        }
    }
}
