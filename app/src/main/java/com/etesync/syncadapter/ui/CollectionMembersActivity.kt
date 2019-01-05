package com.etesync.syncadapter.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.etesync.syncadapter.App
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import com.etesync.syncadapter.resource.LocalCalendar

class CollectionMembersActivity : BaseActivity(), Refreshable {

    private lateinit var account: Account
    private var journalEntity: JournalEntity? = null
    private var listFragment: CollectionMembersListFragment? = null
    protected lateinit var info: CollectionInfo

    override fun refresh() {
        val data = (applicationContext as App).data

        journalEntity = JournalEntity.fetch(data, info.getServiceEntity(data), info.uid)
        if (journalEntity == null || journalEntity!!.isDeleted) {
            finish()
            return
        }

        info = journalEntity!!.info

        setTitle(R.string.collection_members_title)

        val colorSquare = findViewById<View>(R.id.color)
        if (info.type == CollectionInfo.Type.CALENDAR) {
            colorSquare.setBackgroundColor(info.color ?: LocalCalendar.defaultColor)
        } else {
            colorSquare.visibility = View.GONE
        }
        findViewById<View>(R.id.progressBar).visibility = View.GONE

        val title = findViewById<View>(R.id.display_name) as TextView
        title.text = info.displayName

        val desc = findViewById<View>(R.id.description) as TextView
        desc.text = info.description

        if (listFragment != null) {
            listFragment!!.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.view_collection_members)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)
        info = intent.extras!!.getSerializable(EXTRA_COLLECTION_INFO) as CollectionInfo

        refresh()

        // We refresh before this, so we don't refresh the list before it was fully created.
        if (savedInstanceState == null) {
            listFragment = CollectionMembersListFragment.newInstance(account, info)
            supportFragmentManager.beginTransaction()
                    .add(R.id.list_entries_container, listFragment)
                    .commit()
        }
    }

    fun onAddMemberClicked(v: View) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.collection_members_add)
                .setIcon(R.drawable.ic_account_add_dark)
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    val frag = AddMemberFragment.newInstance(account, info, input.text.toString())
                    frag.show(supportFragmentManager, null)
                }
                .setNegativeButton(android.R.string.no) { dialog, which -> }
        dialog.setView(input)
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    companion object {
        val EXTRA_ACCOUNT = "account"
        val EXTRA_COLLECTION_INFO = "collectionInfo"

        fun newIntent(context: Context, account: Account, info: CollectionInfo): Intent {
            val intent = Intent(context, CollectionMembersActivity::class.java)
            intent.putExtra(CollectionMembersActivity.EXTRA_ACCOUNT, account)
            intent.putExtra(CollectionMembersActivity.EXTRA_COLLECTION_INFO, info)
            return intent
        }
    }
}
