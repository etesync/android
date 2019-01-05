/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.utils.Base64

import org.apache.commons.codec.Charsets
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.io.IOException

import okhttp3.HttpUrl
import okhttp3.OkHttpClient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

class AuthenticatorTest {
    private var httpClient: OkHttpClient? = null
    private var remote: HttpUrl? = null

    @Before
    @Throws(IOException::class)
    fun setUp() {
        httpClient = HttpClient.create(null)
        remote = HttpUrl.parse("http://localhost:8000") // FIXME: hardcode for now, should make configureable
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
    }

    @Test
    @Throws(IOException::class, Exceptions.HttpException::class)
    fun testAuthToken() {
        val journalAuthenticator = JournalAuthenticator(httpClient!!, remote!!)
        val authToken = journalAuthenticator.getAuthToken(Helpers.USER, Helpers.PASSWORD)
        assertNotEquals(authToken!!.length.toLong(), 0)
    }

    @Test(expected = Exceptions.UnauthorizedException::class)
    @Throws(Exceptions.IntegrityException::class, Exceptions.VersionTooNewException::class, IOException::class, Exceptions.HttpException::class)
    fun testNoUser() {
        val journalAuthenticator = JournalAuthenticator(httpClient!!, remote!!)
        val authToken = journalAuthenticator.getAuthToken(Helpers.USER, "BadPassword")
        assertNotEquals(authToken!!.length.toLong(), 0)
    }
}
