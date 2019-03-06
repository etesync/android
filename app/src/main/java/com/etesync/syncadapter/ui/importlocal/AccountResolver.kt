package com.etesync.syncadapter.ui.importlocal

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.etesync.syncadapter.App
import com.etesync.syncadapter.R
import java.util.*

internal class AccountResolver(private val context: Context) {
    private val cache: HashMap<String, AccountInfo>

    init {
        this.cache = LinkedHashMap()
    }

    fun resolve(accountName: String): AccountInfo {
        var accountName = accountName
        // Hardcoded swaps for known accounts:
        if (accountName == "com.google") {
            accountName = "com.google.android.googlequicksearchbox"
        } else if (accountName == App.addressBookAccountType) {
            accountName = App.accountType
        } else if (accountName == "at.bitfire.davdroid.address_book") {
            accountName = "at.bitfire.davdroid"
        }

        var ret: AccountInfo? = cache[accountName]
        if (ret == null) {
            try {
                val packageManager = context.packageManager
                val applicationInfo = packageManager.getApplicationInfo(accountName, 0)
                val name = if (applicationInfo != null) packageManager.getApplicationLabel(applicationInfo).toString() else accountName
                val icon = context.packageManager.getApplicationIcon(accountName)
                ret = AccountInfo(name, icon)
            } catch (e: PackageManager.NameNotFoundException) {
                ret = AccountInfo(accountName, ContextCompat.getDrawable(context, R.drawable.ic_account_dark)!!)
            }

            cache[accountName] = ret!!
        }

        return ret
    }

    class AccountInfo internal constructor(internal val name: String, internal val icon: Drawable)
}
