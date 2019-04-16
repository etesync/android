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
import android.accounts.OnAccountsUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.ListFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import com.etesync.syncadapter.App
import com.etesync.syncadapter.R

class AccountListFragment : ListFragment(), LoaderManager.LoaderCallbacks<Array<Account>>, AdapterView.OnItemClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        listAdapter = AccountListAdapter(context!!)

        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderManager.initLoader(0, arguments, this)

        val list = listView
        list.onItemClickListener = this
        list.choiceMode = AbsListView.CHOICE_MODE_SINGLE
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val account = listAdapter.getItem(position) as Account

        val intent = Intent(context, AccountActivity::class.java)
        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
        startActivity(intent)
    }


    // loader

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Array<Account>> {
        return AccountLoader(context!!)
    }

    override fun onLoadFinished(loader: Loader<Array<Account>>, accounts: Array<Account>) {
        val adapter = listAdapter as AccountListAdapter
        adapter.clear()
        adapter.addAll(*accounts)
    }

    override fun onLoaderReset(loader: Loader<Array<Account>>) {
        (listAdapter as AccountListAdapter).clear()
    }

    private class AccountLoader(context: Context) : AsyncTaskLoader<Array<Account>>(context), OnAccountsUpdateListener {
        private val accountManager = AccountManager.get(context)

        override fun onStartLoading() =
                accountManager.addOnAccountsUpdatedListener(this, null, true)

        override fun onStopLoading() =
                accountManager.removeOnAccountsUpdatedListener(this)

        override fun onAccountsUpdated(accounts: Array<Account>) {
            forceLoad()
        }

        @SuppressLint("MissingPermission")
        override fun loadInBackground(): Array<Account>? {
            return accountManager.getAccountsByType(App.accountType)
        }
    }


    // list adapter

    internal class AccountListAdapter(context: Context) : ArrayAdapter<Account>(context, R.layout.account_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false)

            val account = getItem(position)

            val tv = v!!.findViewById<View>(R.id.account_name) as TextView
            tv.text = account!!.name

            return v
        }
    }

}
