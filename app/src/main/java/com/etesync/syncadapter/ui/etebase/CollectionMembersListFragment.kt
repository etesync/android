package com.etesync.syncadapter.ui.etebase

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.ListFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.etebase.client.CollectionAccessLevel
import com.etebase.client.CollectionMember
import com.etebase.client.FetchOptions
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.R
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import java.util.concurrent.Future

class CollectionMembersListFragment : ListFragment(), AdapterView.OnItemClickListener {
    private val model: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()
    private val membersModel: CollectionMembersViewModel by viewModels()

    private var emptyTextView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.collection_members_list, container, false)

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = view.findViewById<TextView>(android.R.id.empty)
        return view
    }

    private fun setListAdapterMembers(members: List<CollectionMember>) {
        val context = context
        if (context != null) {
            val listAdapter = MembersListAdapter(context)
            setListAdapter(listAdapter)

            listAdapter.addAll(members)

            emptyTextView!!.setText(R.string.collection_members_list_empty)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.observe(this) {
            collectionModel.observe(this) { cachedCollection ->
                membersModel.loadMembers(it, cachedCollection)
            }
        }

        membersModel.observe(this) {
            setListAdapterMembers(it)
        }

        listView.onItemClickListener = this
    }

    override fun onDestroyView() {
        super.onDestroyView()

        membersModel.cancelLoad()
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val member = listAdapter?.getItem(position) as CollectionMember

        if (member.accessLevel == CollectionAccessLevel.Admin) {
            AlertDialog.Builder(requireActivity())
                    .setIcon(R.drawable.ic_error_dark)
                    .setTitle(R.string.collection_members_remove_title)
                    .setMessage(R.string.collection_members_remove_admin)
                    .setNegativeButton(android.R.string.ok) { _, _ -> }.show()
            return
        }

        AlertDialog.Builder(requireActivity())
                .setIcon(R.drawable.ic_info_dark)
                .setTitle(R.string.collection_members_remove_title)
                .setMessage(getString(R.string.collection_members_remove, member.username))
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    membersModel.removeMember(model.value!!, collectionModel.value!!, member.username)
                }
                .setNegativeButton(android.R.string.no) { dialog, which -> }.show()
    }

    internal inner class MembersListAdapter(context: Context) : ArrayAdapter<CollectionMember>(context, R.layout.collection_members_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.collection_members_list_item, parent, false)

            val member = getItem(position)

            val tv = v!!.findViewById<View>(R.id.title) as TextView
            tv.text = member!!.username

            // FIXME: Also mark admins
            val readOnly = v.findViewById<View>(R.id.read_only)
            readOnly.visibility = if (member.accessLevel == CollectionAccessLevel.ReadOnly) View.VISIBLE else View.GONE

            return v
        }
    }
}

class CollectionMembersViewModel : ViewModel() {
    private val members = MutableLiveData<List<CollectionMember>>()
    private var asyncTask: Future<Unit>? = null

    fun loadMembers(accountCollectionHolder: AccountHolder, cachedCollection: CachedCollection) {
        asyncTask = doAsync {
            val ret = LinkedList<CollectionMember>()
            val col = cachedCollection.col
            val memberManager = accountCollectionHolder.colMgr.getMemberManager(col)
            var iterator: String? = null
            var done = false
            while (!done) {
                val chunk = memberManager.list(FetchOptions().iterator(iterator).limit(30))
                iterator = chunk.stoken
                done = chunk.isDone

                ret.addAll(chunk.data)
            }

            uiThread {
                members.value = ret
            }
        }
    }

    fun removeMember(accountCollectionHolder: AccountHolder, cachedCollection: CachedCollection, username: String) {
        doAsync {
            val col = cachedCollection.col
            val memberManager = accountCollectionHolder.colMgr.getMemberManager(col)
            memberManager.remove(username)
            val ret = members.value!!.filter { it.username != username }

            uiThread {
                members.value = ret
            }
        }
    }

    fun cancelLoad() {
        asyncTask?.cancel(true)
    }

    fun observe(owner: LifecycleOwner, observer: (List<CollectionMember>) -> Unit) =
            members.observe(owner, observer)
}