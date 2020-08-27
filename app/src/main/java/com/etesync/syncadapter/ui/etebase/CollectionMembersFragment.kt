package com.etesync.syncadapter.ui.etebase

import android.graphics.Color.parseColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.BaseActivity

class CollectionMembersFragment : Fragment() {
    private val collectionModel: CollectionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.etebase_view_collection_members, container, false)

        if (savedInstanceState == null) {
            collectionModel.observe(this) {
                (activity as? BaseActivity?)?.supportActionBar?.setTitle(R.string.collection_members_title)
                if (container != null) {
                    initUi(inflater, ret, it)
                }
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View, cachedCollection: CachedCollection) {
        v.findViewById<View>(R.id.add_member).setOnClickListener {

        }

        val meta = cachedCollection.meta
        val colorSquare = v.findViewById<View>(R.id.color)
        val color = if (!meta.color.isNullOrBlank()) parseColor(meta.color) else LocalCalendar.defaultColor
        when (meta.collectionType) {
            Constants.ETEBASE_TYPE_CALENDAR -> {
                colorSquare.setBackgroundColor(color)
            }
            Constants.ETEBASE_TYPE_TASKS -> {
                colorSquare.setBackgroundColor(color)
            }
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                colorSquare.visibility = View.GONE
            }
        }

        val title = v.findViewById<View>(R.id.display_name) as TextView
        title.text = meta.name

        val desc = v.findViewById<View>(R.id.description) as TextView
        desc.text = meta.description

        v.findViewById<View>(R.id.progressBar).visibility = View.GONE
    }
}