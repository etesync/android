package com.etesync.syncadapter;

import android.content.Context;

import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

public class AcraConfiguration {
    public static CoreConfigurationBuilder getConfig(Context context) {
        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(context)
                .setBuildConfigClass(BuildConfig.class)
                .setLogcatArguments("-t", "500", "-v", "time")
                .setReportFormat(StringFormat.JSON);
        builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder.class)
                .setUri(Constants.crashReportingUrl)
                .setHttpMethod(HttpSender.Method.POST)
                .setEnabled(true);
        builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                .setResTitle(R.string.crash_title)
                .setResText(R.string.crash_message)
                .setResCommentPrompt(R.string.crash_email_body)
                .setEnabled(true);

        return builder;
    }
}
