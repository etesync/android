package com.etesync.syncadapter.ui.etebase

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.BaseActivity

class InvitationsActivity : BaseActivity() {
    private lateinit var account: Account
    private val model: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!

        setContentView(R.layout.etebase_fragment_activity)

        if (savedInstanceState == null) {
            model.loadAccount(this, account)
            title = getString(R.string.invitations_title)
            supportFragmentManager.commit {
                replace(R.id.fragment_container, InvitationsListFragment())
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private val EXTRA_ACCOUNT = "account"

        fun newIntent(context: Context, account: Account): Intent {
            val intent = Intent(context, InvitationsActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            return intent
        }
    }
}