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
import at.bitfire.cert4android.CertTlsSocketFactory
import at.bitfire.cert4android.CustomCertManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.model.Settings
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import java.util.logging.Logger as LoggerType

class HttpClient private constructor(
        val okHttpClient: OkHttpClient,
        private val certManager: CustomCertManager?
): AutoCloseable {

    companion object {
        /** [OkHttpClient] singleton to build all clients from */
        val sharedClient = OkHttpClient.Builder()
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
        certManager?.close()
    }

    class Builder(
            val context: Context? = null,
            accountSettings: AccountSettings? = null,
            val logger: java.util.logging.Logger = Logger.log
    ) {
        private var certManager: CustomCertManager? = null
        private var certificateAlias: String? = null
        private var foreground = true

        private val orig = sharedClient.newBuilder()

        init {
            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                    message -> logger.finest(message)
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
                    dbHelper.close()
                }
            }

            // use account settings for authentication
            accountSettings?.let {
                addAuthentication(accountSettings.uri!!.host, accountSettings.authToken)
            }
        }

        constructor(context: Context, host: String?, authToken: String): this(context) {
            addAuthentication(host, authToken)
        }

        fun withDiskCache(): Builder {
            val context = context ?: throw IllegalArgumentException("Context is required to find the cache directory")
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir).filterNotNull()) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    Logger.log.fine("Using disk cache: $cacheDir")
                    orig.cache(Cache(cacheDir, 10*1024*1024))
                    break
                }
            }
            return this
        }

        fun customCertManager(manager: CustomCertManager) {
            certManager = manager
        }

        fun setForeground(foreground: Boolean): Builder {
            this.foreground = foreground
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

        fun build(): HttpClient {
            //if (BuildConfig.customCerts)
            context?.let {
                val dbHelper = ServiceDB.OpenHelper(context)
                val settings = Settings(dbHelper.readableDatabase)

                // Only make it interactive if app is in foreground
                customCertManager(CustomCertManager(context, foreground, !settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false), foreground))
                dbHelper.close()
            }

            val trustManager = certManager ?: {
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(null as KeyStore?)
                factory.trustManagers.first() as X509TrustManager
            }()

            val hostnameVerifier = certManager?.hostnameVerifier(OkHostnameVerifier.INSTANCE)
                    ?: OkHostnameVerifier.INSTANCE

            var keyManager: KeyManager? = null
            try {
                certificateAlias?.let { alias ->
                    val context = requireNotNull(context)

                    // get client certificate and private key
                    val certs = KeyChain.getCertificateChain(context, alias) ?: return@let
                    val key = KeyChain.getPrivateKey(context, alias) ?: return@let
                    logger.fine("Using client certificate $alias for authentication (chain length: ${certs.size})")

                    // create Android KeyStore (performs key operations without revealing secret data to DAVx5)
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    // create KeyManager
                    keyManager = object: X509ExtendedKeyManager() {
                        override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String>? = null
                        override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?) = null

                        override fun getClientAliases(p0: String?, p1: Array<out Principal>?) =
                                arrayOf(alias)

                        override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?) =
                                alias

                        override fun getCertificateChain(forAlias: String?) =
                                certs.takeIf { forAlias == alias }

                        override fun getPrivateKey(forAlias: String?) =
                                key.takeIf { forAlias == alias }
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't set up client certificate authentication", e)
            }

            orig.sslSocketFactory(CertTlsSocketFactory(keyManager, trustManager), trustManager)
            orig.hostnameVerifier(hostnameVerifier)

            return HttpClient(orig.build(), certManager)
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
