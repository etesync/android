package com.etesync.syncadapter.utils

import android.content.Context
import android.content.pm.PackageManager


fun packageInstalled(context: Context, packageName: String): Boolean {
    try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }

    return true
}