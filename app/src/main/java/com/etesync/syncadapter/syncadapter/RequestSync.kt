package com.etesync.syncadapter.syncadapter

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import com.etesync.syncadapter.App
import com.etesync.syncadapter.utils.TaskProviderHandling


fun requestSync(context: Context, account: Account?) {
    val authorities = arrayOf(
            App.addressBooksAuthority,
            CalendarContract.AUTHORITY,
            TaskProviderHandling.getWantedTaskSyncProvider(context)?.authority
    )

    for (authority in authorities.filterNotNull()) {
        val extras = Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
        ContentResolver.requestSync(account, authority, extras)
    }
}