package com.etesync.syncadapter.ui.etebase

import android.content.DialogInterface
import android.graphics.Color.parseColor
import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.BaseActivity
import com.etesync.syncadapter.ui.EditCollectionActivity
import com.etesync.syncadapter.ui.WebViewActivity
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.ShowcaseBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import tourguide.tourguide.ToolTip
import java.util.*

class ViewCollectionFragment : Fragment() {
    private val collectionModel: CollectionViewModel by activityViewModels()
    private val itemsModel: ItemsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.view_collection_fragment, container, false)
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

    private fun initUi(inflater: LayoutInflater, container: View, cachedCollection: CachedCollection) {
        val title = container.findViewById<TextView>(R.id.display_name)
        if (!HintManager.getHintSeen(requireContext(), HINT_IMPORT)) {
            val tourGuide = ShowcaseBuilder.getBuilder(requireActivity())
                    .setToolTip(ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_import)).setGravity(Gravity.BOTTOM))
                    .setPointer(null)
            tourGuide.mOverlay.setHoleRadius(0)
            tourGuide.playOn(title)
            HintManager.setHintSeen(requireContext(), HINT_IMPORT, true)
        }

        val fab = container.findViewById<FloatingActionButton>(R.id.fab)
        fab?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.use_native_apps_title)
                    .setMessage(R.string.use_native_apps_body)
                    .setNegativeButton(R.string.navigation_drawer_guide, { _: DialogInterface, _: Int -> WebViewActivity.openUrl(requireContext(), Constants.helpUri) })
                    .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
        }

        val col = cachedCollection.col
        val meta = cachedCollection.meta
        val isAdmin = col.accessLevel == "adm"

        val colorSquare = container.findViewById<View>(R.id.color)
        val color = if (!meta.color.isNullOrBlank()) parseColor(meta.color) else LocalCalendar.defaultColor
        when (meta.collectionType) {
            Constants.ETEBASE_TYPE_CALENDAR -> {
                colorSquare.setBackgroundColor(color)
            }
            Constants.ETEBASE_TYPE_TASKS -> {
                colorSquare.setBackgroundColor(color)
                val tasksNotShowing = container.findViewById<View>(R.id.tasks_not_showing)
                tasksNotShowing.visibility = View.VISIBLE
            }
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                colorSquare.visibility = View.GONE
            }
        }

        title.text = meta.name

        val desc = container.findViewById<TextView>(R.id.description)
        desc.text = meta.description

        val owner = container.findViewById<TextView>(R.id.owner)
        if (isAdmin) {
            owner.visibility = View.GONE
        } else {
            owner.visibility = View.VISIBLE
            owner.text = "Shared with us" // FIXME: Figure out how to represent it and don't use a hardcoded string
        }

        itemsModel.observe(this) {
            val stats = container.findViewById<TextView>(R.id.stats)
            container.findViewById<View>(R.id.progressBar).visibility = View.GONE
            stats.text = String.format(Locale.getDefault(), "Change log items: %d", it.size)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_view_collection, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val cachedCollection = collectionModel.value!!

        when (item.itemId) {
            R.id.on_edit -> {
                if (cachedCollection.col.accessLevel == "adm") {
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, EditCollectionFragment(cachedCollection))
                        addToBackStack(EditCollectionFragment::class.java.name)
                    }
                } else {
                    val dialog = AlertDialog.Builder(requireContext())
                            .setIcon(R.drawable.ic_info_dark)
                            .setTitle(R.string.not_allowed_title)
                            .setMessage(R.string.edit_owner_only_anon)
                            .setPositiveButton(android.R.string.yes) { _, _ -> }.create()
                    dialog.show()
                }
            }
            R.id.on_manage_members -> {
                if (cachedCollection.col.accessLevel == "adm") {
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, CollectionMembersFragment())
                        addToBackStack(null)
                    }
                } else {
                    val dialog = AlertDialog.Builder(requireContext())
                            .setIcon(R.drawable.ic_info_dark)
                            .setTitle(R.string.not_allowed_title)
                            .setMessage(R.string.edit_owner_only_anon)
                            .setPositiveButton(android.R.string.yes) { _, _ -> }.create()
                    dialog.show()
                }            }
            R.id.on_import -> {
                Toast.makeText(context, "Import", Toast.LENGTH_LONG).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private val HINT_IMPORT = "Import"
    }
}