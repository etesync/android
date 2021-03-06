package com.etesync.syncadapter.ui.etebase

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.CachedItem
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import java.text.SimpleDateFormat


class ListEntriesFragment : ListFragment(), AdapterView.OnItemClickListener {
    private val collectionModel: CollectionViewModel by activityViewModels()
    private val itemsModel: ItemsViewModel by activityViewModels()
    private var state: Parcelable? = null

    private var emptyTextView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.journal_viewer_list, container, false)

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = view.findViewById<View>(android.R.id.empty) as TextView

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var restored = false

        collectionModel.observe(this) { col ->
            itemsModel.observe(this) {
                val entries = it.sortedByDescending { item ->
                    item.meta.mtime ?: 0
                }
                val listAdapter = EntriesListAdapter(requireContext(), col)
                setListAdapter(listAdapter)

                listAdapter.addAll(entries)

                if(!restored && (state != null)) {
                    listView.onRestoreInstanceState(state)
                    restored = true
                }

                emptyTextView!!.text = getString(R.string.journal_entries_list_empty)
            }
        }

        listView.onItemClickListener = this
    }
    override fun onPause() {
        state = listView.onSaveInstanceState()
        super.onPause()
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val item = listAdapter?.getItem(position) as CachedItem
        activity?.supportFragmentManager?.commit {
            replace(R.id.fragment_container, CollectionItemFragment.newInstance(item))
            addToBackStack(EditCollectionFragment::class.java.name)
        }
    }

    internal inner class EntriesListAdapter(context: Context, val cachedCollection: CachedCollection) : ArrayAdapter<CachedItem>(context, R.layout.journal_viewer_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.journal_viewer_list_item, parent, false)!!

            val item = getItem(position)!!

            setItemView(v, cachedCollection.collectionType, item)

            /* FIXME: handle entry error:
            val entryError = data.select(EntryErrorEntity::class.java).where(EntryErrorEntity.ENTRY.eq(entryEntity)).limit(1).get().firstOrNull()
            if (entryError != null) {
                val errorIcon = v.findViewById<View>(R.id.error) as ImageView
                errorIcon.visibility = View.VISIBLE
            }
             */

            return v
        }
    }

    companion object {
        private val dateFormatter = SimpleDateFormat()
        private fun getLine(content: String?, prefix: String): String? {
            var content: String? = content ?: return null

            val start = content!!.indexOf(prefix)
            if (start >= 0) {
                val end = content.indexOf("\n", start)
                content = content.substring(start + prefix.length, end)
            } else {
                content = null
            }
            return content
        }

        fun setItemView(v: View, collectionType: String, item: CachedItem) {

            var tv = v.findViewById<View>(R.id.title) as TextView

            // FIXME: hacky way to make it show sensible info
            val prefix: String = when (collectionType) {
                Constants.ETEBASE_TYPE_CALENDAR, Constants.ETEBASE_TYPE_TASKS -> {
                    "SUMMARY:"
                }
                Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                    "FN:"
                }
                else -> {
                    ""
                }
            }

            val fullContent = item.content
            var content = getLine(fullContent, prefix)
            content = content ?: "Not found"
            tv.text = content

            tv = v.findViewById<View>(R.id.description) as TextView
            // FIXME: Don't use a hard-coded string
            content = "Modified: ${dateFormatter.format(item.meta.mtime ?: 0)}"
            tv.text = content

            val action = v.findViewById<View>(R.id.action) as ImageView
            if (item.item.isDeleted) {
                action.setImageResource(R.drawable.action_delete)
            } else {
                action.setImageResource(R.drawable.action_change)
            }
        }
    }
}
