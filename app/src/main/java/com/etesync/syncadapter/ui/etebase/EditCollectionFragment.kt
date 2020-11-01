package com.etesync.syncadapter.ui.etebase

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.etebase.client.Collection
import com.etebase.client.exceptions.EtebaseException
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.syncadapter.requestSync
import com.etesync.syncadapter.ui.BaseActivity
import org.apache.commons.lang3.StringUtils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import yuku.ambilwarna.AmbilWarnaDialog

class EditCollectionFragment : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()
    private val itemsModel: ItemsViewModel by activityViewModels()
    private val loadingModel: LoadingViewModel by viewModels()

    private lateinit var cachedCollection: CachedCollection
    private var isCreating: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.activity_create_collection, container, false)
        setHasOptionsMenu(true)

        if (savedInstanceState == null) {
            updateTitle()
            if (container != null) {
                initUi(inflater, ret)
            }
        }

        return ret
    }

    fun updateTitle() {
        cachedCollection.let {
            var titleId: Int = R.string.create_calendar
            if (isCreating) {
                when (cachedCollection.collectionType) {
                    Constants.ETEBASE_TYPE_CALENDAR -> {
                        titleId = R.string.create_calendar
                    }
                    Constants.ETEBASE_TYPE_TASKS -> {
                        titleId = R.string.create_tasklist
                    }
                    Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                        titleId = R.string.create_addressbook
                    }
                }
            } else {
                titleId = R.string.edit_collection
            }
            (activity as? BaseActivity?)?.supportActionBar?.setTitle(titleId)
        }
    }

    private fun initUi(inflater: LayoutInflater, v: View) {
        val title = v.findViewById<EditText>(R.id.display_name)
        val desc = v.findViewById<EditText>(R.id.description)

        val meta = cachedCollection.meta

        title.setText(meta.name)
        desc.setText(meta.description)

        val colorSquare = v.findViewById<View>(R.id.color)
        when (cachedCollection.collectionType) {
            Constants.ETEBASE_TYPE_CALENDAR -> {
                title.setHint(R.string.create_calendar_display_name_hint)

                val color = LocalCalendar.parseColor(meta.color)
                colorSquare.setBackgroundColor(color)
                colorSquare.setOnClickListener {
                    AmbilWarnaDialog(context, (colorSquare.background as ColorDrawable).color, true, object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onCancel(dialog: AmbilWarnaDialog) {}

                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            colorSquare.setBackgroundColor(color)
                        }
                    }).show()
                }
            }
            Constants.ETEBASE_TYPE_TASKS -> {
                title.setHint(R.string.create_tasklist_display_name_hint)

                val color = LocalCalendar.parseColor(meta.color)
                colorSquare.setBackgroundColor(color)
                colorSquare.setOnClickListener {
                    AmbilWarnaDialog(context, (colorSquare.background as ColorDrawable).color, true, object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onCancel(dialog: AmbilWarnaDialog) {}

                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            colorSquare.setBackgroundColor(color)
                        }
                    }).show()
                }
            }
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                title.setHint(R.string.create_addressbook_display_name_hint)

                val colorGroup = v.findViewById<View>(R.id.color_group)
                colorGroup.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_edit_collection, menu)
        if (isCreating) {
            menu.findItem(R.id.on_delete).setVisible(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.on_delete -> {
                deleteColection()
            }
            R.id.on_save -> {
                saveCollection()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun deleteColection() {
        val meta = cachedCollection.meta
        val name = meta.name

        AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_collection_confirm_title)
                .setMessage(getString(R.string.delete_collection_confirm_warning, name))
                .setPositiveButton(android.R.string.yes) { dialog, _ ->
                    doDeleteCollection()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
                .show()
    }

    private fun doDeleteCollection() {
        loadingModel.setLoading(true)
        doAsync {
            try {
                val col = cachedCollection.col
                val meta = col.meta
                meta.mtime = System.currentTimeMillis()
                col.meta = meta
                col.delete()
                uploadCollection(col)
                val applicationContext = activity?.applicationContext
                if (applicationContext != null) {
                    requestSync(applicationContext, model.value!!.account)
                }
                activity?.finish()
            } catch (e: EtebaseException) {
                uiThread {
                    Logger.log.warning(e.localizedMessage)
                    context?.let { context ->
                        AlertDialog.Builder(context)
                                .setIcon(R.drawable.ic_info_dark)
                                .setTitle(R.string.exception)
                                .setMessage(e.localizedMessage)
                                .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
                    }
                }
            } finally {
                uiThread {
                    loadingModel.setLoading(false)
                }
            }
        }
    }

    private fun saveCollection() {
        var ok = true

        val meta = cachedCollection.meta
        val v = requireView()

        var edit = v.findViewById<EditText>(R.id.display_name)
        meta.name = edit.text.toString()
        if (TextUtils.isEmpty(meta.name)) {
            edit.error = getString(R.string.create_collection_display_name_required)
            ok = false
        }

        edit = v.findViewById<EditText>(R.id.description)
        meta.description = StringUtils.trimToNull(edit.text.toString())

        meta.mtime = System.currentTimeMillis()

        if (ok) {
            when (cachedCollection.collectionType) {
                Constants.ETEBASE_TYPE_CALENDAR, Constants.ETEBASE_TYPE_TASKS -> {
                    val view = v.findViewById<View>(R.id.color)
                    val color = (view.background as ColorDrawable).color
                    meta.color = String.format("#%06X", 0xFFFFFF and color)
                }
                Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                }
            }

            loadingModel.setLoading(true)
            doAsync {
                try {
                    val col = cachedCollection.col
                    col.meta = meta
                    uploadCollection(col)
                    val applicationContext = activity?.applicationContext
                    if (applicationContext != null) {
                        requestSync(applicationContext, model.value!!.account)
                    }
                    if (isCreating) {
                        // Load the items since we just created it
                        itemsModel.loadItems(model.value!!, cachedCollection)
                        parentFragmentManager.commit {
                            replace(R.id.fragment_container, ViewCollectionFragment())
                        }
                    } else {
                        parentFragmentManager.popBackStack()
                    }
                } catch (e: EtebaseException) {
                    uiThread {
                        AlertDialog.Builder(requireContext())
                                .setIcon(R.drawable.ic_info_dark)
                                .setTitle(R.string.exception)
                                .setMessage(e.localizedMessage)
                                .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
                    }
                } finally {
                    uiThread {
                        loadingModel.setLoading(false)
                    }
                }
            }
        }
    }

    private fun uploadCollection(col: Collection) {
        val accountHolder = model.value!!
        val etebaseLocalCache = accountHolder.etebaseLocalCache
        val colMgr = accountHolder.colMgr
        colMgr.upload(col)
        synchronized(etebaseLocalCache) {
            etebaseLocalCache.collectionSet(colMgr, col)
        }
        collectionModel.loadCollection(model.value!!, col.uid)
    }

    companion object {
        fun newInstance(cachedCollection: CachedCollection, isCreating: Boolean = false): EditCollectionFragment {
            val ret = EditCollectionFragment()
            ret.cachedCollection = cachedCollection
            ret.isCreating = isCreating
            return ret
        }
    }
}