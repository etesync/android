package com.etesync.syncadapter.ui.importlocal

import android.accounts.Account
import android.app.ProgressDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalContact
import com.etesync.syncadapter.resource.LocalGroup
import java.util.*
import kotlin.collections.HashMap

class LocalContactImportFragment : Fragment() {

    private lateinit var account: Account
    private lateinit var info: CollectionInfo
    private var recyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        account = arguments!!.getParcelable(KEY_ACCOUNT)
        info = arguments!!.getSerializable(KEY_COLLECTION_INFO) as CollectionInfo
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_local_contact_import, container, false)

        recyclerView = view.findViewById<View>(R.id.recyclerView) as RecyclerView

        recyclerView!!.layoutManager = LinearLayoutManager(activity)
        recyclerView!!.addItemDecoration(DividerItemDecoration(activity!!))
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importAccount()
    }

    protected fun importAccount() {
        val provider = context!!.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)
        val cursor: Cursor?
        try {
            cursor = provider!!.query(ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE), null, null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " ASC, " + ContactsContract.RawContacts.ACCOUNT_TYPE)
        } catch (except: Exception) {
            Log.w(TAG, "Addressbook provider is missing columns, continuing anyway")

            except.printStackTrace()
            return
        }

        val localAddressBooks = ArrayList<LocalAddressBook>()
        var account: Account? = null
        val accountNameIndex = cursor!!.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
        val accountTypeIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
        while (cursor.moveToNext()) {
            val accountName = cursor.getString(accountNameIndex)
            val accountType = cursor.getString(accountTypeIndex)
            if (account == null || !(account.name == accountName && account.type == accountType)) {
                if (accountName != null && accountType != null) {
                    account = Account(accountName, accountType)
                    localAddressBooks.add(LocalAddressBook(context!!, account, provider))
                }
            }
        }

        cursor.close()
        provider.release()

        recyclerView!!.adapter = ImportContactAdapter(context!!, localAddressBooks, object : OnAccountSelected {
            override fun accountSelected(index: Int) {
                ImportContacts().execute(localAddressBooks[index])
            }
        })
    }

    protected inner class ImportContacts : AsyncTask<LocalAddressBook, Int, ResultFragment.ImportResult>() {
        private lateinit var progressDialog: ProgressDialog

        override fun onPreExecute() {
            progressDialog = ProgressDialog(activity)
            progressDialog.setTitle(R.string.import_dialog_title)
            progressDialog.setMessage(getString(R.string.import_dialog_adding_entries))
            progressDialog.setCanceledOnTouchOutside(false)
            progressDialog.setCancelable(false)
            progressDialog.isIndeterminate = false
            progressDialog.setIcon(R.drawable.ic_import_export_black)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.show()
        }

        override fun doInBackground(vararg addressBooks: LocalAddressBook): ResultFragment.ImportResult {
            return importContacts(addressBooks[0])
        }

        override fun onProgressUpdate(vararg values: Int?) {
            progressDialog.progress = values[0]!!
        }

        override fun onPostExecute(result: ResultFragment.ImportResult) {
            if (progressDialog.isShowing && !activity!!.isDestroyed) {
                progressDialog.dismiss()
            }
            (activity as ResultFragment.OnImportCallback).onImportResult(result)
        }

        private fun importContacts(localAddressBook: LocalAddressBook): ResultFragment.ImportResult {
            val result = ResultFragment.ImportResult()
            try {
                val addressBook = LocalAddressBook.findByUid(context!!,
                        context!!.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)!!,
                        account, info.uid!!)!!
                val localContacts = localAddressBook.findAllContacts()
                val localGroups = localAddressBook.findAllGroups()
                val oldIdToNewId = HashMap<Long, Long>()
                val total = localContacts.size + localGroups.size
                progressDialog.max = total
                result.total = total.toLong()
                var progress = 0
                for (currentLocalContact in localContacts) {
                    val contact = currentLocalContact.contact

                    try {
                        val localContact = LocalContact(addressBook, contact!!, null, null)
                        localContact.createAsDirty()
                        oldIdToNewId[currentLocalContact.id!!] = localContact.id!!
                        result.added++
                    } catch (e: ContactsStorageException) {
                        e.printStackTrace()
                        result.e = e
                    }

                    publishProgress(++progress)
                }
                for (currentLocalGroup in localGroups) {
                    val group = currentLocalGroup.contact

                    try {
                        val localGroup = LocalGroup(addressBook, group!!, null, null)
                        val members = currentLocalGroup.getMembers().map { it ->
                            oldIdToNewId[it]!!
                        }

                        localGroup.createAsDirty(members)
                        result.added++
                    } catch (e: ContactsStorageException) {
                        e.printStackTrace()
                        result.e = e
                    }

                    publishProgress(++progress)
                }
            } catch (e: Exception) {
                result.e = e
            }

            return result
        }
    }

    class ImportContactAdapter
    /**
     * Initialize the dataset of the Adapter.
     *
     * @param addressBooks containing the data to populate views to be used by RecyclerView.
     */
    (context: Context, private val mAddressBooks: List<LocalAddressBook>, private val mOnAccountSelected: OnAccountSelected) : RecyclerView.Adapter<ImportContactAdapter.ViewHolder>() {
        private val accountResolver: AccountResolver

        /**
         * Provide a reference to the type of views that you are using (custom ViewHolder)
         */
        class ViewHolder(v: View, onAccountSelected: OnAccountSelected) : RecyclerView.ViewHolder(v) {
            internal val titleTextView: TextView
            internal val descTextView: TextView
            internal val iconImageView: ImageView

            init {
                // Define click listener for the ViewHolder's View.
                v.setOnClickListener { onAccountSelected.accountSelected(adapterPosition) }
                titleTextView = v.findViewById<View>(R.id.title) as TextView
                descTextView = v.findViewById<View>(R.id.description) as TextView
                iconImageView = v.findViewById<View>(R.id.icon) as ImageView
            }
        }

        init {
            accountResolver = AccountResolver(context)
        }

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view.
            val v = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.import_content_list_account, viewGroup, false)

            return ViewHolder(v, mOnAccountSelected)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.titleTextView.text = mAddressBooks[position].account.name
            val accountInfo = accountResolver.resolve(mAddressBooks[position].account.type)
            viewHolder.descTextView.text = accountInfo.name
            viewHolder.iconImageView.setImageDrawable(accountInfo.icon)
        }

        override fun getItemCount(): Int {
            return mAddressBooks.size
        }

        companion object {
            private val TAG = "ImportContactAdapter"
        }
    }

    interface OnAccountSelected {
        fun accountSelected(index: Int)
    }

    class DividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

        private val mDivider: Drawable?

        init {
            val a = context.obtainStyledAttributes(ATTRS)
            mDivider = a.getDrawable(0)
            a.recycle()
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            drawVertical(c, parent)
        }

        fun drawVertical(c: Canvas, parent: RecyclerView) {
            val left = parent.paddingLeft
            val right = parent.width - parent.paddingRight

            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val params = child
                        .layoutParams as RecyclerView.LayoutParams
                val top = child.bottom + params.bottomMargin
                val bottom = top + mDivider!!.intrinsicHeight
                mDivider.setBounds(left, top, right, bottom)
                mDivider.draw(c)
            }
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.set(0, 0, 0, mDivider!!.intrinsicHeight)
        }

        companion object {

            private val ATTRS = intArrayOf(android.R.attr.listDivider)
        }
    }

    companion object {

        fun newInstance(account: Account, info: CollectionInfo): LocalContactImportFragment {
            val frag = LocalContactImportFragment()
            val args = Bundle(1)
            args.putParcelable(KEY_ACCOUNT, account)
            args.putSerializable(KEY_COLLECTION_INFO, info)
            frag.arguments = args

            return frag
        }
    }
}
