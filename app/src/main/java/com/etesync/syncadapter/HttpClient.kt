/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.content.Context
import android.os.Build
import android.security.KeyChain
import at.bitfire.cert4android.CustomCertManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.model.Settings
import okhttp3.*
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.net.ssl.*

class HttpClient private constructor(
        val okHttpClient: OkHttpClient,
        private val certManager: CustomCertManager?
): AutoCloseable {

    companion object {
        /** max. size of disk cache (10 MB) */
        const val DISK_CACHE_MAX_SIZE: Long = 10*1024*1024

        /** [OkHttpClient] singleton to build all clients from */
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
                // set timeouts
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)

                // don't allow redirects by default, because it would break PROPFIND handling
                .followRedirects(false)

                // add User-Agent to every request
                .addNetworkInterceptor(UserAgentInterceptor)

                .build()
    }


    override fun close() {
        okHttpClient.cache?.close()
        certManager?.close()
    }

    class Builder(
            val context: Context? = null,
            accountSettings: AccountSettings? = null,
            val logger: java.util.logging.Logger = Logger.log
    ) {
        private var certManager: CustomCertManager? = null
        private var certificateAlias: String? = null

        private val orig = sharedClient.newBuilder()

        init {
            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor(object: HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        logger.finest(message)
                    }
                })
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                orig.addInterceptor(loggingInterceptor)
            }

            context?.let {
                val dbHelper = ServiceDB.OpenHelper(context)
                val settings = Settings(dbHelper.readableDatabase)

                try {
                    if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                        val address = InetSocketAddress(
                                settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                                settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                        )

                        val proxy = Proxy(Proxy.Type.HTTP, address)
                        orig.proxy(proxy)
                        Logger.log.log(Level.INFO, "Using proxy", proxy)
                    }
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
                } finally {
                    // dbHelper.close()
                }

                //if (BuildConfig.customCerts)
                    customCertManager(CustomCertManager(context, true,
                            !(settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES,false)), true))

                dbHelper.close()
            }

            // use account settings for authentication
            accountSettings?.let {
                addAuthentication(accountSettings.uri!!.host, accountSettings.authToken)
            }
        }

        constructor(context: Context?, host: String?, authToken: String): this(context) {
            addAuthentication(host, authToken)
        }

        fun withDiskCache(): Builder {
            val context = context ?: throw IllegalArgumentException("Context is required to find the cache directory")
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir).filterNotNull()) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    Logger.log.fine("Using disk cache: $cacheDir")
                    orig.cache(Cache(cacheDir, DISK_CACHE_MAX_SIZE))
                    break
                }
            }
            return this
        }

        fun followRedirects(follow: Boolean): Builder {
            orig.followRedirects(follow)
            return this
        }

        fun customCertManager(manager: CustomCertManager) {
            certManager = manager
        }
        fun setForeground(foreground: Boolean): Builder {
            certManager?.appInForeground = foreground
            return this
        }

        private fun addAuthentication(host: String?, token: String): Builder {
            val authHandler = TokenAuthenticator(host, token)

            orig.addNetworkInterceptor(authHandler)

            return this
        }

private class TokenAuthenticator internal constructor(internal val host: String?, internal val token: String?) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        /* Only add to the host we want. */
        if (host == null || request.url.host == host) {
            if (token != null && request.header(HEADER_AUTHORIZATION) == null) {
                request = request.newBuilder()
                        .sslContext(SSLContext.getInstance("TLS"), SSLSessionCache.nullSessionCache())
                        .build()
            }
        }
        
        return chain.proceed(request)
    }
    
    companion object {
        protected val HEADER_AUTHORIZATION = "Authorization"
    }
}

if (keyManager != null) {
    synchronized(certManager) {
        certManager.notify();
    }
}

    }

    private object UserAgentInterceptor : Interceptor {
        private val userAgent = "${App.appName}/${BuildConfig.VERSION_NAME} (okhttp3) Android/${Build.VERSION.RELEASE}"

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
