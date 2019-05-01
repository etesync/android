package com.etesync.syncadapter.ui.importlocal

import android.accounts.Account
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import at.bitfire.ical4android.CalendarStorageException
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalEvent

class LocalCalendarImportFragment : ListFragment() {

    private lateinit var account: Account
    private lateinit var info: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        account = arguments!!.getParcelable(KEY_ACCOUNT)!!
        info = arguments!!.getSerializable(KEY_COLLECTION_INFO) as CollectionInfo
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_local_calendar_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importAccount()
    }

    protected fun importAccount() {
        val calendarAccountList = CalendarAccount.loadAll(context!!.contentResolver)

        val listCalendar = listView as ExpandableListView

        val adapter = ExpandableListAdapter(context!!, calendarAccountList)
        listCalendar.setAdapter(adapter)

        listCalendar.setOnChildClickListener { aExpandableListView, aView, groupPosition, childPosition, aL ->
            ImportEvents().execute(calendarAccountList[groupPosition].getCalendars()[childPosition])
            false
        }
    }


    private inner class ExpandableListAdapter(private val context: Context, private val calendarAccounts: List<CalendarAccount>) : BaseExpandableListAdapter() {
        private val accountResolver: AccountResolver

        init {
            this.accountResolver = AccountResolver(context)
        }

        private inner class ChildViewHolder {
            internal var textView: TextView? = null
        }

        private inner class GroupViewHolder {
            internal var titleTextView: TextView? = null
            internal var descriptionTextView: TextView? = null
            internal var iconImageView: ImageView? = null
        }

        override fun getChild(groupPosition: Int, childPosititon: Int): Any {
            return calendarAccounts[groupPosition].getCalendars()[childPosititon].displayName!!
        }

        override fun getChildId(groupPosition: Int, childPosition: Int): Long {
            return childPosition.toLong()
        }

        override fun getChildView(groupPosition: Int, childPosition: Int,
                                  isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView

            val childText = getChild(groupPosition, childPosition) as String
            val viewHolder: ChildViewHolder
            if (convertView == null) {
                val inflater = context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                convertView = inflater.inflate(R.layout.import_calendars_list_item, null)
            }

            if (convertView!!.tag != null) {
                viewHolder = convertView.tag as ChildViewHolder
            } else {
                viewHolder = ChildViewHolder()
                viewHolder.textView = convertView
                        .findViewById<View>(R.id.listItemText) as TextView
                convertView.tag = viewHolder
            }
            viewHolder.textView!!.text = childText
            return convertView
        }

        override fun getChildrenCount(groupPosition: Int): Int {
            return calendarAccounts[groupPosition].getCalendars()
                    .size
        }

        override fun getGroup(groupPosition: Int): Any {
            return calendarAccounts[groupPosition]
        }

        override fun getGroupCount(): Int {
            return calendarAccounts.size
        }

        override fun getGroupId(groupPosition: Int): Long {
            return groupPosition.toLong()
        }

        override fun getGroupView(groupPosition: Int, isExpanded: Boolean,
                                  convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val calendarAccount = getGroup(groupPosition) as CalendarAccount
            val viewHolder: GroupViewHolder
            if (convertView == null) {
                val inflater = context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                convertView = inflater.inflate(R.layout.import_content_list_header, null)
            }
            if (convertView!!.tag != null) {
                viewHolder = convertView.tag as GroupViewHolder
            } else {
                viewHolder = GroupViewHolder()
                viewHolder.titleTextView = convertView
                        .findViewById<View>(R.id.title) as TextView
                viewHolder.descriptionTextView = convertView
                        .findViewById<View>(R.id.description) as TextView
                viewHolder.iconImageView = convertView.findViewById<View>(R.id.icon) as ImageView
                convertView.tag = viewHolder
            }

            viewHolder.titleTextView!!.text = calendarAccount.accountName
            val accountInfo = accountResolver.resolve(calendarAccount.accountType)
            viewHolder.descriptionTextView!!.text = accountInfo.name
            viewHolder.iconImageView!!.setImageDrawable(accountInfo.icon)

            return convertView
        }

        override fun hasStableIds(): Boolean {
            return false
        }

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
            return true
        }
    }

    protected inner class ImportEvents : AsyncTask<LocalCalendar, Int, ResultFragment.ImportResult>() {
        private lateinit var progressDialog: ProgressDialog

        override fun onPreExecute() {
            progressDialog = ProgressDialog(activity)
            progressDialog.setTitle(R.string.import_dialog_title)
            progressDialog.setMessage(getString(R.string.import_dialog_adding_entries))
            progressDialog.setCanceledOnTouchOutside(false)
            progressDialog.setCancelable(false)
            progressDialog.isIndeterminate = false
            progressDialog.setIcon(R.drawable.ic_import_export_black)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.show()
        }

        override fun doInBackground(vararg calendars: LocalCalendar): ResultFragment.ImportResult {
            return importEvents(calendars[0])
        }

        override fun onProgressUpdate(vararg progress: Int?) {
            progressDialog.progress = progress[0]!!
        }

        override fun onPostExecute(result: ResultFragment.ImportResult) {
            if (progressDialog.isShowing && !activity!!.isDestroyed) {
                progressDialog.dismiss()
            }
            (activity as ResultFragment.OnImportCallback).onImportResult(result)
        }

        private fun importEvents(fromCalendar: LocalCalendar): ResultFragment.ImportResult {
            val result = ResultFragment.ImportResult()
            try {
                val localCalendar = LocalCalendar.findByName(account,
                        context!!.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!,
                        LocalCalendar.Factory, info!!.uid!!)
                val localEvents = fromCalendar.findAll()
                val total = localEvents.size
                progressDialog.max = total
                result.total = total.toLong()
                var progress = 0
                for (currentLocalEvent in localEvents) {
                    val event = currentLocalEvent.event
                    try {
                        val localEvent = LocalEvent(localCalendar!!, event!!, null, null)
                        localEvent.addAsDirty()
                        result.added++
                    } catch (e: CalendarStorageException) {
                        e.printStackTrace()

                    }

                    publishProgress(++progress)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                result.e = e
            }

            return result
        }
    }

    companion object {

        fun newInstance(account: Account, info: CollectionInfo): LocalCalendarImportFragment {
            val frag = LocalCalendarImportFragment()
            val args = Bundle(1)
            args.putParcelable(KEY_ACCOUNT, account)
            args.putSerializable(KEY_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }
    }
}
