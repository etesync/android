package com.etesync.syncadapter.ui

import androidx.appcompat.app.AppCompatActivity
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
}