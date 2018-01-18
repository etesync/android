/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.etesync.syncadapter.BuildConfig;
import com.etesync.syncadapter.Constants;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.utils.HintManager;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StartupDialogFragment extends DialogFragment {
    private static final String
            HINT_BATTERY_OPTIMIZATIONS = "BatteryOptimizations",
            HINT_VENDOR_SPECIFIC_BUGS = "VendorSpecificBugs";

    private static final String ARGS_MODE = "mode";

    enum Mode {
        BATTERY_OPTIMIZATIONS,
        DEVELOPMENT_VERSION,
        GOOGLE_PLAY_ACCOUNTS_REMOVED,
        VENDOR_SPECIFIC_BUGS,
    }

    public static StartupDialogFragment[] getStartupDialogs(Context context) {
        List<StartupDialogFragment> dialogs = new LinkedList<>();

        if (BuildConfig.VERSION_NAME.contains("-alpha") || BuildConfig.VERSION_NAME.contains("-beta") || BuildConfig.VERSION_NAME.contains("-rc"))
            dialogs.add(StartupDialogFragment.instantiate(Mode.DEVELOPMENT_VERSION));

        // battery optimization whitelisting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !HintManager.getHintSeen(context, HINT_BATTERY_OPTIMIZATIONS)) {
            PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                dialogs.add(StartupDialogFragment.instantiate(Mode.BATTERY_OPTIMIZATIONS));
        }

        // Vendor specific bugs
        String manu = Build.MANUFACTURER;
        if (!HintManager.getHintSeen(context, HINT_BATTERY_OPTIMIZATIONS) && (manu.equalsIgnoreCase("Xiaomi") || manu.equalsIgnoreCase("Huawei")) && !Build.DISPLAY.contains("lineage")) {
            dialogs.add(StartupDialogFragment.instantiate(Mode.VENDOR_SPECIFIC_BUGS));
        }

        Collections.reverse(dialogs);
        return dialogs.toArray(new StartupDialogFragment[dialogs.size()]);
    }

    public static StartupDialogFragment instantiate(Mode mode) {
        StartupDialogFragment frag = new StartupDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(ARGS_MODE, mode.name());
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    @TargetApi(Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        Mode mode = Mode.valueOf(getArguments().getString(ARGS_MODE));
        switch (mode) {
            case BATTERY_OPTIMIZATIONS:
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.startup_battery_optimization)
                        .setMessage(R.string.startup_battery_optimization_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_battery_optimization_disable, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                                if (intent.resolveActivity(getContext().getPackageManager()) != null)
                                    getContext().startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                HintManager.setHintSeen(getContext(), HINT_BATTERY_OPTIMIZATIONS, true);
                            }
                        })
                        .create();

            case DEVELOPMENT_VERSION:
                return new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.startup_development_version)
                        .setMessage(R.string.startup_development_version_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_development_version_give_feedback, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Constants.feedbackUri));
                            }
                        })
                        .create();
            case VENDOR_SPECIFIC_BUGS:
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.startup_vendor_specific_bugs)
                        .setMessage(R.string.startup_vendor_specific_bugs_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_vendor_specific_bugs_open_faq, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                WebViewActivity.openUrl(getContext(), Constants.faqUri.buildUpon().encodedFragment("vendor-issues").build());
                            }
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                HintManager.setHintSeen(getContext(), HINT_VENDOR_SPECIFIC_BUGS, true);
                            }
                        })
                        .create();
        }

        throw new IllegalArgumentException(/* illegal mode argument */);
    }

    private static String installedFrom(Context context) {
        try {
            return context.getPackageManager().getInstallerPackageName(context.getPackageName());
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

}
