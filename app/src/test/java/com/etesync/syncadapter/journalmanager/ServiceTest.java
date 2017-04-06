/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.journalmanager;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.HttpClient;
import com.etesync.syncadapter.model.CollectionInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceTest {
    private OkHttpClient httpClient;
    private HttpUrl remote;
    private String authToken;

    @Before
    public void setUp() throws Exception {
        httpClient = HttpClient.create(null);
        remote = HttpUrl.parse("http://localhost:8000"); // FIXME: hardcode for now, should make configureable
        JournalAuthenticator journalAuthenticator = new JournalAuthenticator(httpClient, remote);
        authToken = journalAuthenticator.getAuthToken(Helpers.USER, Helpers.PASSWORD);

        httpClient = HttpClient.create(null, App.log, null, authToken);

        /* Reset */
        Request request = new Request.Builder()
                .post(new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return null;
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {

                    }
                })
                .url(remote.newBuilder().addEncodedPathSegments("reset/").build())
                .build();
        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception("Failed resetting");
        }
    }

    @After
    public void tearDown() throws IOException {
    }

    @Test
    public void testSyncSimple() throws IOException, Exceptions.HttpException, Exceptions.GenericCryptoException, Exceptions.IntegrityException {
        Exception caught;
        JournalManager journalManager = new JournalManager(httpClient, remote);
        CollectionInfo info = CollectionInfo.defaultForServiceType(CollectionInfo.Type.ADDRESS_BOOK);
        info.url = JournalManager.Journal.genUid();
        info.displayName = "Test";
        Crypto.CryptoManager crypto = new Crypto.CryptoManager(info.version, Helpers.keyBase64, info.url);
        JournalManager.Journal journal = new JournalManager.Journal(crypto, info.toJson(), info.url);
        journalManager.putJournal(journal);

        // Try pushing the same journal (uid clash)
        try {
            caught = null;
            journalManager.putJournal(journal);
        } catch (Exceptions.HttpException e) {
            caught = e;
        }
        assertNotNull(caught);

        List<JournalManager.Journal> journals = journalManager.getJournals(Helpers.keyBase64);
        assertEquals(journals.size(), 1);
        CollectionInfo info2 = CollectionInfo.fromJson(journals.get(0).getContent(crypto));
        assertEquals(info2.displayName, info.displayName);

        // Update journal
        info.displayName = "Test 2";
        journal = new JournalManager.Journal(crypto, info.toJson(), info.url);
        journalManager.updateJournal(journal);

        journals = journalManager.getJournals(Helpers.keyBase64);
        assertEquals(journals.size(), 1);
        info2 = CollectionInfo.fromJson(journals.get(0).getContent(crypto));
        assertEquals(info2.displayName, info.displayName);

        // Delete journal
        journalManager.deleteJournal(journal);

        journals = journalManager.getJournals(Helpers.keyBase64);
        assertEquals(journals.size(), 0);

        // Bad HMAC
        info.url = JournalManager.Journal.genUid();
        journal = new JournalManager.Journal(crypto, info.toJson(), info.url);
        info.displayName = "Test 3";
        //// We assume this doesn't update the hmac.
        journal.setContent(crypto, info.toJson());
        journalManager.putJournal(journal);

        try {
            caught = null;
            journalManager.getJournals(Helpers.keyBase64);
        } catch (Exceptions.IntegrityException e) {
            caught = e;
        }
        assertNotNull(caught);
    }
}
