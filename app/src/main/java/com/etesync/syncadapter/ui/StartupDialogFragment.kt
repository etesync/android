/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.etesync.syncadapter.BuildConfig
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.utils.HintManager
import java.util.*

class StartupDialogFragment : DialogFragment() {

    enum class Mode {
        BATTERY_OPTIMIZATIONS,
        DEVELOPMENT_VERSION,
        GOOGLE_PLAY_ACCOUNTS_REMOVED,
        VENDOR_SPECIFIC_BUGS
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val mode = Mode.valueOf(arguments!!.getString(ARGS_MODE)!!)
        when (mode) {
            StartupDialogFragment.Mode.BATTERY_OPTIMIZATIONS -> return AlertDialog.Builder(activity!!)
                    .setTitle(R.string.startup_battery_optimization)
                    .setMessage(R.string.startup_battery_optimization_message)
                    .setPositiveButton(android.R.string.ok) { dialog, which -> }
                    .setNeutralButton(R.string.startup_battery_optimization_disable) { dialog, which ->
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                        if (intent.resolveActivity(context!!.packageManager) != null)
                            context!!.startActivity(intent)
                    }
                    .setNegativeButton(R.string.startup_dont_show_again) { dialog, which -> HintManager.setHintSeen(context!!, HINT_BATTERY_OPTIMIZATIONS, true) }
                    .create()

            StartupDialogFragment.Mode.DEVELOPMENT_VERSION -> return AlertDialog.Builder(activity!!)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(R.string.startup_development_version)
                    .setMessage(R.string.startup_development_version_message)
                    .setPositiveButton(android.R.string.ok) { dialog, which -> }
                    .setNeutralButton(R.string.startup_development_version_give_feedback) { dialog, which -> startActivity(Intent(Intent.ACTION_VIEW, Constants.feedbackUri)) }
                    .create()
            StartupDialogFragment.Mode.VENDOR_SPECIFIC_BUGS -> return AlertDialog.Builder(activity!!)
                    .setTitle(R.string.startup_vendor_specific_bugs)
                    .setMessage(R.string.startup_vendor_specific_bugs_message)
                    .setPositiveButton(android.R.string.ok) { dialog, which -> }
                    .setNeutralButton(R.string.startup_vendor_specific_bugs_open_faq) { dialog, which -> WebViewActivity.openUrl(context!!, Constants.faqUri.buildUpon().encodedFragment("vendor-issues").build()) }
                    .setNegativeButton(R.string.startup_dont_show_again) { dialog, which -> HintManager.setHintSeen(context!!, HINT_VENDOR_SPECIFIC_BUGS, true) }
                    .create()
        }

        throw IllegalArgumentException(/* illegal mode argument */)
    }

    companion object {
        private val HINT_BATTERY_OPTIMIZATIONS = "BatteryOptimizations"
        private val HINT_VENDOR_SPECIFIC_BUGS = "VendorSpecificBugs"

        private val ARGS_MODE = "mode"

        fun getStartupDialogs(context: Context): Array<StartupDialogFragment> {
            val dialogs = LinkedList<StartupDialogFragment>()

            if (BuildConfig.VERSION_NAME.contains("-alpha") || BuildConfig.VERSION_NAME.contains("-beta") || BuildConfig.VERSION_NAME.contains("-rc"))
                dialogs.add(StartupDialogFragment.instantiate(Mode.DEVELOPMENT_VERSION))

            // battery optimization whitelisting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !HintManager.getHintSeen(context, HINT_BATTERY_OPTIMIZATIONS)) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                    dialogs.add(StartupDialogFragment.instantiate(Mode.BATTERY_OPTIMIZATIONS))
            }

            // Vendor specific bugs
            val manu = Build.MANUFACTURER
            if (!HintManager.getHintSeen(context, HINT_BATTERY_OPTIMIZATIONS) && (manu.equals("Xiaomi", ignoreCase = true) || manu.equals("Huawei", ignoreCase = true)) && !Build.DISPLAY.contains("lineage")) {
                dialogs.add(StartupDialogFragment.instantiate(Mode.VENDOR_SPECIFIC_BUGS))
            }

            Collections.reverse(dialogs)
            return dialogs.toTypedArray()
        }

        fun instantiate(mode: Mode): StartupDialogFragment {
            val frag = StartupDialogFragment()
            val args = Bundle(1)
            args.putString(ARGS_MODE, mode.name)
            frag.arguments = args
            return frag
        }

        private fun installedFrom(context: Context): String? {
            try {
                return context.packageManager.getInstallerPackageName(context.packageName)
            } catch (e: IllegalArgumentException) {
                return null
            }

        }
    }

}
