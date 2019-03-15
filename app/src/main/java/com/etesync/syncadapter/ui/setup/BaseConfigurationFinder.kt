/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.ui.setup

import android.content.Context
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.Exceptions
import com.etesync.syncadapter.journalmanager.JournalAuthenticator
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.log.StringHandler
import com.etesync.syncadapter.model.CollectionInfo
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.util.*

class BaseConfigurationFinder(protected val context: Context, protected val credentials: LoginCredentials) {

    protected val logBuffer = StringHandler()
    protected var httpClient: OkHttpClient

    init {
        httpClient = HttpClient.Builder(context).build().okHttpClient
    }


    fun findInitialConfiguration(): Configuration {
        var failed = false
        val cardDavConfig = findInitialConfiguration(CollectionInfo.Type.ADDRESS_BOOK)
        val calDavConfig = findInitialConfiguration(CollectionInfo.Type.CALENDAR)

        val authenticator = JournalAuthenticator(httpClient, HttpUrl.get(credentials.uri!!)!!)

        var authtoken: String? = null
        try {
            authtoken = authenticator.getAuthToken(credentials.userName, credentials.password)
        } catch (e: Exceptions.HttpException) {
            Logger.log.warning(e.message)

            failed = true
        } catch (e: IOException) {
            Logger.log.warning(e.message)
            failed = true
        }

        return Configuration(
                credentials.uri,
                credentials.userName, authtoken,
                cardDavConfig, calDavConfig,
                logBuffer.toString(), failed
        )
    }

    protected fun findInitialConfiguration(service: CollectionInfo.Type): Configuration.ServiceInfo {
        // put discovered information here
        val config = Configuration.ServiceInfo()
        Logger.log.info("Finding initial " + service.toString() + " service configuration")

        return config
    }

    // data classes

    class Configuration
    // We have to use URI here because HttpUrl is not serializable!

    (val url: URI, val userName: String, val authtoken: String?, val cardDAV: ServiceInfo, val calDAV: ServiceInfo, val logs: String, val isFailed: Boolean) : Serializable {
        var rawPassword: String? = null
        var password: String? = null
        var keyPair: Crypto.AsymmetricKeyPair? = null

        var error: Throwable? = null

        class ServiceInfo : Serializable {
            val collections: Map<String, CollectionInfo> = HashMap()

            override fun toString(): String {
                return "BaseConfigurationFinder.Configuration.ServiceInfo(collections=" + this.collections + ")"
            }
        }

        override fun toString(): String {
            return "BaseConfigurationFinder.Configuration(url=" + this.url + ", userName=" + this.userName + ", keyPair=" + this.keyPair + ", cardDAV=" + this.cardDAV + ", calDAV=" + this.calDAV + ", error=" + this.error + ", failed=" + this.isFailed + ")"
        }
    }

}
