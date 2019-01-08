/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.LoaderManager
import android.content.*
import android.content.ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.*
import android.widget.*
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.model.ServiceEntity
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.setup.SetupUserInfoFragment
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.ShowcaseBuilder
import tourguide.tourguide.ToolTip
import java.util.logging.Level

class AccountActivity : BaseActivity(), Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener, LoaderManager.LoaderCallbacks<AccountActivity.AccountInfo>, Refreshable {

    private lateinit var account: Account
    private var accountInfo: AccountInfo? = null

    internal var listCalDAV: ListView? = null
    internal var listCardDAV: ListView? = null
    internal var listTaskDAV: ListView? = null

    private val onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
        val list = parent as ListView
        val adapter = list.adapter as ArrayAdapter<*>
        val journalEntity = adapter.getItem(position) as JournalEntity
        val info = journalEntity.getInfo()

        startActivity(ViewCollectionActivity.newIntent(this@AccountActivity, account!!, info))
    }

    private val formattedFingerprint: String?
        get() {
            var settings: AccountSettings? = null
            try {
                settings = AccountSettings(this, account!!)
                return Crypto.AsymmetricCryptoManager.getPrettyKeyFingerprint(settings.keyPair!!.publicKey)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.getParcelableExtra(EXTRA_ACCOUNT)
        title = account!!.name

        setContentView(R.layout.activity_account)

        val icMenu = ContextCompat.getDrawable(this, R.drawable.ic_menu_light)

        // CardDAV toolbar
        val tbCardDAV = findViewById<View>(R.id.carddav_menu) as Toolbar
        tbCardDAV.overflowIcon = icMenu
        tbCardDAV.inflateMenu(R.menu.carddav_actions)
        tbCardDAV.setOnMenuItemClickListener(this)
        tbCardDAV.setTitle(R.string.settings_carddav)

        // CalDAV toolbar
        val tbCalDAV = findViewById<View>(R.id.caldav_menu) as Toolbar
        tbCalDAV.overflowIcon = icMenu
        tbCalDAV.inflateMenu(R.menu.caldav_actions)
        tbCalDAV.setOnMenuItemClickListener(this)
        tbCalDAV.setTitle(R.string.settings_caldav)

        // TaskDAV toolbar
        val tbTaskDAV = findViewById<View>(R.id.taskdav_menu) as Toolbar
        tbTaskDAV.overflowIcon = icMenu
        tbTaskDAV.inflateMenu(R.menu.taskdav_actions)
        tbTaskDAV.setOnMenuItemClickListener(this)
        tbTaskDAV.setTitle(R.string.settings_taskdav)

        // load CardDAV/CalDAV journals
        loaderManager.initLoader(0, intent.extras, this)

        if (!HintManager.getHintSeen(this, HINT_VIEW_COLLECTION)) {
            ShowcaseBuilder.getBuilder(this)
                    .setToolTip(ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.account_showcase_view_collection)))
                    .playOn(tbCardDAV)
            HintManager.setHintSeen(this, HINT_VIEW_COLLECTION, true)
        }

        if (!SetupUserInfoFragment.hasUserInfo(this, account!!)) {
            SetupUserInfoFragment.newInstance(account!!).show(supportFragmentManager, null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_account, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sync_now -> requestSync()
            R.id.settings -> {
                val intent = Intent(this, AccountSettingsActivity::class.java)
                intent.putExtra(Constants.KEY_ACCOUNT, account)
                startActivity(intent)
            }
            R.id.delete_account -> AlertDialog.Builder(this@AccountActivity)
                    .setIcon(R.drawable.ic_error_dark)
                    .setTitle(R.string.account_delete_confirmation_title)
                    .setMessage(R.string.account_delete_confirmation_text)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes) { dialog, which -> deleteAccount() }
                    .show()
            R.id.show_fingerprint -> {
                val view = layoutInflater.inflate(R.layout.fingerprint_alertdialog, null)
                view.findViewById<View>(R.id.body).visibility = View.GONE
                (view.findViewById<View>(R.id.fingerprint) as TextView).text = formattedFingerprint
                val dialog = AlertDialog.Builder(this@AccountActivity)
                        .setIcon(R.drawable.ic_fingerprint_dark)
                        .setTitle(R.string.show_fingperprint_title)
                        .setView(view)
                        .setPositiveButton(android.R.string.yes) { dialog, which -> }.create()
                dialog.show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val info: CollectionInfo
        when (item.itemId) {
            R.id.create_calendar -> {
                info = CollectionInfo()
                info.type = CollectionInfo.Type.CALENDAR
                startActivity(CreateCollectionActivity.newIntent(this@AccountActivity, account!!, info))
            }
            R.id.create_tasklist -> {
                info = CollectionInfo()
                info.type = CollectionInfo.Type.TASKS
                startActivity(CreateCollectionActivity.newIntent(this@AccountActivity, account!!, info))
            }
            R.id.create_addressbook -> {
                info = CollectionInfo()
                info.type = CollectionInfo.Type.ADDRESS_BOOK
                startActivity(CreateCollectionActivity.newIntent(this@AccountActivity, account!!, info))
            }
        }
        return false
    }

    /* LOADERS AND LOADED DATA */

    class AccountInfo {
        internal var carddav: ServiceInfo? = null
        internal var caldav: ServiceInfo? = null
        internal var taskdav: ServiceInfo? = null

        class ServiceInfo {
            internal var id: Long = 0
            internal var refreshing: Boolean = false

            internal var journals: List<JournalEntity>? = null
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<AccountInfo> {
        return AccountLoader(this, account!!)
    }

    override fun refresh() {
        loaderManager.restartLoader(0, intent.extras, this)
    }

    override fun onLoadFinished(loader: Loader<AccountInfo>, info: AccountInfo) {
        accountInfo = info

        var card = findViewById<View>(R.id.carddav) as CardView
        if (info.carddav != null) {
            val progress = findViewById<View>(R.id.carddav_refreshing) as ProgressBar
            progress.visibility = if (info.carddav!!.refreshing) View.VISIBLE else View.GONE

            listCardDAV = findViewById<View>(R.id.address_books) as ListView
            listCardDAV!!.isEnabled = !info.carddav!!.refreshing
            listCardDAV!!.setAlpha(if (info.carddav!!.refreshing) 0.5f else 1f)

            val adapter = CollectionListAdapter(this, account!!)
            adapter.addAll(info.carddav!!.journals!!)
            listCardDAV!!.adapter = adapter
            listCardDAV!!.onItemClickListener = onItemClickListener
        } else
            card.visibility = View.GONE

        card = findViewById<View>(R.id.caldav) as CardView
        if (info.caldav != null) {
            val progress = findViewById<View>(R.id.caldav_refreshing) as ProgressBar
            progress.visibility = if (info.caldav!!.refreshing) View.VISIBLE else View.GONE

            listCalDAV = findViewById<View>(R.id.calendars) as ListView
            listCalDAV!!.isEnabled = !info.caldav!!.refreshing
            listCalDAV!!.setAlpha(if (info.caldav!!.refreshing) 0.5f else 1f)

            val adapter = CollectionListAdapter(this, account!!)
            adapter.addAll(info.caldav!!.journals!!)
            listCalDAV!!.adapter = adapter
            listCalDAV!!.onItemClickListener = onItemClickListener
        } else
            card.visibility = View.GONE

        card = findViewById<View>(R.id.caldav) as CardView
        if (info.taskdav != null) {
            val progress = findViewById<View>(R.id.taskdav_refreshing) as ProgressBar
            progress.visibility = if (info.taskdav!!.refreshing) View.VISIBLE else View.GONE

            listTaskDAV = findViewById<View>(R.id.tasklists) as ListView
            listTaskDAV!!.isEnabled = !info.taskdav!!.refreshing
            listTaskDAV!!.setAlpha(if (info.taskdav!!.refreshing) 0.5f else 1f)

            val adapter = CollectionListAdapter(this, account!!)
            adapter.addAll(info.taskdav!!.journals!!)
            listTaskDAV!!.adapter = adapter
            listTaskDAV!!.onItemClickListener = onItemClickListener
        } else
            card.visibility = View.GONE
    }

    override fun onLoaderReset(loader: Loader<AccountInfo>) {
        if (listCardDAV != null)
            listCardDAV!!.adapter = null

        if (listCalDAV != null)
            listCalDAV!!.adapter = null

        if (listTaskDAV != null)
            listTaskDAV!!.adapter = null
    }


    private class AccountLoader(context: Context, private val account: Account) : AsyncTaskLoader<AccountInfo>(context), AccountUpdateService.RefreshingStatusListener, ServiceConnection, SyncStatusObserver {
        private var davService: AccountUpdateService.InfoBinder? = null
        private var syncStatusListener: Any? = null

        override fun onStartLoading() {
            syncStatusListener = ContentResolver.addStatusChangeListener(SYNC_OBSERVER_TYPE_ACTIVE, this)

            context.bindService(Intent(context, AccountUpdateService::class.java), this, Context.BIND_AUTO_CREATE)
        }

        override fun onStopLoading() {
            davService!!.removeRefreshingStatusListener(this)
            context.unbindService(this)

            if (syncStatusListener != null)
                ContentResolver.removeStatusChangeListener(syncStatusListener)
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            davService = service as AccountUpdateService.InfoBinder
            davService!!.addRefreshingStatusListener(this, false)

            forceLoad()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            davService = null
        }

        override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) {
            forceLoad()
        }

        override fun onStatusChanged(which: Int) {
            forceLoad()
        }

        override fun loadInBackground(): AccountInfo {
            val info = AccountInfo()

            val data = (context.applicationContext as App).data

            for (serviceEntity in data.select(ServiceEntity::class.java).where(ServiceEntity.ACCOUNT.eq(account.name)).get()) {
                val id = serviceEntity.id.toLong()
                val service = serviceEntity.type
                when (service) {
                    CollectionInfo.Type.ADDRESS_BOOK -> {
                        info.carddav = AccountInfo.ServiceInfo()
                        info.carddav!!.id = id
                        info.carddav!!.refreshing = davService != null && davService!!.isRefreshing(id) || ContentResolver.isSyncActive(account, App.addressBooksAuthority)
                        info.carddav!!.journals = JournalEntity.getJournals(data, serviceEntity)

                        val accountManager = AccountManager.get(context)
                        for (addrBookAccount in accountManager.getAccountsByType(App.addressBookAccountType)) {
                            val addressBook = LocalAddressBook(context, addrBookAccount, null)
                            try {
                                if (account == addressBook.mainAccount)
                                    info.carddav!!.refreshing = info.carddav!!.refreshing or ContentResolver.isSyncActive(addrBookAccount, ContactsContract.AUTHORITY)
                            } catch (e: ContactsStorageException) {
                            }

                        }
                    }
                    CollectionInfo.Type.CALENDAR -> {
                        info.caldav = AccountInfo.ServiceInfo()
                        info.caldav!!.id = id
                        info.caldav!!.refreshing = davService != null && davService!!.isRefreshing(id) ||
                                ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY)
                        info.caldav!!.journals = JournalEntity.getJournals(data, serviceEntity)
                    }
                    CollectionInfo.Type.TASKS -> {
                        info.taskdav = AccountInfo.ServiceInfo()
                        info.taskdav!!.id = id
                        info.taskdav!!.refreshing = davService != null && davService!!.isRefreshing(id) ||
                                ContentResolver.isSyncActive(account, TaskProvider.ProviderName.OpenTasks.authority)
                        info.taskdav!!.journals = JournalEntity.getJournals(data, serviceEntity)
                    }
                }
            }
            return info
        }
    }


    /* LIST ADAPTERS */

    class CollectionListAdapter(context: Context, private val account: Account) : ArrayAdapter<JournalEntity>(context, R.layout.account_collection_item) {

        override fun getView(position: Int, v: View?, parent: ViewGroup): View {
            var v = v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.account_collection_item, parent, false)

            val journalEntity = getItem(position)
            val info = journalEntity!!.info

            var tv = v!!.findViewById<View>(R.id.title) as TextView
            tv.text = if (TextUtils.isEmpty(info.displayName)) info.uid else info.displayName

            tv = v.findViewById<View>(R.id.description) as TextView
            if (TextUtils.isEmpty(info.description))
                tv.visibility = View.GONE
            else {
                tv.visibility = View.VISIBLE
                tv.text = info.description
            }

            val vColor = v.findViewById<View>(R.id.color)
            if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                vColor.visibility = View.GONE
            } else {
                vColor.setBackgroundColor(info.color ?: LocalCalendar.defaultColor)
            }

            val readOnly = v.findViewById<View>(R.id.read_only)
            readOnly.visibility = if (journalEntity.isReadOnly) View.VISIBLE else View.GONE

            val shared = v.findViewById<View>(R.id.shared)
            val isOwner = journalEntity.isOwner(account.name)
            shared.visibility = if (isOwner) View.GONE else View.VISIBLE

            return v
        }
    }

    /* USER ACTIONS */

    private fun deleteAccount() {
        val accountManager = AccountManager.get(this)

        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, this, { future ->
                try {
                    if (future.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                        finish()
                } catch(e: Exception) {
                    App.log.log(Level.SEVERE, "Couldn't remove account", e)
                }
            }, null)
        else
            accountManager.removeAccount(account, { future ->
                try {
                    if (future.result)
                        finish()
                } catch (e: Exception) {
                    App.log.log(Level.SEVERE, "Couldn't remove account", e)
                }
            }, null)
    }


    private fun requestSync() {
        requestSync(account)
        Snackbar.make(findViewById(R.id.parent), R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        val EXTRA_ACCOUNT = "account"
        private val HINT_VIEW_COLLECTION = "ViewCollection"

        protected fun requestSync(account: Account?) {
            val authorities = arrayOf(App.addressBooksAuthority, CalendarContract.AUTHORITY, TaskProvider.ProviderName.OpenTasks.authority)

            for (authority in authorities) {
                val extras = Bundle()
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
                ContentResolver.requestSync(account, authority, extras)
            }
        }
    }

}
