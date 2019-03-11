package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.App


fun requestSync(account: Account?) {
    val authorities = arrayOf(App.addressBooksAuthority, CalendarContract.AUTHORITY, TaskProvider.ProviderName.OpenTasks.authority)

    for (authority in authorities) {
        val extras = Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
        ContentResolver.requestSync(account, authority, extras)
    }
}