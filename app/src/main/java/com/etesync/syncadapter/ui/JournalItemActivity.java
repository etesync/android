package com.etesync.syncadapter.ui;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.model.JournalEntity;
import com.etesync.syncadapter.model.SyncEntry;

import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.property.Attendee;

import org.apache.commons.codec.Charsets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.LabeledProperty;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.RelatedType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.Impp;
import ezvcard.property.Related;
import ezvcard.property.Telephone;
import ezvcard.property.Url;
import io.requery.Persistable;
import io.requery.sql.EntityDataStore;

import static com.etesync.syncadapter.ui.journalviewer.ListEntriesFragment.setJournalEntryView;

public class JournalItemActivity extends BaseActivity implements Refreshable {
    private static final String KEY_SYNC_ENTRY = "syncEntry";
    private JournalEntity journalEntity;
    protected CollectionInfo info;
    private SyncEntry syncEntry;

    public static Intent newIntent(Context context, CollectionInfo info, SyncEntry syncEntry) {
        Intent intent = new Intent(context, JournalItemActivity.class);
        intent.putExtra(Constants.KEY_COLLECTION_INFO, info);
        intent.putExtra(KEY_SYNC_ENTRY, syncEntry);
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

        setTitle(info.displayName);

        setJournalEntryView(findViewById(R.id.journal_list_item), info, syncEntry);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.journal_item_activity);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        info = (CollectionInfo) getIntent().getExtras().getSerializable(Constants.KEY_COLLECTION_INFO);
        syncEntry = (SyncEntry) getIntent().getExtras().getSerializable(KEY_SYNC_ENTRY);

        refresh();

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new TabsAdapter(getSupportFragmentManager(), info, syncEntry));

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    private static class TabsAdapter extends FragmentPagerAdapter {
        private CollectionInfo info;
        private SyncEntry syncEntry;
        public TabsAdapter(FragmentManager fm, CollectionInfo info, SyncEntry syncEntry) {
            super(fm);
            this.info = info;
            this.syncEntry = syncEntry;
        }

        @Override
        public int getCount() {
            // FIXME: Make it depend on info type (only have non-raw for known types)
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // FIXME: use string resources
            if (position == 0) {
                return "Main";
            } else {
                return "Raw";
            }
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return PrettyFragment.newInstance(info, syncEntry);
            } else {
                return TextFragment.newInstance(syncEntry);
            }
        }
    }

    public static class TextFragment extends Fragment {
        public static TextFragment newInstance(SyncEntry syncEntry) {
            TextFragment frag = new TextFragment();
            Bundle args = new Bundle(1);
            args.putSerializable(KEY_SYNC_ENTRY, syncEntry);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.text_fragment, container, false);

            TextView tv = (TextView) v.findViewById(R.id.content);

            SyncEntry syncEntry = (SyncEntry) getArguments().getSerializable(KEY_SYNC_ENTRY);
            tv.setText(syncEntry.getContent());

            return v;
        }
    }

    public static class PrettyFragment extends Fragment {
        CollectionInfo info;
        SyncEntry syncEntry;

        public static PrettyFragment newInstance(CollectionInfo info, SyncEntry syncEntry) {
            PrettyFragment frag = new PrettyFragment();
            Bundle args = new Bundle(1);
            args.putSerializable(Constants.KEY_COLLECTION_INFO, info);
            args.putSerializable(KEY_SYNC_ENTRY, syncEntry);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = null;

            info = (CollectionInfo) getArguments().getSerializable(Constants.KEY_COLLECTION_INFO);
            syncEntry = (SyncEntry) getArguments().getSerializable(KEY_SYNC_ENTRY);

            switch (info.type) {
                case ADDRESS_BOOK:
                    v = inflater.inflate(R.layout.contact_info, container, false);
                    new LoadContactTask(v).execute();
                    break;
                case CALENDAR:
                    v = inflater.inflate(R.layout.event_info, container, false);
                    new LoadEventTask(v).execute();
                    break;
            }

            return v;
        }

        private class LoadEventTask extends AsyncTask<Void, Void, Event> {
            View view;
            LoadEventTask(View v) {
                super();
                view = v;
            }
            @Override
            protected Event doInBackground(Void... aVoids) {
                InputStream is = new ByteArrayInputStream(syncEntry.getContent().getBytes(Charsets.UTF_8));

                try {
                    return Event.fromStream(is, Charsets.UTF_8, null)[0];
                } catch (InvalidCalendarException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Event event) {
                final View loader = view.findViewById(R.id.event_info_loading_msg);
                loader.setVisibility(View.GONE);
                final View contentContainer = view.findViewById(R.id.event_info_scroll_view);
                contentContainer.setVisibility(View.VISIBLE);

                setTextViewText(view, R.id.title, event.summary);

                setTextViewText(view, R.id.when_datetime, getDisplayedDatetime(event.dtStart.getDate().getTime(), event.dtEnd.getDate().getTime(), event.isAllDay(), getContext()));

                setTextViewText(view, R.id.where, event.location);

                if (event.organizer != null) {
                    TextView tv = (TextView) view.findViewById(R.id.organizer);
                    tv.setText(event.organizer.getCalAddress().toString().replaceFirst("mailto:", ""));
                } else {
                    View organizer = view.findViewById(R.id.organizer_container);
                    organizer.setVisibility(View.GONE);
                }

                setTextViewText(view, R.id.description, event.description);

                boolean first = true;
                StringBuilder sb = new StringBuilder();
                for (Attendee attendee : event.attendees) {
                    if (first) {
                        first = false;
                        sb.append("Attendees: ");
                    } else {
                        sb.append(", ");
                    }
                    sb.append(attendee.getCalAddress().toString().replaceFirst("mailto:", ""));
                }
                setTextViewText(view, R.id.attendees, sb.toString());

                first = true;
                sb = new StringBuilder();
                for (VAlarm alarm : event.alarms) {
                    if (first) {
                        first = false;
                        sb.append("Reminders: ");
                    } else {
                        sb.append(", ");
                    }
                    sb.append(alarm.getTrigger().getValue());
                }
                setTextViewText(view, R.id.reminders, sb.toString());
            }
        }

        private class LoadContactTask extends AsyncTask<Void, Void, Contact> {
            View view;

            LoadContactTask(View v) {
                super();
                view = v;
            }

            @Override
            protected Contact doInBackground(Void... aVoids) {
                InputStream is = new ByteArrayInputStream(syncEntry.getContent().getBytes(Charsets.UTF_8));

                try {
                    return Contact.fromStream(is, Charsets.UTF_8, null)[0];
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Contact contact) {
                final View loader = view.findViewById(R.id.loading_msg);
                loader.setVisibility(View.GONE);
                final View contentContainer = view.findViewById(R.id.content_container);
                contentContainer.setVisibility(View.VISIBLE);

                TextView tv = (TextView) view.findViewById(R.id.display_name);
                tv.setText(contact.displayName);

                final ViewGroup mainCard = (ViewGroup) view.findViewById(R.id.main_card);
                final ViewGroup aboutCard = (ViewGroup) view.findViewById(R.id.about_card);
                aboutCard.findViewById(R.id.title_container).setVisibility(View.VISIBLE);

                // TEL
                for (LabeledProperty<Telephone> labeledPhone : contact.phoneNumbers) {
                    List<TelephoneType> types = labeledPhone.property.getTypes();
                    String type = (types.size() > 0) ? types.get(0).getValue() : null;
                    addInfoItem(view.getContext(), mainCard, "Phone", type, labeledPhone.property.getText());
                }

                // EMAIL
                for (LabeledProperty<Email> labeledEmail : contact.emails) {
                    List<EmailType> types = labeledEmail.property.getTypes();
                    String type = (types.size() > 0) ? types.get(0).getValue() : null;
                    addInfoItem(view.getContext(), mainCard, "Email", type, labeledEmail.property.getValue());
                }

                // ORG, TITLE, ROLE
                if (contact.organization != null) {
                    addInfoItem(view.getContext(), aboutCard, "Organization", contact.jobTitle, contact.organization.getValues().get(0));
                }
                if (contact.jobDescription != null) {
                    addInfoItem(view.getContext(), aboutCard, "Job Description", null, contact.jobTitle);
                }

                // IMPP
                for (LabeledProperty<Impp> labeledImpp : contact.impps) {
                    addInfoItem(view.getContext(), mainCard, "Instant Messaging", labeledImpp.property.getProtocol(), labeledImpp.property.getHandle());
                }

                // NICKNAME
                if (contact.nickName != null) {
                    addInfoItem(view.getContext(), aboutCard, "Nickname", null, contact.nickName.getValues().get(0));
                }

                // ADR
                for (LabeledProperty<Address> labeledAddress : contact.addresses) {
                    List<AddressType> types = labeledAddress.property.getTypes();
                    String type = (types.size() > 0) ? types.get(0).getValue() : null;
                    addInfoItem(view.getContext(), mainCard, "Address", type, labeledAddress.property.getLabel());
                }

                // NOTE
                if (contact.note != null) {
                    addInfoItem(view.getContext(), aboutCard, "Note", null, contact.note);
                }

                // URL
                for (LabeledProperty<Url> labeledUrl : contact.urls) {
                    addInfoItem(view.getContext(), aboutCard, "Website", null, labeledUrl.property.getValue());
                }

                // ANNIVERSARY
                if (contact.anniversary != null) {
                    String date = getDisplayedDatetime(contact.anniversary.getDate().getTime(), contact.anniversary.getDate().getTime(), true, getContext());
                    addInfoItem(view.getContext(), aboutCard, "Anniversary", null, date);
                }
                // BDAY
                if (contact.birthDay != null) {
                    String date = getDisplayedDatetime(contact.birthDay.getDate().getTime(), contact.birthDay.getDate().getTime(), true, getContext());
                    addInfoItem(view.getContext(), aboutCard, "Birthday", null, date);
                }

                // RELATED
                for (Related related : contact.relations) {
                    List<RelatedType> types = related.getTypes();
                    String type = (types.size() > 0) ? types.get(0).getValue() : null;
                    addInfoItem(view.getContext(), aboutCard, "Relation", type, related.getText());
                }

                // PHOTO
                // if (contact.photo != null)
            }
        }

        private static View addInfoItem(Context context, ViewGroup parent, String type, String label, String value) {
            ViewGroup layout = (ViewGroup) parent.findViewById(R.id.container);
            View infoItem = LayoutInflater.from(context).inflate(R.layout.contact_info_item, layout, false);
            layout.addView(infoItem);
            setTextViewText(infoItem, R.id.type, type);
            setTextViewText(infoItem, R.id.title, label);
            setTextViewText(infoItem, R.id.content, value);
            parent.setVisibility(View.VISIBLE);

            return infoItem;
        }

        private static void setTextViewText(View parent, int id, String text) {
            TextView tv = (TextView) parent.findViewById(id);
            if (text == null) {
                tv.setVisibility(View.GONE);
            } else {
                tv.setText(text);
            }
        }

        public static String getDisplayedDatetime(long startMillis, long endMillis, boolean allDay, Context context) {
            // Configure date/time formatting.
            int flagsDate = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY;
            int flagsTime = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(context)) {
                flagsTime |= DateUtils.FORMAT_24HOUR;
            }

            String datetimeString = null;
            if (allDay) {
                // For multi-day allday events or single-day all-day events that are not
                // today or tomorrow, use framework formatter.
                Formatter f = new Formatter(new StringBuilder(50), Locale.getDefault());
                datetimeString = DateUtils.formatDateRange(context, f, startMillis,
                        endMillis, flagsDate, Time.TIMEZONE_UTC).toString();
            } else {
                // For multiday events, shorten day/month names.
                // Example format: "Fri Apr 6, 5:00pm - Sun, Apr 8, 6:00pm"
                int flagsDatetime = flagsDate | flagsTime | DateUtils.FORMAT_ABBREV_MONTH |
                        DateUtils.FORMAT_ABBREV_WEEKDAY;
                datetimeString = DateUtils.formatDateRange(context, startMillis, endMillis,
                        flagsDatetime);
            }
            return datetimeString;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
