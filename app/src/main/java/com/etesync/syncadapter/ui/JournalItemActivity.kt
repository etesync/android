package com.etesync.syncadapter.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import at.bitfire.vcard4android.Contact
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.SyncEntry
import com.etesync.syncadapter.ui.journalviewer.ListEntriesFragment.Companion.setJournalEntryView
import com.etesync.syncadapter.utils.EventEmailInvitation
import com.google.android.material.tabs.TabLayout
import ezvcard.util.PartialDate
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Future

class JournalItemActivity : BaseActivity(), Refreshable {
    private var journalEntity: JournalEntity? = null
    private lateinit var account: Account
    protected lateinit var info: CollectionInfo
    private lateinit var syncEntry: SyncEntry
    private var emailInvitationEvent: Event? = null
    private var emailInvitationEventString: String? = null

    override fun refresh() {
        val data = (applicationContext as App).data

        journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid)
        if (journalEntity == null || journalEntity!!.isDeleted) {
            finish()
            return
        }

        account = intent.extras!!.getParcelable(ViewCollectionActivity.EXTRA_ACCOUNT)!!
        info = journalEntity!!.info

        title = info.displayName

        setJournalEntryView(findViewById(R.id.journal_list_item), info, syncEntry)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.journal_item_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        info = intent.extras!!.getSerializable(Constants.KEY_COLLECTION_INFO) as CollectionInfo
        syncEntry = intent.extras!!.getSerializable(KEY_SYNC_ENTRY) as SyncEntry

        refresh()

        val viewPager = findViewById<View>(R.id.viewpager) as ViewPager
        viewPager.adapter = TabsAdapter(supportFragmentManager, this, info, syncEntry)

        val tabLayout = findViewById<View>(R.id.tabs) as TabLayout
        tabLayout.setupWithViewPager(viewPager)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (emailInvitationEvent != null) {
            menuInflater.inflate(R.menu.activity_journal_item, menu)
        }
        return true
    }

    fun allowSendEmail(event: Event?, icsContent: String) {
        emailInvitationEvent = event
        emailInvitationEventString = icsContent
        invalidateOptionsMenu()
    }

    fun sendEventInvite(item: MenuItem) {
        val intent = EventEmailInvitation(this, account).createIntent(emailInvitationEvent!!, emailInvitationEventString!!)
        startActivity(intent)
    }

    private class TabsAdapter(fm: FragmentManager, private val context: Context, private val info: CollectionInfo, private val syncEntry: SyncEntry) : FragmentPagerAdapter(fm) {

        override fun getCount(): Int {
            // FIXME: Make it depend on info type (only have non-raw for known types)
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return if (position == 0) {
                context.getString(R.string.journal_item_tab_main)
            } else {
                context.getString(R.string.journal_item_tab_raw)
            }
        }

        override fun getItem(position: Int): Fragment {
            return if (position == 0) {
                PrettyFragment.newInstance(info, syncEntry)
            } else {
                TextFragment.newInstance(syncEntry)
            }
        }
    }

    class TextFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater.inflate(R.layout.text_fragment, container, false)

            val tv = v.findViewById<View>(R.id.content) as TextView

            val syncEntry = arguments!!.getSerializable(KEY_SYNC_ENTRY) as SyncEntry
            tv.text = syncEntry.content

            return v
        }

        companion object {
            fun newInstance(syncEntry: SyncEntry): TextFragment {
                val frag = TextFragment()
                val args = Bundle(1)
                args.putSerializable(KEY_SYNC_ENTRY, syncEntry)
                frag.arguments = args
                return frag
            }
        }
    }

    class PrettyFragment : Fragment() {
        internal lateinit var info: CollectionInfo
        internal lateinit var syncEntry: SyncEntry
        private var asyncTask: Future<Unit>? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            var v: View? = null

            info = arguments!!.getSerializable(Constants.KEY_COLLECTION_INFO) as CollectionInfo
            syncEntry = arguments!!.getSerializable(KEY_SYNC_ENTRY) as SyncEntry

            when (info.type) {
                CollectionInfo.Type.ADDRESS_BOOK -> {
                    v = inflater.inflate(R.layout.contact_info, container, false)
                    asyncTask = loadContactTask(v)
                }
                CollectionInfo.Type.CALENDAR -> {
                    v = inflater.inflate(R.layout.event_info, container, false)
                    asyncTask = loadEventTask(v)
                }
                CollectionInfo.Type.TASKS -> {
                    v = inflater.inflate(R.layout.task_info, container, false)
                    asyncTask = loadTaskTask(v)
                }
            }

            return v
        }

        override fun onDestroyView() {
            super.onDestroyView()
            if (asyncTask != null)
                asyncTask!!.cancel(true)
        }

        private fun loadEventTask(view: View): Future<Unit> {
            return doAsync {
                var event: Event? = null
                val inputReader = StringReader(syncEntry.content)

                try {
                    event = Event.fromReader(inputReader, null)[0]
                } catch (e: InvalidCalendarException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (event != null) {
                    uiThread {
                        val loader = view.findViewById<View>(R.id.event_info_loading_msg)
                        loader.visibility = View.GONE
                        val contentContainer = view.findViewById<View>(R.id.event_info_scroll_view)
                        contentContainer.visibility = View.VISIBLE

                        setTextViewText(view, R.id.title, event.summary)

                        setTextViewText(view, R.id.when_datetime, getDisplayedDatetime(event.dtStart?.date?.time!!, event.dtEnd?.date!!.time, event.isAllDay(), context))

                        setTextViewText(view, R.id.where, event.location)

                        val organizer = event.organizer
                        if (organizer != null) {
                            val tv = view.findViewById<View>(R.id.organizer) as TextView
                            tv.text = organizer.calAddress.toString().replaceFirst("mailto:".toRegex(), "")
                        } else {
                            val organizerView = view.findViewById<View>(R.id.organizer_container)
                            organizerView.visibility = View.GONE
                        }

                        setTextViewText(view, R.id.description, event.description)

                        var first = true
                        var sb = StringBuilder()
                        for (attendee in event.attendees) {
                            if (first) {
                                first = false
                                sb.append(getString(R.string.journal_item_attendees)).append(": ")
                            } else {
                                sb.append(", ")
                            }
                            sb.append(attendee.calAddress.toString().replaceFirst("mailto:".toRegex(), ""))
                        }
                        setTextViewText(view, R.id.attendees, sb.toString())

                        first = true
                        sb = StringBuilder()
                        for (alarm in event.alarms) {
                            if (first) {
                                first = false
                                sb.append(getString(R.string.journal_item_reminders)).append(": ")
                            } else {
                                sb.append(", ")
                            }
                            sb.append(alarm.trigger.value)
                        }
                        setTextViewText(view, R.id.reminders, sb.toString())

                        if (event.attendees.isNotEmpty() && activity != null) {
                            (activity as JournalItemActivity).allowSendEmail(event, syncEntry.content)
                        }
                    }
                }
            }
        }

        private fun loadTaskTask(view: View): Future<Unit> {
            return doAsync {
                var task: Task? = null
                val inputReader = StringReader(syncEntry.content)

                try {
                    task = Task.fromReader(inputReader)[0]
                } catch (e: InvalidCalendarException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (task != null) {
                    uiThread {
                        val loader = view.findViewById<View>(R.id.task_info_loading_msg)
                        loader.visibility = View.GONE
                        val contentContainer = view.findViewById<View>(R.id.task_info_scroll_view)
                        contentContainer.visibility = View.VISIBLE

                        setTextViewText(view, R.id.title, task.summary)

                        setTextViewText(view, R.id.where, task.location)

                        val organizer = task.organizer
                        if (organizer != null) {
                            val tv = view.findViewById<View>(R.id.organizer) as TextView
                            tv.text = organizer.calAddress.toString().replaceFirst("mailto:".toRegex(), "")
                        } else {
                            val organizerView = view.findViewById<View>(R.id.organizer_container)
                            organizerView.visibility = View.GONE
                        }

                        setTextViewText(view, R.id.description, task.description)
                    }
                }
            }
        }

        private fun loadContactTask(view: View): Future<Unit> {
            return doAsync {
                var contact: Contact? = null
                val reader = StringReader(syncEntry.content)

                try {
                    contact = Contact.fromReader(reader, null)[0]
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (contact != null) {
                    uiThread {
                        val loader = view.findViewById<View>(R.id.loading_msg)
                        loader.visibility = View.GONE
                        val contentContainer = view.findViewById<View>(R.id.content_container)
                        contentContainer.visibility = View.VISIBLE

                        val tv = view.findViewById<View>(R.id.display_name) as TextView
                        tv.text = contact.displayName

                        if (contact.group) {
                            showGroup(contact)
                        } else {
                            showContact(contact)
                        }
                    }
                }
            }
        }


        private fun showGroup(contact: Contact) {
            val view = this.view!!

            val mainCard = view.findViewById<View>(R.id.main_card) as ViewGroup

            addInfoItem(view.context, mainCard, getString(R.string.journal_item_member_count), null, contact.members.size.toString())

            for (member in contact.members) {
                addInfoItem(view.context, mainCard, getString(R.string.journal_item_member), null, member)
            }
        }


        private fun showContact(contact: Contact) {
            val view = this.view!!
            val mainCard = view.findViewById<View>(R.id.main_card) as ViewGroup
            val aboutCard = view.findViewById<View>(R.id.about_card) as ViewGroup
            aboutCard.findViewById<View>(R.id.title_container).visibility = View.VISIBLE

            // TEL
            for (labeledPhone in contact.phoneNumbers) {
                val types = labeledPhone.property.types
                val type = if (types.size > 0) types[0].value else null
                addInfoItem(view.context, mainCard, getString(R.string.journal_item_phone), type, labeledPhone.property.text)
            }

            // EMAIL
            for (labeledEmail in contact.emails) {
                val types = labeledEmail.property.types
                val type = if (types.size > 0) types[0].value else null
                addInfoItem(view.context, mainCard, getString(R.string.journal_item_email), type, labeledEmail.property.value)
            }

            // ORG, TITLE, ROLE
            if (contact.organization != null) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_organization), contact.jobTitle, contact.organization?.values!![0])
            }
            if (contact.jobDescription != null) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_job_description), null, contact.jobTitle)
            }

            // IMPP
            for (labeledImpp in contact.impps) {
                addInfoItem(view.context, mainCard, getString(R.string.journal_item_impp), labeledImpp.property.protocol, labeledImpp.property.handle)
            }

            // NICKNAME
            if (contact.nickName != null && !contact.nickName?.values?.isEmpty()!!) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_nickname), null, contact.nickName?.values!![0])
            }

            // ADR
            for (labeledAddress in contact.addresses) {
                val types = labeledAddress.property.types
                val type = if (types.size > 0) types[0].value else null
                addInfoItem(view.context, mainCard, getString(R.string.journal_item_address), type, labeledAddress.property.label)
            }

            // NOTE
            if (contact.note != null) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_note), null, contact.note)
            }

            // URL
            for (labeledUrl in contact.urls) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_website), null, labeledUrl.property.value)
            }

            // ANNIVERSARY
            if (contact.anniversary != null) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_anniversary), null, getDisplayedDate(contact.anniversary?.date, contact.anniversary?.partialDate))
            }
            // BDAY
            if (contact.birthDay != null) {
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_birthday), null, getDisplayedDate(contact.birthDay?.date, contact.birthDay?.partialDate))
            }

            // RELATED
            for (related in contact.relations) {
                val types = related.types
                val type = if (types.size > 0) types[0].value else null
                addInfoItem(view.context, aboutCard, getString(R.string.journal_item_relation), type, related.text)
            }

            // PHOTO
            // if (contact.photo != null)
        }

        private fun getDisplayedDate(date: Date?, partialDate: PartialDate?): String? {
            if (date != null) {
                val epochDate = date.time
                return getDisplayedDatetime(epochDate, epochDate, true, context)
            } else if (partialDate != null){
                val formatter = SimpleDateFormat("d MMMM", Locale.getDefault())
                val calendar = GregorianCalendar()
                calendar.set(Calendar.DAY_OF_MONTH, partialDate.date!!)
                calendar.set(Calendar.MONTH, partialDate.month!! - 1)
                return formatter.format(calendar.time)
            }

            return null
        }

        companion object {

            fun newInstance(info: CollectionInfo, syncEntry: SyncEntry): PrettyFragment {
                val frag = PrettyFragment()
                val args = Bundle(1)
                args.putSerializable(Constants.KEY_COLLECTION_INFO, info)
                args.putSerializable(KEY_SYNC_ENTRY, syncEntry)
                frag.arguments = args
                return frag
            }

            private fun addInfoItem(context: Context, parent: ViewGroup, type: String, label: String?, value: String?): View {
                val layout = parent.findViewById<View>(R.id.container) as ViewGroup
                val infoItem = LayoutInflater.from(context).inflate(R.layout.contact_info_item, layout, false)
                layout.addView(infoItem)
                setTextViewText(infoItem, R.id.type, type)
                setTextViewText(infoItem, R.id.title, label)
                setTextViewText(infoItem, R.id.content, value)
                parent.visibility = View.VISIBLE

                return infoItem
            }

            private fun setTextViewText(parent: View, id: Int, text: String?) {
                val tv = parent.findViewById<View>(id) as TextView
                if (text == null) {
                    tv.visibility = View.GONE
                } else {
                    tv.text = text
                }
            }

            fun getDisplayedDatetime(startMillis: Long, endMillis: Long, allDay: Boolean, context: Context?): String? {
                // Configure date/time formatting.
                val flagsDate = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
                var flagsTime = DateUtils.FORMAT_SHOW_TIME
                if (DateFormat.is24HourFormat(context)) {
                    flagsTime = flagsTime or DateUtils.FORMAT_24HOUR
                }

                val datetimeString: String
                if (allDay) {
                    // For multi-day allday events or single-day all-day events that are not
                    // today or tomorrow, use framework formatter.
                    val f = Formatter(StringBuilder(50), Locale.getDefault())
                    datetimeString = DateUtils.formatDateRange(context, f, startMillis,
                            endMillis, flagsDate).toString()
                } else {
                    // For multiday events, shorten day/month names.
                    // Example format: "Fri Apr 6, 5:00pm - Sun, Apr 8, 6:00pm"
                    val flagsDatetime = flagsDate or flagsTime or DateUtils.FORMAT_ABBREV_MONTH or
                            DateUtils.FORMAT_ABBREV_WEEKDAY
                    datetimeString = DateUtils.formatDateRange(context, startMillis, endMillis,
                            flagsDatetime)
                }
                return datetimeString
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    companion object {
        private val KEY_SYNC_ENTRY = "syncEntry"

        fun newIntent(context: Context, account: Account, info: CollectionInfo, syncEntry: SyncEntry): Intent {
            val intent = Intent(context, JournalItemActivity::class.java)
            intent.putExtra(ViewCollectionActivity.EXTRA_ACCOUNT, account)
            intent.putExtra(Constants.KEY_COLLECTION_INFO, info)
            intent.putExtra(KEY_SYNC_ENTRY, syncEntry)
            return intent
        }
    }
}
