/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.journalmanager;

import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.utils.Base64;

import org.apache.commons.codec.Charsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AuthenticatorTest {
    private OkHttpClient httpClient;
    private HttpUrl remote;

    @Before
    public void setUp() throws IOException {
        httpClient = HttpClient.create(null);
        remote = HttpUrl.parse("http://localhost:8000"); // FIXME: hardcode for now, should make configureable
    }

    @After
    public void tearDown() throws IOException {
    }

    @Test
    public void testAuthToken() throws IOException, Exceptions.HttpException {
        JournalAuthenticator journalAuthenticator = new JournalAuthenticator(httpClient, remote);
        String authToken = journalAuthenticator.getAuthToken(Helpers.USER, Helpers.PASSWORD);
        assertNotEquals(authToken.length(), 0);
    }

    @Test(expected=Exceptions.UnauthorizedException.class)
    public void testNoUser() throws Exceptions.IntegrityException, Exceptions.VersionTooNewException, IOException, Exceptions.HttpException {
        JournalAuthenticator journalAuthenticator = new JournalAuthenticator(httpClient, remote);
        String authToken = journalAuthenticator.getAuthToken(Helpers.USER, "BadPassword");
        assertNotEquals(authToken.length(), 0);
    }
}
