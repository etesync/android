/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.etesync.syncadapter.model.ServiceDB;
import com.etesync.syncadapter.model.Settings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();

    private static final String userAgent;

    static {
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(BuildConfig.buildTime));
        userAgent = App.getAppName() + "/" + BuildConfig.VERSION_NAME + " (" + date + "; okhttp3) Android/" + Build.VERSION.RELEASE;
    }

    private HttpClient() {
    }

    public static OkHttpClient create(@Nullable Context context, @NonNull final Logger logger, @Nullable String host, @NonNull String token) {
        OkHttpClient.Builder builder = defaultBuilder(context, logger);

        // use account settings for authentication
        builder = addAuthentication(builder, host, token);

        return builder.build();
    }

    public static OkHttpClient create(@Nullable Context context, @NonNull AccountSettings settings, @NonNull final Logger logger) {
        return create(context, logger, settings.getUri().getHost(), settings.getAuthToken());
    }

    public static OkHttpClient create(@NonNull Context context, @NonNull Logger logger) {
        return defaultBuilder(context, logger).build();
    }

    public static OkHttpClient create(@NonNull Context context, @NonNull AccountSettings settings) {
        return create(context, settings, App.log);
    }

    public static OkHttpClient create(@Nullable Context context) {
        return create(context, App.log);
    }

    public static OkHttpClient create(@Nullable Context context, @NonNull URI uri, String authToken) {
        return create(context, App.log, uri.getHost(), authToken);
    }


    private static OkHttpClient.Builder defaultBuilder(@Nullable Context context, @NonNull final Logger logger) {
        OkHttpClient.Builder builder = client.newBuilder();

        // use MemorizingTrustManager to manage self-signed certificates
        if (context != null) {
            App app = (App) context.getApplicationContext();
            if (App.getSslSocketFactoryCompat() != null && app.getCertManager() != null)
                builder.sslSocketFactory(App.getSslSocketFactoryCompat(), app.getCertManager());
            if (App.getHostnameVerifier() != null)
                builder.hostnameVerifier(App.getHostnameVerifier());
        }

        // set timeouts
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.writeTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(120, TimeUnit.SECONDS);

        // custom proxy support
        if (context != null) {
            SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(context);
            try {
                Settings settings = new Settings(dbHelper.getReadableDatabase());
                if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                    InetSocketAddress address = new InetSocketAddress(
                            settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                            settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                    );

                    Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
                    builder.proxy(proxy);
                    App.log.log(Level.INFO, "Using proxy", proxy);
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                App.log.log(Level.SEVERE, "Can't set proxy, ignoring", e);
            } finally {
                dbHelper.close();
            }
        }

        // add User-Agent to every request
        builder.addNetworkInterceptor(userAgentInterceptor);

        // add network logging, if requested
        if (logger.isLoggable(Level.FINEST)) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    logger.finest(message);
                }
            });
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }

        return builder;
    }

    private static OkHttpClient.Builder addAuthentication(@NonNull OkHttpClient.Builder builder, @Nullable String host, @NonNull String token) {
        TokenAuthenticator authHandler = new TokenAuthenticator(host, token);

        return builder.addNetworkInterceptor(authHandler);
    }

    private static class TokenAuthenticator implements Interceptor {
        protected static final String
                HEADER_AUTHORIZATION = "Authorization";
        final String host, token;


        private TokenAuthenticator(String host, String token) {
            this.host = host;
            this.token = token;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            /* Only add to the host we want. */
            if ((host == null)
                    || (request.url().host().equals(host))) {
                if ((token != null)
                        && (request.header(HEADER_AUTHORIZATION) == null)) {
                    request = request.newBuilder()
                            .header(HEADER_AUTHORIZATION, "Token " + token)
                            .build();
                }
            }

            return chain.proceed(request);
        }
    }

    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Locale locale = Locale.getDefault();
            Request request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", locale.getLanguage() + "-" + locale.getCountry() + ", " + locale.getLanguage() + ";q=0.7, *;q=0.5")
                    .build();
            return chain.proceed(request);
        }
    }

}
