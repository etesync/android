package com.etesync.syncadapter;

import android.content.Context;

import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.NotificationConfigurationBuilder;
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
                .setResBody(R.string.crash_email_body)
                .setReportFileName("ACRA-report.stacktrace.json")
                .setReportAsFile(emailSupportsAttachments(context))
                .setEnabled(true);
        builder.getPluginConfigurationBuilder(NotificationConfigurationBuilder.class)
                .setResTitle(R.string.crash_title)
                .setResText(R.string.crash_message)
                .setResChannelName(R.string.notification_channel_crash_reports)
                .setSendOnClick(true)
                .setEnabled(true);

        return builder;
    }
}
