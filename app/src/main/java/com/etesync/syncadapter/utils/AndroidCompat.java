package com.etesync.syncadapter.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Build;

public class AndroidCompat {
    public static void removeAccount (AccountManager accountManager, Account account) {
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP_MR1) {
            accountManager.removeAccountExplicitly(account);
        } else {
            accountManager.removeAccount(account, null, null);
        }
    }
}
