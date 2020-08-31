/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.ui.setup

import android.content.Context
import com.etebase.client.Account
import com.etebase.client.Client
import com.etebase.client.exceptions.EtebaseException
import com.etesync.syncadapter.HttpClient
import com.etesync.journalmanager.Crypto
import com.etesync.journalmanager.Exceptions
import com.etesync.journalmanager.JournalAuthenticator
import com.etesync.journalmanager.UserInfoManager
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.util.*

class BaseConfigurationFinder(protected val context: Context, protected val credentials: LoginCredentials) {
    protected var httpClient: OkHttpClient

    init {
        httpClient = HttpClient.Builder(context).build().okHttpClient
    }

    private fun isServerEtebase(): Boolean {
        if (credentials.uri != null) {
            val remote = credentials.uri.toHttpUrlOrNull()!!.newBuilder()
                    .addPathSegments("api/v1/authentication/is_etebase/")
                    .build()

            val request = Request.Builder()
                    .get()
                    .url(remote)
                    .build()

            val response = httpClient.newCall(request).execute()

            return response.isSuccessful
        } else {
            return !credentials.userName.contains("@")
        }
    }

    fun findInitialConfigurationLegacy(): Configuration {
        var exception: Throwable? = null

        val uri = credentials.uri ?: URI(Constants.serviceUrl.toString())

        val authenticator = JournalAuthenticator(httpClient, uri.toHttpUrlOrNull()!!)

        var authtoken: String? = null
        var userInfo: UserInfoManager.UserInfo? = null
        try {
            authtoken = authenticator.getAuthToken(credentials.userName, credentials.password)

            val authenticatedHttpClient = HttpClient.Builder(context, uri.host, authtoken!!).build().okHttpClient
            val userInfoManager = UserInfoManager(authenticatedHttpClient, uri.toHttpUrlOrNull()!!)
            userInfo = userInfoManager.fetch(credentials.userName)
        } catch (e: Exceptions.HttpException) {
            Logger.log.warning(e.message)
            exception = e
        } catch (e: IOException) {
            Logger.log.warning(e.message)
            exception = e
        }

        return Configuration(
                uri,
                credentials.userName,
                null,
                authtoken,
                userInfo,
                exception
        )
    }

    fun findInitialConfigurationEtebase(): Configuration {
        var exception: Throwable? = null

        val uri = credentials.uri ?: URI(Constants.etebaseServiceUrl)

        var etebaseSession: String? = null
        try {
            val client = Client.create(httpClient, uri.toString())
            val etebase = Account.login(client, credentials.userName, credentials.password)
            etebaseSession = etebase.save(null)
        } catch (e: EtebaseException) {
            exception = e
        }

        return Configuration(
                uri,
                credentials.userName,
                etebaseSession,
                null,
                null,
                exception
        )
    }

    fun findInitialConfiguration(): Configuration {
        try {
            if (isServerEtebase()) {
                return findInitialConfigurationEtebase()
            } else {
                return findInitialConfigurationLegacy()
            }
        } catch (e: Exception) {
            return Configuration(
                    credentials.uri,
                    credentials.userName,
                    null,
                    null,
                    null,
                    e
            )
        }
    }

    // data classes

    class Configuration
    // We have to use URI here because HttpUrl is not serializable!

    (val url: URI?, val userName: String, val etebaseSession: String?, val authtoken: String?, var userInfo: UserInfoManager.UserInfo?, var error: Throwable?) : Serializable {
        var rawPassword: String? = null
        var password: String? = null
        var keyPair: Crypto.AsymmetricKeyPair? = null

        val isFailed: Boolean
            get() = this.error != null

        val isLegacy: Boolean
            get() = this.authtoken != null

        class ServiceInfo : Serializable {
            val collections: Map<String, CollectionInfo> = HashMap()

            override fun toString(): String {
                return "BaseConfigurationFinder.Configuration.ServiceInfo(collections=" + this.collections + ")"
            }
        }

        override fun toString(): String {
            return "BaseConfigurationFinder.Configuration(url=" + this.url + ", userName=" + this.userName + ", keyPair=" + this.keyPair + ", error=" + this.error + ", failed=" + this.isFailed + ")"
        }
    }

}
