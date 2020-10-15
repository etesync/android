/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter;

import android.net.Uri;

import static com.etesync.syncadapter.BuildConfig.DEBUG_REMOTE_URL;

public class Constants {

    // notification IDs
    public final static int
            NOTIFICATION_ACCOUNT_SETTINGS_UPDATED = 0,
            NOTIFICATION_EXTERNAL_FILE_LOGGING = 1,
            NOTIFICATION_REFRESH_COLLECTIONS = 2,
            NOTIFICATION_CONTACTS_SYNC = 10,
            NOTIFICATION_CALENDAR_SYNC = 11,
            NOTIFICATION_TASK_SYNC = 12,
            NOTIFICATION_ACCOUNT_UPDATE = 13,
            NOTIFICATION_PERMISSIONS = 20;

    public static final Uri webUri = Uri.parse((DEBUG_REMOTE_URL == null) ? "https://www.etesync.com/" : DEBUG_REMOTE_URL);
    public static final Uri contactUri = webUri.buildUpon().appendEncodedPath("about/#contact").build();
    public static final Uri registrationUrl = webUri.buildUpon().appendEncodedPath("accounts/signup/").build();
    public static final Uri reportIssueUri = Uri.parse("https://github.com/etesync/android/issues");
    public static final Uri feedbackUri = reportIssueUri;
    public static final Uri dashboard = webUri.buildUpon().appendEncodedPath("dashboard/").build();
    public static final Uri faqUri = webUri.buildUpon().appendEncodedPath("faq/").build();
    public static final Uri helpUri = webUri.buildUpon().appendEncodedPath("user-guide/android/").build();
    public static final Uri forgotPassword = faqUri.buildUpon().fragment("forgot-password").build();

    public static final Uri serviceUrl = Uri.parse((DEBUG_REMOTE_URL == null) ? "https://api.etesync.com/" : DEBUG_REMOTE_URL);
    public static final String etebaseServiceUrl = (DEBUG_REMOTE_URL == null) ? "https://api.etebase.com/partner/etesync/" : DEBUG_REMOTE_URL;

    public static final String PRODID_BASE = "-//EteSync//" + BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME;

    public static final int DEFAULT_SYNC_INTERVAL = 4 * 3600;  // 4 hours
    public static final long DEFAULT_RETRY_DELAY = 30 * 60; // 30 minutes

    public final static String KEY_ACCOUNT = "account",
            KEY_COLLECTION_INFO = "collectionInfo";

    public final static String ETEBASE_TYPE_ADDRESS_BOOK = "etebase.vcard";
    public final static String ETEBASE_TYPE_CALENDAR = "etebase.vevent";
    public final static String ETEBASE_TYPE_TASKS = "etebase.vtodo";
    public final static String[] COLLECTION_TYPES = new String[] {
      ETEBASE_TYPE_ADDRESS_BOOK,
      ETEBASE_TYPE_CALENDAR,
      ETEBASE_TYPE_TASKS
    };
}
