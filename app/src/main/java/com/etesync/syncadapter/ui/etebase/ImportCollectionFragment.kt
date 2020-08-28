package com.etesync.syncadapter.ui.etebase

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.BaseActivity
import com.etesync.syncadapter.ui.importlocal.ImportFragment
import com.etesync.syncadapter.ui.importlocal.LocalCalendarImportFragment
import com.etesync.syncadapter.ui.importlocal.LocalContactImportFragment

class ImportCollectionFragment : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.import_actions_list, container, false)
        setHasOptionsMenu(true)

        if (savedInstanceState == null) {
            collectionModel.observe(this) {
                (activity as? BaseActivity?)?.supportActionBar?.setTitle(R.string.import_dialog_title)
                if (container != null) {
                    initUi(inflater, ret, it)
                }
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View, cachedCollection: CachedCollection) {
        val accountHolder = model.value!!

        var card = v.findViewById<View>(R.id.import_file)
        var img = card.findViewById<View>(R.id.action_icon) as ImageView
        var text = card.findViewById<View>(R.id.action_text) as TextView
        img.setImageResource(R.drawable.ic_file_white)
        text.setText(R.string.import_button_file)
        card.setOnClickListener {
            parentFragmentManager.commit {
                add(ImportFragment.newInstance(accountHolder.account, cachedCollection), null)
            }
        }

        card = v.findViewById(R.id.import_account)
        img = card.findViewById<View>(R.id.action_icon) as ImageView
        text = card.findViewById<View>(R.id.action_text) as TextView
        img.setImageResource(R.drawable.ic_account_circle_white)
        text.setText(R.string.import_button_local)
        card.setOnClickListener {
            if (cachedCollection.meta.collectionType == Constants.ETEBASE_TYPE_CALENDAR) {
                parentFragmentManager.commit {
                    replace(R.id.fragment_container, LocalCalendarImportFragment(accountHolder.account, cachedCollection.col.uid))
                    addToBackStack(null)
                }
            } else if (cachedCollection.meta.collectionType == Constants.ETEBASE_TYPE_ADDRESS_BOOK) {
                parentFragmentManager.commit {
                    replace(R.id.fragment_container, LocalContactImportFragment(accountHolder.account, cachedCollection.col.uid))
                    addToBackStack(null)
                }
            }
            // FIXME: should be in the fragments once we kill legacy
            (activity as? BaseActivity?)?.supportActionBar?.setTitle(R.string.import_select_account)
        }

        if (collectionModel.value!!.meta.collectionType == Constants.ETEBASE_TYPE_TASKS) {
            card.visibility = View.GONE
        }
    }
}