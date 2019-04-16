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
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.core.app.NavUtils
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalTaskList
import org.apache.commons.lang3.StringUtils
import yuku.ambilwarna.AmbilWarnaDialog

open class CreateCollectionActivity : BaseActivity() {

    protected lateinit var account: Account
    protected lateinit var info: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!
        info = intent.extras!!.getSerializable(EXTRA_COLLECTION_INFO) as CollectionInfo

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.activity_create_collection)

        val displayName = findViewById<View>(R.id.display_name) as EditText
        when (info.type) {
            CollectionInfo.Type.CALENDAR -> {
                setTitle(R.string.create_calendar)
                displayName.setHint(R.string.create_calendar_display_name_hint)

                val colorSquare = findViewById<View>(R.id.color)
                colorSquare.setBackgroundColor(LocalCalendar.defaultColor)
                colorSquare.setOnClickListener {
                    AmbilWarnaDialog(this@CreateCollectionActivity, (colorSquare.background as ColorDrawable).color, true, object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onCancel(dialog: AmbilWarnaDialog) {}

                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            colorSquare.setBackgroundColor(color)
                        }
                    }).show()
                }
            }
            CollectionInfo.Type.TASKS -> {
                setTitle(R.string.create_tasklist)
                displayName.setHint(R.string.create_tasklist_display_name_hint)

                val colorSquare = findViewById<View>(R.id.color)
                colorSquare.setBackgroundColor(LocalTaskList.defaultColor)
                colorSquare.setOnClickListener {
                    AmbilWarnaDialog(this@CreateCollectionActivity, (colorSquare.background as ColorDrawable).color, true, object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onCancel(dialog: AmbilWarnaDialog) {}

                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            colorSquare.setBackgroundColor(color)
                        }
                    }).show()
                }
            }
            CollectionInfo.Type.ADDRESS_BOOK -> {
                setTitle(R.string.create_addressbook)
                displayName.setHint(R.string.create_addressbook_display_name_hint)

                val colorGroup = findViewById<View>(R.id.color_group)
                colorGroup.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_create_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val intent = Intent(this, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            NavUtils.navigateUpTo(this, intent)
            return true
        }
        return false
    }

    fun onCreateCollection(item: MenuItem) {
        var ok = true

        var edit = findViewById<View>(R.id.display_name) as EditText
        info.displayName = edit.text.toString()
        if (TextUtils.isEmpty(info.displayName)) {
            edit.error = getString(R.string.create_collection_display_name_required)
            ok = false
        }

        edit = findViewById<View>(R.id.description) as EditText
        info.description = StringUtils.trimToNull(edit.text.toString())

        if (ok) {
            when (info.type) {
                CollectionInfo.Type.CALENDAR, CollectionInfo.Type.TASKS -> {
                    val view = findViewById<View>(R.id.color)
                    info.color = (view.background as ColorDrawable).color
                }
                CollectionInfo.Type.ADDRESS_BOOK -> {
                }
            }

            info.selected = true

            CreateCollectionFragment.newInstance(account, info).show(supportFragmentManager, null)
        }
    }

    companion object {
        internal val EXTRA_ACCOUNT = "account"
        internal val EXTRA_COLLECTION_INFO = "collectionInfo"

        fun newIntent(context: Context, account: Account, info: CollectionInfo): Intent {
            val intent = Intent(context, CreateCollectionActivity::class.java)
            intent.putExtra(CreateCollectionActivity.EXTRA_ACCOUNT, account)
            intent.putExtra(CreateCollectionActivity.EXTRA_COLLECTION_INFO, info)
            return intent
        }
    }
}
