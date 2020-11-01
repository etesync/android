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
import androidx.fragment.app.commit
import at.bitfire.ical4android.CalendarStorageException
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalEvent


class LocalCalendarImportFragment : ListFragment() {
    private lateinit var account: Account
    private lateinit var uid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
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
            val activity = activity
            if (activity == null) {
                Logger.log.warning("Activity is null on import.")
                return
            }

            if (progressDialog.isShowing && !activity.isDestroyed) {
                progressDialog.dismiss()
            }
            onImportResult(result)
        }

        private fun importEvents(fromCalendar: LocalCalendar): ResultFragment.ImportResult {
            val result = ResultFragment.ImportResult()
            try {
                val localCalendar = LocalCalendar.findByName(account,
                        context!!.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!,
                        LocalCalendar.Factory, uid)
                val localEvents = fromCalendar.findAll()
                val total = localEvents.size
                progressDialog.max = total
                result.total = total.toLong()
                var progress = 0
                for (currentLocalEvent in localEvents) {
                    val event = currentLocalEvent.event
                    try {
                        localCalendar!!

                        var localEvent = if (event == null || event.uid == null)
                            null
                        else
                            localCalendar.findByUid(event.uid!!)

                        if (localEvent != null) {
                            localEvent.updateAsDirty(event!!)
                            result.updated++
                        } else {
                            localEvent = LocalEvent(localCalendar, event!!, event.uid, null)
                            localEvent.addAsDirty()
                            result.added++
                        }
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

    fun onImportResult(importResult: ResultFragment.ImportResult) {
        val fragment = ResultFragment.newInstance(importResult)
        parentFragmentManager.commit(true) {
            add(fragment, "importResult")
        }
    }

    companion object {

        fun newInstance(account: Account, uid: String): LocalCalendarImportFragment {
            val ret = LocalCalendarImportFragment()
            ret.account = account
            ret.uid = uid
            return ret
        }
    }
}
