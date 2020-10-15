package com.etesync.syncadapter.ui.etebase

import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.activityViewModels
import androidx.viewpager.widget.ViewPager
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.Contact
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.CachedItem
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.*
import com.etesync.syncadapter.ui.BaseActivity
import com.etesync.syncadapter.utils.EventEmailInvitation
import com.etesync.syncadapter.utils.TaskProviderHandling
import com.google.android.material.tabs.TabLayout
import ezvcard.util.PartialDate
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Future

class CollectionItemFragment(private val cachedItem: CachedItem) : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()

    private var emailInvitationEvent: Event? = null
    private var emailInvitationEventString: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.journal_item_activity, container, false)
        setHasOptionsMenu(true)

        if (savedInstanceState == null) {
            collectionModel.observe(this) {
                (activity as? BaseActivity?)?.supportActionBar?.title = it.meta.name
                if (container != null) {
                    initUi(inflater, ret, it)
                }
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View, cachedCollection: CachedCollection) {
        val viewPager = v.findViewById<ViewPager>(R.id.viewpager)
        viewPager.adapter = TabsAdapter(childFragmentManager, this, requireContext(), cachedCollection, cachedItem)

        val tabLayout = v.findViewById<TabLayout>(R.id.tabs)
        tabLayout.setupWithViewPager(viewPager)

        v.findViewById<View>(R.id.journal_list_item).visibility = View.GONE
    }

    fun allowSendEmail(event: Event?, icsContent: String) {
        emailInvitationEvent = event
        emailInvitationEventString = icsContent
        activity?.invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.collection_item_fragment, menu)
        menu.setGroupVisible(R.id.journal_item_menu_event_invite, emailInvitationEvent != null)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val accountHolder = model.value!!
        when (item.itemId) {
            R.id.on_send_event_invite -> {
                val account = accountHolder.account
                val intent = EventEmailInvitation(requireContext(), account).createIntent(emailInvitationEvent!!, emailInvitationEventString!!)
                startActivity(intent)
            }
            R.id.on_restore_item -> {
                restoreItem(accountHolder)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun restoreItem(accountHolder: AccountHolder) {
        // FIXME: This code makes the assumption that providers are all available. May not be true for tasks, and potentially others too.
        val context = requireContext()
        val account = accountHolder.account
        val cachedCol = collectionModel.value!!
        when (cachedCol.collectionType) {
            Constants.ETEBASE_TYPE_CALENDAR -> {
                val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!
                val localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory, cachedCol.col.uid)!!
                val event = Event.eventsFromReader(StringReader(cachedItem.content))[0]
                var localEvent = localCalendar.findByUid(event.uid!!)
                if (localEvent != null) {
                    localEvent.updateAsDirty(event)
                } else {
                    localEvent = LocalEvent(localCalendar, event, event.uid, null)
                    localEvent.addAsDirty()
                }
            }
            Constants.ETEBASE_TYPE_TASKS -> {
                TaskProviderHandling.getWantedTaskSyncProvider(context)?.let {
                    val provider = TaskProvider.acquire(context, it)!!
                    val localTaskList = LocalTaskList.findByName(account, provider, LocalTaskList.Factory, cachedCol.col.uid)!!
                    val task = Task.tasksFromReader(StringReader(cachedItem.content))[0]
                    var localTask = localTaskList.findByUid(task.uid!!)
                    if (localTask != null) {
                        localTask.updateAsDirty(task)
                    } else {
                        localTask = LocalTask(localTaskList, task, task.uid, null)
                        localTask.addAsDirty()
                    }
                }
            }
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)!!
                val localAddressBook = LocalAddressBook.findByUid(context, provider, account, cachedCol.col.uid)!!
                val contact = Contact.fromReader(StringReader(cachedItem.content), null)[0]
                if (contact.group) {
                    // FIXME: not currently supported
                } else {
                    var localContact = localAddressBook.findByUid(contact.uid!!) as LocalContact?
                    if (localContact != null) {
                        localContact.updateAsDirty(contact)
                    } else {
                        localContact = LocalContact(localAddressBook, contact, contact.uid, null)
                        localContact.createAsDirty()
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(context)
                .setTitle(R.string.journal_item_restore_action)
                .setIcon(R.drawable.ic_restore_black)
                .setMessage(R.string.journal_item_restore_dialog_body)
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    // dismiss
                }
                .create()
        dialog.show()
    }
}

private class TabsAdapter(fm: FragmentManager, private val mainFragment: CollectionItemFragment, private val context: Context, private val cachedCollection: CachedCollection, private val cachedItem: CachedItem) : FragmentPagerAdapter(fm) {

    override fun getCount(): Int {
        // FIXME: Make it depend on info enumType (only have non-raw for known types)
        return 3
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return if (position == 0) {
            context.getString(R.string.journal_item_tab_main)
        } else if (position == 1) {
            context.getString(R.string.journal_item_tab_raw)
        } else {
            context.getString(R.string.journal_item_tab_revisions)
        }
    }

    override fun getItem(position: Int): Fragment {
        return if (position == 0) {
            PrettyFragment(mainFragment, cachedCollection, cachedItem.content)
        } else if (position == 1) {
            TextFragment(cachedItem.content)
        } else {
            ItemRevisionsListFragment(cachedCollection, cachedItem)
        }
    }
}


class TextFragment(private val content: String) : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.text_fragment, container, false)

        val tv = v.findViewById<View>(R.id.content) as TextView

        tv.text = content

        return v
    }
}

class PrettyFragment(private val mainFragment: CollectionItemFragment, private val cachedCollection: CachedCollection, private val content: String) : Fragment() {
    private var asyncTask: Future<Unit>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var v: View? = null

        when (cachedCollection.collectionType) {
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                v = inflater.inflate(R.layout.contact_info, container, false)
                asyncTask = loadContactTask(v)
            }
            Constants.ETEBASE_TYPE_CALENDAR -> {
                v = inflater.inflate(R.layout.event_info, container, false)
                asyncTask = loadEventTask(v)
            }
            Constants.ETEBASE_TYPE_TASKS -> {
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
            val inputReader = StringReader(content)

            try {
                event = Event.eventsFromReader(inputReader, null)[0]
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

                    if (event.attendees.isNotEmpty()) {
                        mainFragment.allowSendEmail(event, content)
                    }

                }
            }
        }
    }

    private fun loadTaskTask(view: View): Future<Unit> {
        return doAsync {
            var task: Task? = null
            val inputReader = StringReader(content)

            try {
                task = Task.tasksFromReader(inputReader)[0]
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
            val reader = StringReader(content)

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
        val view = requireView()

        val mainCard = view.findViewById<View>(R.id.main_card) as ViewGroup

        addInfoItem(view.context, mainCard, getString(R.string.journal_item_member_count), null, contact.members.size.toString())

        for (member in contact.members) {
            addInfoItem(view.context, mainCard, getString(R.string.journal_item_member), null, member)
        }
    }


    private fun showContact(contact: Contact) {
        val view = requireView()
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

                // We need to remove 24hrs because full day events are from the start of a day until the start of the next
                var adjustedEnd = endMillis - 24 * 60 * 60 * 1000;
                if (adjustedEnd < startMillis) {
                    adjustedEnd = startMillis;
                }
                val f = Formatter(StringBuilder(50), Locale.getDefault())
                datetimeString = DateUtils.formatDateRange(context, f, startMillis,
                        adjustedEnd, flagsDate).toString()
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
