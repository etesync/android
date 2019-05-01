/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.App
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.EntryEntity
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalTaskList
import com.etesync.syncadapter.ui.importlocal.ImportActivity
import com.etesync.syncadapter.ui.journalviewer.ListEntriesFragment
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.ShowcaseBuilder
import tourguide.tourguide.ToolTip
import java.io.FileNotFoundException
import java.util.*

class ViewCollectionActivity : BaseActivity(), Refreshable {

    private lateinit var account: Account
    private var journalEntity: JournalEntity? = null
    protected lateinit var info: CollectionInfo
    private var isOwner: Boolean = false

    override fun refresh() {
        val data = (applicationContext as App).data

        journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid)
        if (journalEntity == null || journalEntity!!.isDeleted) {
            finish()
            return
        }

        info = journalEntity!!.info
        isOwner = journalEntity!!.isOwner(account.name)

        val colorSquare = findViewById<View>(R.id.color)
        when (info.type) {
            CollectionInfo.Type.CALENDAR -> {
                colorSquare.setBackgroundColor(info.color ?: LocalCalendar.defaultColor)
            }
            CollectionInfo.Type.TASKS -> {
                colorSquare.setBackgroundColor(info.color ?: LocalCalendar.defaultColor)
            }
            CollectionInfo.Type.ADDRESS_BOOK -> {
                colorSquare.visibility = View.GONE
            }
        }

        LoadCountTask().execute()

        val title = findViewById<View>(R.id.display_name) as TextView
        title.text = info.displayName

        val desc = findViewById<View>(R.id.description) as TextView
        desc.text = info.description

        val owner = findViewById<View>(R.id.owner) as TextView
        if (isOwner) {
            owner.visibility = View.GONE
        } else {
            owner.visibility = View.VISIBLE
            owner.text = getString(R.string.account_owner, journalEntity!!.owner)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.view_collection_activity)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!
        info = intent.extras!!.getSerializable(EXTRA_COLLECTION_INFO) as CollectionInfo

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.list_entries_container, ListEntriesFragment.newInstance(account, info))
                    .commit()
        }

        refresh()

        val title = findViewById<View>(R.id.display_name) as TextView
        if (!HintManager.getHintSeen(this, HINT_IMPORT)) {
            val tourGuide = ShowcaseBuilder.getBuilder(this)
                    .setToolTip(ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_import)).setGravity(Gravity.BOTTOM))
                    .setPointer(null)
            tourGuide.mOverlay.setHoleRadius(0)
            tourGuide.playOn(title)
            HintManager.setHintSeen(this, HINT_IMPORT, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_view_collection, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun onEditCollection(item: MenuItem) {
        if (isOwner) {
            startActivity(EditCollectionActivity.newIntent(this, account, info))
        } else {
            val dialog = AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(getString(R.string.edit_owner_only, journalEntity!!.owner))
                    .setPositiveButton(android.R.string.yes) { _, _ -> }.create()
            dialog.show()
        }
    }

    fun onImport(item: MenuItem) {
        startActivity(ImportActivity.newIntent(this@ViewCollectionActivity, account, info))
    }

    fun onManageMembers(item: MenuItem) {
        if (info.version < 2) {
            val dialog = AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(R.string.members_old_journals_not_allowed)
                    .setPositiveButton(android.R.string.yes) { _, _ -> }.create()
            dialog.show()
        } else if (isOwner) {
            startActivity(CollectionMembersActivity.newIntent(this, account, info))
        } else {
            val dialog = AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_info_dark)
                    .setTitle(R.string.not_allowed_title)
                    .setMessage(getString(R.string.members_owner_only, journalEntity!!.owner))
                    .setPositiveButton(android.R.string.yes) { _, _ -> }.create()
            dialog.show()
        }
    }

    private inner class LoadCountTask : AsyncTask<Void, Void, Long>() {
        private var entryCount: Int = 0

        override fun doInBackground(vararg aVoids: Void): Long? {
            val data = (applicationContext as App).data

            val journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid)

            entryCount = data.count(EntryEntity::class.java).where(EntryEntity.JOURNAL.eq(journalEntity)).get().value()
            var count: Long = -1

            when (info.type) {
                CollectionInfo.Type.CALENDAR -> {
                    try {
                        val providerClient = contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)
                        if (providerClient == null) {
                            return null
                        }
                        val resource = LocalCalendar.findByName(account, providerClient, LocalCalendar.Factory, info.uid!!)
                        providerClient.release()
                        if (resource == null) {
                            return null
                        }
                        count = resource.count()
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: CalendarStorageException) {
                        e.printStackTrace()
                    }
                }
                CollectionInfo.Type.TASKS -> {
                    try {
                        val providerClient = TaskProvider.acquire(this@ViewCollectionActivity, TaskProvider.ProviderName.OpenTasks)
                        if (providerClient == null) {
                            return null
                        }
                        val resource = LocalTaskList.findByName(account, providerClient, LocalTaskList.Factory, info.uid!!)
                        if (resource == null) {
                            return null
                        }
                        count = resource.count()
                    } catch (e: ContactsStorageException) {
                        e.printStackTrace()
                    }
                }
                CollectionInfo.Type.ADDRESS_BOOK -> {
                    try {
                        val providerClient = contentResolver.acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI)
                        if (providerClient == null) {
                            return null
                        }
                        val resource = LocalAddressBook.findByUid(this@ViewCollectionActivity, providerClient, account, info.uid!!)
                        providerClient.release()
                        if (resource == null) {
                            return null
                        }
                        count = resource.count()
                    } catch (e: ContactsStorageException) {
                        e.printStackTrace()
                    }
                }
            }
            return count
        }

        override fun onPostExecute(result: Long?) {
            val stats = findViewById<View>(R.id.stats) as TextView
            findViewById<View>(R.id.progressBar).visibility = View.GONE

            if (result == null) {
                stats.text = "Stats loading error."
            } else {
                when (info.type) {
                    CollectionInfo.Type.CALENDAR -> {
                        stats.text = String.format(Locale.getDefault(), "Events: %d, Journal entries: %d",
                                result, entryCount)
                    }
                    CollectionInfo.Type.TASKS -> {
                        stats.text = String.format(Locale.getDefault(), "Tasks: %d, Journal entries: %d",
                                result, entryCount)
                    }
                    CollectionInfo.Type.ADDRESS_BOOK -> {
                        stats.text = String.format(Locale.getDefault(), "Contacts: %d, Journal Entries: %d",
                                result, entryCount)
                    }
                }
            }
        }
    }

    companion object {
        private val HINT_IMPORT = "Import"
        val EXTRA_ACCOUNT = "account"
        val EXTRA_COLLECTION_INFO = "collectionInfo"

        fun newIntent(context: Context, account: Account, info: CollectionInfo): Intent {
            val intent = Intent(context, ViewCollectionActivity::class.java)
            intent.putExtra(ViewCollectionActivity.EXTRA_ACCOUNT, account)
            intent.putExtra(ViewCollectionActivity.EXTRA_COLLECTION_INFO, info)
            return intent
        }
    }
}
