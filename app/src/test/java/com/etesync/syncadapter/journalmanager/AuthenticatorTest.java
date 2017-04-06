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
    private static final String USER = "test@localhost";
    private static final String PASSWORD = "SomePassword";
    private static final String keyBase64 = "Gpn6j6WJ/9JJbVkWhmEfZjlqSps5rwEOzjUOO0rqufvb4vtT4UfRgx0uMivuGwjF7/8Y1z1glIASX7Oz/4l2jucgf+lAzg2oTZFodWkXRZCDmFa7c9a8/04xIs7koFmUH34Rl9XXW6V2/GDVigQhQU8uWnrGo795tupoNQMbtB8RgMX5GyuxR55FvcybHpYBbwrDIsKvXcBxWFEscdNU8zyeq3yjvDo/W/y24dApW3mnNo7vswoL2rpkZj3dqw==";

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
        String authToken = journalAuthenticator.getAuthToken(USER, PASSWORD);
        assertNotEquals(authToken.length(), 0);
    }

    @Test(expected=Exceptions.UnauthorizedException.class)
    public void testNoUser() throws Exceptions.IntegrityException, Exceptions.VersionTooNewException, IOException, Exceptions.HttpException {
        JournalAuthenticator journalAuthenticator = new JournalAuthenticator(httpClient, remote);
        String authToken = journalAuthenticator.getAuthToken(USER, "BadPassword");
        assertNotEquals(authToken.length(), 0);
    }
}
