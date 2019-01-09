/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.App
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.model.CollectionInfo

import org.apache.commons.codec.Charsets
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.io.IOException
import java.util.LinkedList

import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class ServiceTest {
    private var httpClient: OkHttpClient? = null
    private var remote: HttpUrl? = null
    private var authToken: String? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        httpClient = HttpClient.create(null)
        remote = HttpUrl.parse("http://localhost:8000") // FIXME: hardcode for now, should make configureable
        val journalAuthenticator = JournalAuthenticator(httpClient!!, remote!!)
        authToken = journalAuthenticator.getAuthToken(Helpers.USER, Helpers.PASSWORD)

        httpClient = HttpClient.create(null, App.log, null, authToken!!)

        /* Reset */
        val request = Request.Builder()
                .post(object : RequestBody() {
                    override fun contentType(): MediaType? {
                        return null
                    }

                    @Throws(IOException::class)
                    override fun writeTo(sink: BufferedSink) {

                    }
                })
                .url(remote!!.newBuilder().addEncodedPathSegments("reset/").build())
                .build()
        val response = httpClient!!.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed resetting")
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
    }

    @Test
    @Throws(IOException::class, Exceptions.HttpException::class, Exceptions.GenericCryptoException::class, Exceptions.IntegrityException::class)
    fun testSyncSimple() {
        var caught: Exception?
        val journalManager = JournalManager(httpClient!!, remote!!)
        val info = CollectionInfo.defaultForServiceType(CollectionInfo.Type.ADDRESS_BOOK)
        info.uid = JournalManager.Journal.genUid()
        info.displayName = "Test"
        val crypto = Crypto.CryptoManager(info.version, Helpers.keyBase64, info.uid)
        var journal = JournalManager.Journal(crypto, info.toJson(), info.uid)
        journalManager.create(journal)

        // Try pushing the same journal (uid clash)
        try {
            caught = null
            journalManager.create(journal)
        } catch (e: Exceptions.HttpException) {
            caught = e
        }

        assertNotNull(caught)

        var journals: List<JournalManager.Journal> = journalManager.list()
        assertEquals(journals.size.toLong(), 1)
        var info2 = CollectionInfo.fromJson(journals[0].getContent(crypto))
        assertEquals(info2.displayName, info.displayName)

        // Update journal
        info.displayName = "Test 2"
        journal = JournalManager.Journal(crypto, info.toJson(), info.uid)
        journalManager.update(journal)

        journals = journalManager.list()
        assertEquals(journals.size.toLong(), 1)
        info2 = CollectionInfo.fromJson(journals[0].getContent(crypto))
        assertEquals(info2.displayName, info.displayName)

        // Delete journal
        journalManager.delete(journal)

        journals = journalManager.list()
        assertEquals(journals.size.toLong(), 0)

        // Bad HMAC
        info.uid = JournalManager.Journal.genUid()
        journal = JournalManager.Journal(crypto, info.toJson(), info.uid)
        info.displayName = "Test 3"
        //// We assume this doesn't update the hmac.
        journal.setContent(crypto, info.toJson())
        journalManager.create(journal)

        try {
            caught = null
            for (journal1 in journalManager.list()) {
                val crypto1 = Crypto.CryptoManager(info.version, Helpers.keyBase64, journal1.uid!!)
                journal1.verify(crypto1)
            }
        } catch (e: Exceptions.IntegrityException) {
            caught = e
        }

        assertNotNull(caught)
    }


    @Test
    @Throws(IOException::class, Exceptions.HttpException::class, Exceptions.GenericCryptoException::class, Exceptions.IntegrityException::class)
    fun testSyncEntry() {
        var caught: Exception?
        val journalManager = JournalManager(httpClient!!, remote!!)
        val info = CollectionInfo.defaultForServiceType(CollectionInfo.Type.ADDRESS_BOOK)
        info.uid = JournalManager.Journal.genUid()
        info.displayName = "Test"
        val crypto = Crypto.CryptoManager(info.version, Helpers.keyBase64, info.uid)
        val journal = JournalManager.Journal(crypto, info.toJson(), info.uid)
        journalManager.create(journal)

        val journalEntryManager = JournalEntryManager(httpClient!!, remote!!, info.uid)
        var previousEntry: JournalEntryManager.Entry? = null
        val entry = JournalEntryManager.Entry()
        entry.update(crypto, "Content", previousEntry)

        var entries: MutableList<JournalEntryManager.Entry> = LinkedList()
        var retEntries: List<JournalEntryManager.Entry>

        entries.add(entry)
        journalEntryManager.create(entries, null)
        previousEntry = entry

        entries.clear()
        var entry2 = JournalEntryManager.Entry()
        entry2.update(crypto, "Content", previousEntry)
        entries.add(entry2)

        // Pushing a correct entries without the last parameter
        try {
            caught = null
            journalEntryManager.create(entries, null)
        } catch (e: Exceptions.HttpException) {
            caught = e
        }

        assertNotNull(caught)

        // Adding a second entry
        journalEntryManager.create(entries, previousEntry.uid)
        previousEntry = entry2

        entries.clear()
        entries.add(entry)
        entries.add(entry2)

        // Check last works:
        retEntries = journalEntryManager.list(crypto, entry.uid, 0)
        assertEquals(retEntries.size.toLong(), 1)
        retEntries = journalEntryManager.list(crypto, entry2.uid, 0)
        assertEquals(retEntries.size.toLong(), 0)

        // Corrupt the journal and verify we catch it
        entries.clear()
        entry2 = JournalEntryManager.Entry()
        entry2.update(crypto, "Content", null)
        entries.add(entry2)

        journalEntryManager.create(entries, previousEntry.uid)

        try {
            caught = null
            journalEntryManager.list(crypto, null, 0)
        } catch (e: Exceptions.IntegrityException) {
            caught = e
        }

        assertNotNull(caught)
    }


    @Test
    @Throws(IOException::class, Exceptions.HttpException::class, Exceptions.GenericCryptoException::class, Exceptions.IntegrityException::class)
    fun testUserInfo() {
        val cryptoManager = Crypto.CryptoManager(Constants.CURRENT_VERSION, Helpers.keyBase64, "userInfo")
        var userInfo: UserInfoManager.UserInfo?
        var userInfo2: UserInfoManager.UserInfo?
        val manager = UserInfoManager(httpClient!!, remote!!)

        // Get when there's nothing
        userInfo = manager[Helpers.USER]
        assertNull(userInfo)

        // Create
        userInfo = UserInfoManager.UserInfo.generate(cryptoManager, Helpers.USER)
        manager.create(userInfo)

        // Get
        userInfo2 = manager[Helpers.USER]
        assertNotNull(userInfo2)
        assertArrayEquals(userInfo.getContent(cryptoManager), userInfo2!!.getContent(cryptoManager))

        // Update
        userInfo.setContent(cryptoManager, "test".toByteArray())
        manager.update(userInfo)
        userInfo2 = manager[Helpers.USER]
        assertNotNull(userInfo2)
        assertArrayEquals(userInfo.getContent(cryptoManager), userInfo2!!.getContent(cryptoManager))

        // Delete
        manager.delete(userInfo)
        userInfo = manager[Helpers.USER]
        assertNull(userInfo)
    }


    @Test
    @Throws(IOException::class, Exceptions.HttpException::class, Exceptions.GenericCryptoException::class, Exceptions.IntegrityException::class)
    fun testJournalMember() {
        var caught: Exception?
        val journalManager = JournalManager(httpClient!!, remote!!)
        val info = CollectionInfo.defaultForServiceType(CollectionInfo.Type.ADDRESS_BOOK)
        info.uid = JournalManager.Journal.genUid()
        info.displayName = "Test"
        val crypto = Crypto.CryptoManager(info.version, Helpers.keyBase64, info.uid)
        val journal = JournalManager.Journal(crypto, info.toJson(), info.uid)
        journalManager.create(journal)

        assertEquals(journalManager.listMembers(journal).size.toLong(), 0)

        // Test inviting ourselves
        val member = JournalManager.Member(Helpers.USER, "test".toByteArray())
        try {
            caught = null
            journalManager.addMember(journal, member)
        } catch (e: Exceptions.HttpException) {
            caught = e
        }

        assertNotNull(caught)

        val member2 = JournalManager.Member(Helpers.USER2, "test".toByteArray())
        journalManager.addMember(journal, member2)
        assertEquals(journalManager.listMembers(journal).size.toLong(), 1)

        // Uninviting user
        journalManager.deleteMember(journal, member2)

        assertEquals(journalManager.listMembers(journal).size.toLong(), 0)
    }
}
