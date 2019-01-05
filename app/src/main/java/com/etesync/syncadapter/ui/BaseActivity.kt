package com.etesync.syncadapter.ui

import android.support.v7.app.AppCompatActivity
import android.view.MenuItem

import com.etesync.syncadapter.App

open class BaseActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!supportFragmentManager.popBackStackImmediate()) {
                finish()
            }
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()

        val app = applicationContext as App
        val certManager = app.certManager
        if (certManager != null)
            certManager.appInForeground = true
    }

    override fun onPause() {
        super.onPause()

        val app = applicationContext as App
        val certManager = app.certManager
        if (certManager != null)
            certManager.appInForeground = false
    }
}