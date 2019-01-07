package com.etesync.syncadapter;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

import org.acra.annotation.AcraCore;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.data.StringFormat;

public class AcraConfiguration {
    private static Boolean shouldReportAsFile(Context context) {
        Boolean shouldReport = false;

        try {
            context.getPackageManager().getPackageInfo("ch.protonmail.android", 0);
        } catch (PackageManager.NameNotFoundException e) {
            shouldReport = true;
        }

        System.out.println("ACRA with attached files: " + shouldReport.toString());
        return shouldReport;
    }

    public static CoreConfigurationBuilder getConfig(Context context) {
        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(context)
                .setBuildConfigClass(BuildConfig.class)
                .setLogcatArguments("-t", "500", "-v", "time")
                .setReportFormat(StringFormat.JSON);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class)
                .setMailTo("reports@etesync.com")
                .setResSubject(R.string.crash_email_subject)
                .setReportFileName("ACRA-report.stacktrace.json")
                .setReportAsFile(shouldReportAsFile(context))
                .setEnabled(true);
        builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
                .setResText(R.string.crash_message)
                .setLength(Toast.LENGTH_SHORT)
                .setEnabled(true);

        return builder;
    }
}
