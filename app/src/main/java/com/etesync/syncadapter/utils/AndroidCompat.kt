package com.etesync.syncadapter.utils

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Build

object AndroidCompat {
    fun removeAccount(accountManager: AccountManager, account: Account) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            accountManager.removeAccountExplicitly(account)
        } else {
            accountManager.removeAccount(account, null, null)
        }
    }
}
