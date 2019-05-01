package com.etesync.syncadapter.ui.importlocal

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.ui.BaseActivity

class ImportActivity : BaseActivity(), SelectImportMethod, ResultFragment.OnImportCallback, DialogInterface {

    private lateinit var account: Account
    protected lateinit var info: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        title = getString(R.string.import_dialog_title)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!
        info = intent.extras!!.getSerializable(EXTRA_COLLECTION_INFO) as CollectionInfo

        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, ImportActivity.SelectImportFragment())
                    .commit()
    }

    override fun importFile() {
        supportFragmentManager.beginTransaction()
                .add(ImportFragment.newInstance(account, info), null)
                .commit()

    }

    override fun importAccount() {
        if (info.type == CollectionInfo.Type.CALENDAR) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content,
                            LocalCalendarImportFragment.newInstance(account, info))
                    .addToBackStack(LocalCalendarImportFragment::class.java.name)
                    .commit()
        } else if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content,
                            LocalContactImportFragment.newInstance(account, info))
                    .addToBackStack(LocalContactImportFragment::class.java.name)
                    .commit()
        }
        title = getString(R.string.import_select_account)
    }

    private fun popBackStack() {
        if (!supportFragmentManager.popBackStackImmediate()) {
            finish()
        } else {
            title = getString(R.string.import_dialog_title)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            popBackStack()
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            popBackStack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onImportResult(importResult: ResultFragment.ImportResult) {
        val fragment = ResultFragment.newInstance(importResult)
        supportFragmentManager.beginTransaction()
                .add(fragment, "importResult")
                .commitAllowingStateLoss()
    }

    override fun cancel() {
        finish()
    }

    override fun dismiss() {
        finish()
    }


    class SelectImportFragment : Fragment() {

        private var mSelectImportMethod: SelectImportMethod? = null

        override fun onAttach(context: Context?) {
            super.onAttach(context)
            // This makes sure that the container activity has implemented
            // the callback interface. If not, it throws an exception
            try {
                mSelectImportMethod = activity as SelectImportMethod?
            } catch (e: ClassCastException) {
                throw ClassCastException(activity!!.toString() + " must implement MyInterface ")
            }

        }

        override fun onAttach(activity: Activity?) {
            super.onAttach(activity)
            // This makes sure that the container activity has implemented
            // the callback interface. If not, it throws an exception
            try {
                mSelectImportMethod = activity as SelectImportMethod?
            } catch (e: ClassCastException) {
                throw ClassCastException(activity!!.toString() + " must implement MyInterface ")
            }

        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater.inflate(R.layout.import_actions_list, container, false)

            var card = v.findViewById<View>(R.id.import_file)
            var img = card.findViewById<View>(R.id.action_icon) as ImageView
            var text = card.findViewById<View>(R.id.action_text) as TextView
            img.setImageResource(R.drawable.ic_file_white)
            text.setText(R.string.import_button_file)
            card.setOnClickListener { mSelectImportMethod!!.importFile() }

            card = v.findViewById(R.id.import_account)
            img = card.findViewById<View>(R.id.action_icon) as ImageView
            text = card.findViewById<View>(R.id.action_text) as TextView
            img.setImageResource(R.drawable.ic_account_circle_white)
            text.setText(R.string.import_button_local)
            card.setOnClickListener { mSelectImportMethod!!.importAccount() }

            if ((activity as ImportActivity).info.type == CollectionInfo.Type.TASKS) {
                card.visibility = View.GONE
            }

            return v
        }
    }

    companion object {
        val EXTRA_ACCOUNT = "account"
        val EXTRA_COLLECTION_INFO = "collectionInfo"

        fun newIntent(context: Context, account: Account, info: CollectionInfo): Intent {
            val intent = Intent(context, ImportActivity::class.java)
            intent.putExtra(ImportActivity.EXTRA_ACCOUNT, account)
            intent.putExtra(ImportActivity.EXTRA_COLLECTION_INFO, info)
            return intent
        }
    }
}
