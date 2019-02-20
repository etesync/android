package com.etesync.syncadapter;

import android.content.Context;
import android.widget.Toast;

import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.data.StringFormat;

import static com.etesync.syncadapter.utils.EventEmailInvitationKt.emailSupportsAttachments;

public class AcraConfiguration {
    public static CoreConfigurationBuilder getConfig(Context context) {
        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(context)
                .setBuildConfigClass(BuildConfig.class)
                .setLogcatArguments("-t", "500", "-v", "time")
                .setReportFormat(StringFormat.JSON);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class)
                .setMailTo("reports@etesync.com")
                .setResSubject(R.string.crash_email_subject)
                .setReportFileName("ACRA-report.stacktrace.json")
                .setReportAsFile(emailSupportsAttachments(context))
                .setEnabled(true);
        builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
                .setResText(R.string.crash_message)
                .setLength(Toast.LENGTH_SHORT)
                .setEnabled(true);

        return builder;
    }
}
