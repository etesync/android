package com.etesync.syncadapter.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.*
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Task
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etebase.client.Account as EtebaseAccount
import com.etebase.client.Client
import com.etebase.client.Item
import com.etebase.client.ItemMetadata
import com.etesync.journalmanager.model.SyncEntry
import com.etesync.syncadapter.*
import com.etesync.syncadapter.model.*
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.etebase.*
import com.etesync.syncadapter.ui.setup.CreateAccountFragment
import com.etesync.syncadapter.ui.setup.LoginCredentials
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import net.cachapa.expandablelayout.ExpandableLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.StringReader
import java.net.URI
import java.util.*
import kotlin.collections.HashMap

class MigrateV2Activity : BaseActivity() {
    private lateinit var accountV1: Account
    private val model: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountV1 = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!

        setContentView(R.layout.etebase_fragment_activity)

        if (savedInstanceState == null) {
            setTitle(R.string.migrate_v2_wizard_welcome_title)
            supportFragmentManager.commit {
                replace(R.id.fragment_container, WizardWelcomeFragment(accountV1))
            }
        }
    }

    companion object {
        private val EXTRA_ACCOUNT = "account"

        fun newIntent(context: Context, account: Account): Intent {
            val intent = Intent(context, MigrateV2Activity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            return intent
        }
    }
}


fun reportErrorHelper(context: Context, e: Throwable) {
    AlertDialog.Builder(context)
            .setIcon(R.drawable.ic_info_dark)
            .setTitle(R.string.exception)
            .setMessage(e.localizedMessage)
            .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
}

class WizardWelcomeFragment(private val accountV1: Account) : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.migrate_v2_wizard_welcome, container, false)

        if (savedInstanceState == null) {
            if (container != null) {
                initUi(inflater, ret)
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View) {
        v.findViewById<Button>(R.id.signup).setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragment_container, SignupFragment(accountV1))
                addToBackStack(null)
            }
        }

        v.findViewById<Button>(R.id.login).setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragment_container, LoginFragment(accountV1))
                addToBackStack(null)
            }
        }
    }
}

class SignupFragment(private val accountV1: Account) : Fragment() {
    internal lateinit var editUserName: TextInputLayout
    internal lateinit var editEmail: TextInputLayout
    internal lateinit var editPassword: TextInputLayout

    internal lateinit var showAdvanced: CheckedTextView
    internal lateinit var customServer: TextInputEditText


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.signup_fragment, container, false)

        editUserName = v.findViewById(R.id.user_name)
        editEmail = v.findViewById(R.id.email)
        editPassword = v.findViewById(R.id.url_password)
        showAdvanced = v.findViewById(R.id.show_advanced)
        customServer = v.findViewById(R.id.custom_server)

        // Hide stuff we don't need for the migration tool
        v.findViewById<View>(R.id.trial_notice).visibility = View.GONE
        editEmail.visibility = View.GONE
        editEmail.editText?.setText(accountV1.name)

        val login = v.findViewById<Button>(R.id.login)
        login.visibility = View.GONE

        val createAccount = v.findViewById<Button>(R.id.create_account)
        createAccount.setOnClickListener {
            val credentials = validateData()
            if (credentials != null) {
                SignupDoFragment(accountV1, credentials).show(requireFragmentManager(), null)
            }
        }

        val advancedLayout = v.findViewById<View>(R.id.advanced_layout) as ExpandableLayout

        showAdvanced.setOnClickListener {
            if (showAdvanced.isChecked) {
                showAdvanced.isChecked = false
                advancedLayout.collapse()
            } else {
                showAdvanced.isChecked = true
                advancedLayout.expand()
            }
        }

        return v
    }

    protected fun validateData(): SignupCredentials? {
        var valid = true

        val userName = editUserName.editText?.text.toString()
        // FIXME: this validation should only be done in the server, we are doing it here until the Java library supports field errors
        if ((userName.length < 6) || (!userName.matches(Regex("""^[\w.-]+$""")))) {
            editUserName.error = getString(R.string.login_username_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val email = editEmail.editText?.text.toString()
        if (email.isEmpty()) {
            editEmail.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editEmail.error = null
        }

        val password = editPassword.editText?.text.toString()
        if (password.length < 8) {
            editPassword.error = getString(R.string.signup_password_restrictions)
            valid = false
        } else {
            editPassword.error = null
        }

        var uri: URI? = null
        if (showAdvanced.isChecked) {
            val server = customServer.text.toString()
            // If this field is null, just use the default
            if (!server.isEmpty()) {
                val url = server.toHttpUrlOrNull()
                if (url != null) {
                    uri = url.toUri()
                    customServer.error = null
                } else {
                    customServer.error = getString(R.string.login_custom_server_error)
                    valid = false
                }
            }
        }

        return if (valid) SignupCredentials(uri, userName, email, password) else null
    }
}

class SignupDoFragment(private val accountV1: Account, private val signupCredentials: SignupCredentials) : DialogFragment() {
    private val model: ConfigurationViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.setting_up_encryption)
        progress.setMessage(getString(R.string.setting_up_encryption_content))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val settings = AccountSettings(requireContext(), accountV1)
            // Mark the etesync v1 account as wanting migration
            doAsync {
                val httpClient = HttpClient.Builder(context, settings).setForeground(true).build().okHttpClient
                val remote = settings.uri!!.toHttpUrlOrNull()!!.newBuilder()
                        .addPathSegments("etesync-v2/confirm-migration/")
                        .build()

                val body = RequestBody.create(null, byteArrayOf())

                val request = Request.Builder()
                        .post(body)
                        .url(remote)
                        .build()

                val response = httpClient.newCall(request).execute()
                uiThread {
                    if (context == null) {
                        dismissAllowingStateLoss()
                        return@uiThread
                    }
                    if (response.isSuccessful) {
                        model.signup(requireContext(), signupCredentials)
                    } else {
                        if (response.code == 400) {
                            reportErrorHelper(requireContext(), Error("User already migrated. Please login instead."))
                        } else {
                            reportErrorHelper(requireContext(), Error("Failed preparing account for migration"))
                        }
                        dismissAllowingStateLoss()
                    }
                }
            }
            model.observe(this) {
                if (it.isFailed) {
                    reportErrorHelper(requireContext(), it.error!!)
                    dismissAllowingStateLoss()
                } else {
                    doAsync {
                        val httpClient = HttpClient.Builder(context).setForeground(true).build().okHttpClient
                        val client = Client.create(httpClient, it.url.toString())
                        val etebase = EtebaseAccount.restore(client, it.etebaseSession!!, null)
                        uiThread {
                            fragmentManager?.commit {
                                replace(R.id.fragment_container, WizardCollectionsFragment(accountV1, etebase))
                                addToBackStack(null)
                            }
                            dismissAllowingStateLoss()
                        }
                    }
                } }
        }
    }
}


class LoginFragment(private val accountV1: Account) : Fragment() {
    internal lateinit var editUserName: EditText
    internal lateinit var editUrlPassword: TextInputLayout

    internal lateinit var showAdvanced: CheckedTextView
    internal lateinit var customServer: EditText


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        editUserName = v.findViewById<TextInputEditText>(R.id.user_name)
        editUrlPassword = v.findViewById<TextInputLayout>(R.id.url_password)
        showAdvanced = v.findViewById<CheckedTextView>(R.id.show_advanced)
        customServer = v.findViewById<TextInputEditText>(R.id.custom_server)

        v.findViewById<View>(R.id.create_account).visibility = View.GONE

        val login = v.findViewById<View>(R.id.login) as Button
        login.setOnClickListener {
            val credentials = validateLoginData()
            if (credentials != null)
                LoginDoFragment(accountV1, credentials).show(fragmentManager!!, null)
        }

        val forgotPassword = v.findViewById<View>(R.id.forgot_password) as TextView
        forgotPassword.setOnClickListener { WebViewActivity.openUrl(context!!, Constants.forgotPassword) }

        val advancedLayout = v.findViewById<View>(R.id.advanced_layout) as ExpandableLayout

        showAdvanced.setOnClickListener {
            if (showAdvanced.isChecked) {
                showAdvanced.isChecked = false
                advancedLayout.collapse()
            } else {
                showAdvanced.isChecked = true
                advancedLayout.expand()
            }
        }

        return v
    }

    protected fun validateLoginData(): LoginCredentials? {
        var valid = true

        val userName = editUserName.text.toString()
        if (userName.isEmpty()) {
            editUserName.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val password = editUrlPassword.editText?.text.toString()
        if (password.isEmpty()) {
            editUrlPassword.error = getString(R.string.login_password_required)
            valid = false
        } else {
            editUrlPassword.error = null
        }

        var uri: URI? = null
        if (showAdvanced.isChecked) {
            val server = customServer.text.toString()
            // If this field is null, just use the default
            if (!server.isEmpty()) {
                val url = server.toHttpUrlOrNull()
                if (url != null) {
                    uri = url.toUri()
                    customServer.error = null
                } else {
                    customServer.error = getString(R.string.login_custom_server_error)
                    valid = false
                }
            }
        }

        return if (valid) LoginCredentials(uri, userName, password) else null
    }
}

class LoginDoFragment(private val accountV1: Account, private val loginCredentials: LoginCredentials) : DialogFragment() {
    private val model: ConfigurationViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.setting_up_encryption)
        progress.setMessage(getString(R.string.setting_up_encryption_content))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val settings = AccountSettings(requireContext(), accountV1)
            model.login(requireContext(), loginCredentials)
            model.observe(this) {
                if (it.isFailed) {
                    reportErrorHelper(requireContext(), it.error!!)
                    dismissAllowingStateLoss()
                } else {
                    doAsync {
                        val httpClient = HttpClient.Builder(context).setForeground(true).build().okHttpClient
                        val client = Client.create(httpClient, it.url.toString())
                        val etebase = EtebaseAccount.restore(client, it.etebaseSession!!, null)
                        uiThread {
                            fragmentManager?.commit {
                                replace(R.id.fragment_container, WizardCollectionsFragment(accountV1, etebase))
                                addToBackStack(null)
                            }
                            dismissAllowingStateLoss()
                        }
                    }
                } }
        }
    }
}

class WizardCollectionsFragment(private val accountV1: Account, private val etebase: EtebaseAccount) : Fragment() {
    private val loadingModel: LoadingViewModel by viewModels()
    private lateinit var info: AccountActivity.AccountInfo
    private val migrateJournals = HashMap<String, AccountActivity.CollectionListItemInfo>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.migrate_v2_collections, container, false)

        if (savedInstanceState == null) {
            if (container != null) {
                initUi(inflater, ret)
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View) {
        v.findViewById<Button>(R.id.button_create).setOnClickListener {
            MigrateCollectionsDoFragment(etebase, this.migrateJournals).show(parentFragmentManager, null)
        }

        v.findViewById<Button>(R.id.button_skip).setOnClickListener {
            activity?.finish()
        }

        loadAccount(v)
    }

    private fun loadAccount(v: View) {
        val account = accountV1
        info = AccountActivity.AccountInfo()
        val data = (requireContext().applicationContext as App).data

        loadingModel.setLoading(true)
        doAsync {
            try {
                for (serviceEntity in data.select(ServiceEntity::class.java).where(ServiceEntity.ACCOUNT.eq(account.name)).get()) {
                    val service = serviceEntity.type!!
                    when (service) {
                        CollectionInfo.Type.ADDRESS_BOOK -> {
                            info.carddav = AccountActivity.AccountInfo.ServiceInfo()
                            info.carddav!!.infos = getLegacyJournals(data, serviceEntity)

                            val accountManager = AccountManager.get(context)
                            for (addrBookAccount in accountManager.getAccountsByType(App.addressBookAccountType)) {
                                val addressBook = LocalAddressBook(requireContext(), addrBookAccount, null)
                                try {
                                    if (account == addressBook.mainAccount)
                                        info.carddav!!.refreshing = info.carddav!!.refreshing or ContentResolver.isSyncActive(addrBookAccount, ContactsContract.AUTHORITY)
                                } catch (e: ContactsStorageException) {
                                }

                            }
                        }
                        CollectionInfo.Type.CALENDAR -> {
                            info.caldav = AccountActivity.AccountInfo.ServiceInfo()
                            info.caldav!!.infos = getLegacyJournals(data, serviceEntity)
                        }
                        CollectionInfo.Type.TASKS -> {
                            info.taskdav = AccountActivity.AccountInfo.ServiceInfo()
                            info.taskdav!!.infos = getLegacyJournals(data, serviceEntity)
                        }
                    }
                }
            } finally {
                uiThread {
                    loadingModel.setLoading(false)
                }
            }

            uiThread {
                if (info.carddav != null) {
                    val infos = info.carddav!!.infos!!
                    val listCardDAV = v.findViewById<View>(R.id.address_books) as ListView
                    val adapter = CollectionListAdapter(requireContext(), account)
                    adapter.addAll(infos)
                    listCardDAV.adapter = adapter
                    listCardDAV.setOnItemClickListener { adapterView, view, i, l ->
                        val infoItem = infos.get(i)
                        if (this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)) {
                            this@WizardCollectionsFragment.migrateJournals.remove(infoItem.uid)
                        } else {
                            this@WizardCollectionsFragment.migrateJournals.set(infoItem.uid, infoItem)
                        }
                        view.findViewById<CheckBox>(R.id.sync).isChecked = this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)
                    }
                }

                if (info.caldav != null) {
                    val infos = info.caldav!!.infos!!
                    val listCalDAV = v.findViewById<View>(R.id.calendars) as ListView
                    val adapter = CollectionListAdapter(requireContext(), account)
                    adapter.addAll(infos)
                    listCalDAV.adapter = adapter
                    listCalDAV.setOnItemClickListener { adapterView, view, i, l ->
                        val infoItem = infos.get(i)
                        if (this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)) {
                            this@WizardCollectionsFragment.migrateJournals.remove(infoItem.uid)
                        } else {
                            this@WizardCollectionsFragment.migrateJournals.set(infoItem.uid, infoItem)
                        }
                        view.findViewById<CheckBox>(R.id.sync).isChecked = this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)
                    }
                }

                if (info.taskdav != null) {
                    val infos = info.taskdav!!.infos!!
                    val listTaskDAV = v.findViewById<View>(R.id.tasklists) as ListView
                    val adapter = CollectionListAdapter(requireContext(), account)
                    adapter.addAll(infos)
                    listTaskDAV.adapter = adapter
                    listTaskDAV.setOnItemClickListener { adapterView, view, i, l ->
                        val infoItem = infos.get(i)
                        if (this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)) {
                            this@WizardCollectionsFragment.migrateJournals.remove(infoItem.uid)
                        } else {
                            this@WizardCollectionsFragment.migrateJournals.set(infoItem.uid, infoItem)
                        }
                        view.findViewById<CheckBox>(R.id.sync).isChecked = this@WizardCollectionsFragment.migrateJournals.contains(infoItem.uid)
                    }
                }
            }
        }
    }

    private fun getLegacyJournals(data: MyEntityDataStore, serviceEntity: ServiceEntity): List<AccountActivity.CollectionListItemInfo> {
        return JournalEntity.getJournals(data, serviceEntity).map {
            val info = it.info
            val isAdmin = it.isOwner(accountV1.name)
            AccountActivity.CollectionListItemInfo(it.uid, info.enumType!!, info.displayName!!, info.description
                    ?: "", info.color, it.isReadOnly, isAdmin, info)
        }
    }

    class CollectionListAdapter(context: Context, private val account: Account) : ArrayAdapter<AccountActivity.CollectionListItemInfo>(context, R.layout.account_collection_item) {
        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.account_collection_item, parent, false)

            v!!.findViewById<View>(R.id.sync).visibility = View.VISIBLE
            val info = getItem(position)!!

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
            if (info.enumType == CollectionInfo.Type.ADDRESS_BOOK) {
                vColor.visibility = View.GONE
            } else {
                vColor.setBackgroundColor(info.color ?: LocalCalendar.defaultColor)
            }

            val readOnly = v.findViewById<View>(R.id.read_only)
            readOnly.visibility = if (info.isReadOnly) View.VISIBLE else View.GONE

            val shared = v.findViewById<View>(R.id.shared)
            val isOwner = info.isAdmin
            shared.visibility = if (isOwner) View.GONE else View.VISIBLE

            return v
        }
    }
}


class MigrateCollectionsDoFragment(private val etebase: EtebaseAccount,
                                   private val migrateJournals: HashMap<String, AccountActivity.CollectionListItemInfo>) : DialogFragment() {
    private val configurationModel: ConfigurationViewModel by activityViewModels()
    private lateinit var progress: ProgressDialog
    private val CHUNK_SIZE = 20

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        progress = ProgressDialog(activity)
        progress.setTitle(R.string.migrate_v2_wizard_migrate_title)
        progress.setMessage(getString(R.string.migrate_v2_wizard_migrate_title))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            migrate()
        }
    }

    private fun migrate() {
        val data = (requireContext().applicationContext as App).data
        doAsync {
            try {
                val total = migrateJournals.size
                var malformed = 0
                var badMtime = 0
                var i = 1
                val colMgr = etebase.collectionManager
                for (itemInfo in migrateJournals.values) {
                    uiThread {
                        progress.setMessage(getString(R.string.migrate_v2_wizard_migrate_progress, i, total))
                    }
                    val colType = when (itemInfo.enumType) {
                        CollectionInfo.Type.ADDRESS_BOOK -> Constants.ETEBASE_TYPE_ADDRESS_BOOK
                        CollectionInfo.Type.CALENDAR -> Constants.ETEBASE_TYPE_CALENDAR
                        CollectionInfo.Type.TASKS -> Constants.ETEBASE_TYPE_TASKS
                    }

                    val colMeta = ItemMetadata()
                    colMeta.name = itemInfo.displayName
                    colMeta.description = itemInfo.description
                    if (itemInfo.color != null) {
                        colMeta.color = String.format("#%06X", 0xFFFFFF and itemInfo.color)
                    }
                    colMeta.mtime = System.currentTimeMillis()
                    val collection = colMgr.create(colType, colMeta, "")
                    colMgr.upload(collection)

                    val migratedItems = HashMap<String, Item>()
                    val journalEntity = JournalModel.Journal.fetch(data, itemInfo.legacyInfo!!.getServiceEntity(data), itemInfo.uid)
                    val entries = data.select(EntryEntity::class.java).where(EntryEntity.JOURNAL.eq(journalEntity)).orderBy(EntryEntity.ID.asc()).get().toList()
                    val itemMgr = colMgr.getItemManager(collection)
                    var itemDone = 0
                    val toPush = LinkedList<Item>()
                    for (entry in entries) {
                        itemDone++

                        val inputReader = StringReader(entry.content.content)
                        val uid: String?
                        var lastModified: Long?
                        when (itemInfo.enumType) {
                            CollectionInfo.Type.ADDRESS_BOOK -> {
                                val contact = Contact.fromReader(inputReader, null)[0]
                                uid = contact.uid
                                lastModified = null
                            }
                            CollectionInfo.Type.CALENDAR -> {
                                val event = Event.eventsFromReader(inputReader)[0]
                                uid = event.uid
                                lastModified = event.lastModified?.dateTime?.time
                            }
                            CollectionInfo.Type.TASKS -> {
                                val task = Task.tasksFromReader(inputReader)[0]
                                uid = task.uid
                                lastModified = task.lastModified
                            }
                        }

                        if (uid == null) {
                            malformed++
                            continue
                        }
                        if (lastModified == null) {
                            // When we can't set mtime, set to the item's position in the change log so we at least maintain EteSync 1.0 ordering.
                            lastModified = System.currentTimeMillis() + itemDone
                            badMtime++
                        }

                        val item: Item
                        if (migratedItems.containsKey(uid)) {
                            val tmp = migratedItems.get(uid)!!
                            // We need to clone the item so we can push multiple versions at once
                            item = itemMgr.cacheLoad(itemMgr.cacheSaveWithContent(tmp))
                            item.setContent(entry.content.content)
                            val meta = item.meta
                            meta.mtime = lastModified
                            item.meta = meta
                        } else {
                            val meta = ItemMetadata()
                            meta.mtime = lastModified
                            meta.name = uid
                            item = itemMgr.create(meta, entry.content.content)
                            migratedItems.set(uid, item)
                        }
                        if (entry.content.isAction(SyncEntry.Actions.DELETE)) {
                            item.delete()
                        }
                        toPush.add(item)

                        if (toPush.size == CHUNK_SIZE) {
                            uiThread {
                                progress.setMessage(getString(R.string.migrate_v2_wizard_migrate_progress, i, total) + "\n" +
                                        getString(R.string.migrate_v2_wizard_migrate_progress_entries, itemDone, entries.size))
                            }
                            itemMgr.batch(toPush.toTypedArray())
                            toPush.clear()
                        }
                    }
                    if (toPush.size > 0) {
                        itemMgr.batch(toPush.toTypedArray())
                    }

                    i++;
                }

                uiThread {
                    var message = getString(R.string.migrate_v2_wizard_migrate_progress_done)
                    if (malformed > 0) {
                        message += "\n\n" + getString(R.string.migrate_v2_wizard_migrate_progress_done_malformed, malformed)
                    }
                    AlertDialog.Builder(requireContext())
                            .setIcon(R.drawable.ic_info_dark)
                            .setTitle(R.string.migrate_v2_wizard_migrate_title)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.yes) { _, _ -> }
                            .setOnDismissListener {
                                requireFragmentManager().beginTransaction()
                                        .replace(android.R.id.content, CreateAccountFragment.newInstance(configurationModel.account.value!!))
                                        .addToBackStack(null)
                                        .commitAllowingStateLoss()
                            }
                            .show()
                }
            } catch (e: Exception) {
                uiThread { reportErrorHelper(requireContext(), e) }
            } finally {
                uiThread {
                    dismissAllowingStateLoss()
                }
            }
        }
    }
}