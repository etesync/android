/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.ui.setup;

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.journalmanager.Exceptions;
import com.etesync.syncadapter.journalmanager.JournalAuthenticator;
import com.etesync.syncadapter.log.StringHandler;
import com.etesync.syncadapter.model.CollectionInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class BaseConfigurationFinder {
    protected final Context context;
    protected final LoginCredentials credentials;

    protected final Logger log;
    protected final StringHandler logBuffer = new StringHandler();
    protected OkHttpClient httpClient;

    public BaseConfigurationFinder(@NonNull Context context, @NonNull LoginCredentials credentials) {
		this.context = context;
        this.credentials = credentials;

        log = Logger.getLogger("syncadapter.BaseConfigurationFinder");
        log.setLevel(Level.FINEST);
        log.addHandler(logBuffer);

        httpClient = HttpClient.create(context, log);
	}


    public Configuration findInitialConfiguration() {
        boolean failed = false;
        Configuration.ServiceInfo
                cardDavConfig = findInitialConfiguration(CollectionInfo.Type.ADDRESS_BOOK),
                calDavConfig = findInitialConfiguration(CollectionInfo.Type.CALENDAR);

        JournalAuthenticator authenticator = new JournalAuthenticator(httpClient, HttpUrl.get(credentials.uri));

        String authtoken = null;
        try {
            authtoken = authenticator.getAuthToken(credentials.userName, credentials.password);
        } catch (Exceptions.HttpException|IOException e) {
            log.warning(e.getMessage());

            failed = true;
        }

        return new Configuration(
                credentials.uri,
                credentials.userName, authtoken,
                cardDavConfig, calDavConfig,
                logBuffer.toString(), failed
        );
    }

    protected Configuration.ServiceInfo findInitialConfiguration(@NonNull CollectionInfo.Type service) {
        // put discovered information here
        final Configuration.ServiceInfo config = new Configuration.ServiceInfo();
        log.info("Finding initial " + service.toString() + " service configuration");

        return config;
    }

    // data classes

    @RequiredArgsConstructor
    @ToString(exclude={"logs", "authtoken", "rawPassword", "password"})
    public static class Configuration implements Serializable {
        // We have to use URI here because HttpUrl is not serializable!

        @ToString
        public static class ServiceInfo implements Serializable {
            public final Map<String, CollectionInfo> collections = new HashMap<>();
        }

        public final URI url;

        public final String userName, authtoken;
        public String rawPassword;
        public String password;
        public Crypto.AsymmetricKeyPair keyPair;

        public final ServiceInfo cardDAV;
        public final ServiceInfo calDAV;

        public final String logs;

        public Throwable error;

        @Getter
        private final boolean failed;
    }

}
