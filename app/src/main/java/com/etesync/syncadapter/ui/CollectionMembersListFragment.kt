package com.etesync.syncadapter.ui

import android.accounts.Account
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ListFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.JournalModel
import io.requery.Persistable
import io.requery.sql.EntityDataStore
import okhttp3.HttpUrl

class CollectionMembersListFragment : ListFragment(), AdapterView.OnItemClickListener, Refreshable {
    private lateinit var data: EntityDataStore<Persistable>
    private lateinit var account: Account
    private lateinit var info: CollectionInfo
    private lateinit var journalEntity: JournalEntity
    private var asyncTask: AsyncTask<*, *, *>? = null

    private var emptyTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = (context!!.applicationContext as App).data
        account = arguments!!.getParcelable(Constants.KEY_ACCOUNT)
        info = arguments!!.getSerializable(Constants.KEY_COLLECTION_INFO) as CollectionInfo
        journalEntity = JournalModel.Journal.fetch(data!!, info!!.getServiceEntity(data), info!!.uid)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.collection_members_list, container, false)

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = view.findViewById<View>(android.R.id.empty) as TextView
        return view
    }

    override fun refresh() {
        asyncTask = JournalMembersFetch().execute()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refresh()

        listView.onItemClickListener = this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (asyncTask != null)
            asyncTask!!.cancel(true)
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val member = listAdapter.getItem(position) as JournalManager.Member

        AlertDialog.Builder(activity!!)
                .setIcon(R.drawable.ic_info_dark)
                .setTitle(R.string.collection_members_remove_title)
                .setMessage(getString(R.string.collection_members_remove, member.user))
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    val frag = RemoveMemberFragment.newInstance(account, info, member.user!!)
                    frag.show(fragmentManager!!, null)
                }
                .setNegativeButton(android.R.string.no) { dialog, which -> }.show()
    }

    internal inner class MembersListAdapter(context: Context) : ArrayAdapter<JournalManager.Member>(context, R.layout.collection_members_list_item) {

        override fun getView(position: Int, v: View?, parent: ViewGroup): View {
            var v = v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.collection_members_list_item, parent, false)

            val member = getItem(position)

            val tv = v!!.findViewById<View>(R.id.title) as TextView
            tv.text = member!!.user
            return v
        }
    }

    private inner class JournalMembersFetch : AsyncTask<Void, Void, JournalMembersFetch.MembersResult>() {
        override fun doInBackground(vararg voids: Void): MembersResult {
            try {
                val settings = AccountSettings(context!!, account!!)
                val httpClient = HttpClient.create(context!!, settings)
                val journalsManager = JournalManager(httpClient, HttpUrl.get(settings.uri!!)!!)

                val journal = JournalManager.Journal.fakeWithUid(journalEntity!!.uid)
                return MembersResult(journalsManager.listMembers(journal), null)
            } catch (e: Exception) {
                return MembersResult(null, e)
            }

        }

        override fun onPostExecute(result: MembersResult) {
            if (result.throwable == null) {
                val listAdapter = MembersListAdapter(context!!)
                setListAdapter(listAdapter)

                listAdapter.addAll(result.members)

                emptyTextView!!.setText(R.string.collection_members_list_empty)
            } else {
                emptyTextView!!.text = result.throwable.localizedMessage
            }
        }

        internal inner class MembersResult(val members: List<JournalManager.Member>?, val throwable: Throwable?)
    }

    companion object {

        fun newInstance(account: Account, info: CollectionInfo): CollectionMembersListFragment {
            val frag = CollectionMembersListFragment()
            val args = Bundle(1)
            args.putParcelable(Constants.KEY_ACCOUNT, account)
            args.putSerializable(Constants.KEY_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }
    }
}
