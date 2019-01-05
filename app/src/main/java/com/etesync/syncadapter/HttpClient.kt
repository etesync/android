/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build

import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.model.Settings

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

object HttpClient {
    private val client = OkHttpClient()
    private val userAgentInterceptor = UserAgentInterceptor()

    private val userAgent: String

    init {
        val date = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(BuildConfig.buildTime))
        userAgent = App.appName + "/" + BuildConfig.VERSION_NAME + " (" + date + "; okhttp3) Android/" + Build.VERSION.RELEASE
    }

    fun create(context: Context?, logger: Logger, host: String?, token: String): OkHttpClient {
        var builder = defaultBuilder(context, logger)

        // use account settings for authentication
        builder = addAuthentication(builder, host, token)

        return builder.build()
    }

    @JvmOverloads
    fun create(context: Context?, settings: AccountSettings, logger: Logger = App.log): OkHttpClient {
        return create(context, logger, settings.uri!!.host, settings.authToken)
    }

    @JvmOverloads
    fun create(context: Context, logger: Logger = App.log): OkHttpClient {
        return defaultBuilder(context, logger).build()
    }

    fun create(context: Context?, uri: URI, authToken: String): OkHttpClient {
        return create(context, App.log, uri.host, authToken)
    }


    private fun defaultBuilder(context: Context?, logger: Logger): OkHttpClient.Builder {
        val builder = client.newBuilder()

        // use MemorizingTrustManager to manage self-signed certificates
        if (context != null) {
            val app = context.applicationContext as App
            if (App.sslSocketFactoryCompat != null && app.certManager != null)
                builder.sslSocketFactory(App.sslSocketFactoryCompat!!, app.certManager!!)
            if (App.hostnameVerifier != null)
                builder.hostnameVerifier(App.hostnameVerifier!!)
        }

        // set timeouts
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.writeTimeout(30, TimeUnit.SECONDS)
        builder.readTimeout(120, TimeUnit.SECONDS)

        // custom proxy support
        if (context != null) {
            val dbHelper = ServiceDB.OpenHelper(context)
            try {
                val settings = Settings(dbHelper.readableDatabase)
                if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                    val address = InetSocketAddress(
                            settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                            settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                    )

                    val proxy = Proxy(Proxy.Type.HTTP, address)
                    builder.proxy(proxy)
                    App.log.log(Level.INFO, "Using proxy", proxy)
                }
            } catch (e: IllegalArgumentException) {
                App.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
            } catch (e: NullPointerException) {
                App.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
            } finally {
                dbHelper.close()
            }
        }

        // add User-Agent to every request
        builder.addNetworkInterceptor(userAgentInterceptor)

        // add network logging, if requested
        if (logger.isLoggable(Level.FINEST)) {
            val loggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger { message -> logger.finest(message) })
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(loggingInterceptor)
        }

        return builder
    }

    private fun addAuthentication(builder: OkHttpClient.Builder, host: String?, token: String): OkHttpClient.Builder {
        val authHandler = TokenAuthenticator(host, token)

        return builder.addNetworkInterceptor(authHandler)
    }

    private class TokenAuthenticator internal constructor(internal val host: String?, internal val token: String?) : Interceptor {

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()

            /* Only add to the host we want. */
            if (host == null || request.url().host() == host) {
                if (token != null && request.header(HEADER_AUTHORIZATION) == null) {
                    request = request.newBuilder()
                            .header(HEADER_AUTHORIZATION, "Token $token")
                            .build()
                }
            }

            return chain.proceed(request)
        }

        companion object {
            protected val HEADER_AUTHORIZATION = "Authorization"
        }
    }

    internal class UserAgentInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val locale = Locale.getDefault()
            val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", locale.language + "-" + locale.country + ", " + locale.language + ";q=0.7, *;q=0.5")
                    .build()
            return chain.proceed(request)
        }
    }

}
