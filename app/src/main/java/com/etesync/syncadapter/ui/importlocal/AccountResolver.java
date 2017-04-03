package com.etesync.syncadapter.ui.importlocal;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;

import com.etesync.syncadapter.R;

import java.util.HashMap;
import java.util.LinkedHashMap;

class AccountResolver {
    private Context context;
    private HashMap<String, AccountInfo> cache;

    public AccountResolver(Context context) {
        this.context = context;
        this.cache = new LinkedHashMap<>();
    }

    public AccountInfo resolve(String accountName) {
        // Hardcoded swaps for known accounts:
        if (accountName.equals("com.google")) {
            accountName = "com.google.android.googlequicksearchbox";
        }
        AccountInfo ret = cache.get(accountName);
        if (ret == null) {
            try {
                PackageManager packageManager = context.getPackageManager();
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(accountName, 0);
                String name = (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo).toString() : accountName);
                Drawable icon = context.getPackageManager().getApplicationIcon(accountName);
                ret = new AccountInfo(name, icon);
            } catch (PackageManager.NameNotFoundException e) {
                ret = new AccountInfo(accountName, ContextCompat.getDrawable(context, R.drawable.ic_account_dark));
            }
            cache.put(accountName, ret);
        }

        return ret;
    }

    public static class AccountInfo {
        final String name;
        final Drawable icon;

        AccountInfo(String name, Drawable icon) {
            this.name = name;
            this.icon = icon;
        }
    }
}
