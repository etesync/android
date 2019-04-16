/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.content.ContentResolver
import android.content.ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.etesync.syncadapter.BuildConfig.DEBUG
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.Constants.serviceUrl
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.setup.LoginActivity
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.ShowcaseBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import tourguide.tourguide.ToolTip

class AccountsActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    private var syncStatusSnackbar: Snackbar? = null
    private var syncStatusObserver: Any? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val fab = findViewById<View>(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { startActivity(Intent(this@AccountsActivity, LoginActivity::class.java)) }

        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.setDrawerListener(toggle)
        toggle.syncState()

        val navigationView = findViewById<View>(R.id.nav_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.itemIconTintList = null

        if (savedInstanceState == null && packageName != callingPackage) {
            val ft = supportFragmentManager.beginTransaction()
            for (fragment in StartupDialogFragment.getStartupDialogs(this))
                ft.add(fragment, null)
            ft.commit()

            if (DEBUG) {
                Toast.makeText(this, "Server: " + serviceUrl.toString(), Toast.LENGTH_SHORT).show()
            }
        }

        PermissionsActivity.requestAllPermissions(this)

        if (!HintManager.getHintSeen(this, HINT_ACCOUNT_ADD)) {
            ShowcaseBuilder.getBuilder(this)
                    .setToolTip(ToolTip().setTitle(getString(R.string.tourguide_title)).setDescription(getString(R.string.accounts_showcase_add)).setGravity(Gravity.TOP or Gravity.LEFT))
                    .playOn(fab)
            HintManager.setHintSeen(this, HINT_ACCOUNT_ADD, true)
        }
    }

    override fun onResume() {
        super.onResume()
        onStatusChanged(SYNC_OBSERVER_TYPE_SETTINGS)
        syncStatusObserver = ContentResolver.addStatusChangeListener(SYNC_OBSERVER_TYPE_SETTINGS, this)
    }

    override fun onPause() {
        super.onPause()
        if (syncStatusObserver != null) {
            ContentResolver.removeStatusChangeListener(syncStatusObserver)
            syncStatusObserver = null
        }
    }

    override fun onStatusChanged(which: Int) {
        if (syncStatusSnackbar != null) {
            syncStatusSnackbar!!.dismiss()
            syncStatusSnackbar = null
        }

        if (!ContentResolver.getMasterSyncAutomatically()) {
            syncStatusSnackbar = Snackbar.make(findViewById(R.id.coordinator), R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable) { ContentResolver.setMasterSyncAutomatically(true) }
            syncStatusSnackbar!!.show()
        }
    }


    override fun onBackPressed() {
        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.nav_app_settings -> startActivity(Intent(this, AppSettingsActivity::class.java))
            R.id.nav_website -> startActivity(Intent(Intent.ACTION_VIEW, Constants.webUri))
            R.id.nav_guide -> WebViewActivity.openUrl(this, Constants.helpUri)
            R.id.nav_faq -> WebViewActivity.openUrl(this, Constants.faqUri)
            R.id.nav_report_issue -> startActivity(Intent(Intent.ACTION_VIEW, Constants.reportIssueUri))
            R.id.nav_contact -> startActivity(Intent(Intent.ACTION_VIEW, Constants.contactUri))
        }

        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    companion object {
        val HINT_ACCOUNT_ADD = "AddAccount"
    }
}
