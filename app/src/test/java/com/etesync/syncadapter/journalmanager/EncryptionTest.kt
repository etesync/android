/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.utils.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.spongycastle.util.encoders.Hex
import java.io.IOException

class EncryptionTest {
    @Before
    @Throws(IOException::class)
    fun setUp() {
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
    }

    @Test
    fun testDerivePassword() {
        val key = Crypto.deriveKey(Helpers.USER, Helpers.PASSWORD)
        assertEquals(key, Helpers.keyBase64)
    }

    @Test
    @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
    fun testCryptoV1() {
        val cryptoManager = Crypto.CryptoManager(1, Helpers.keyBase64, "TestSaltShouldBeJournalId")

        val clearText = "This Is Some Test Cleartext."
        val cipher = cryptoManager.encrypt(clearText.toByteArray())
        assertEquals(clearText, String(cryptoManager.decrypt(cipher!!)!!))

        val expected = "Lz+HUFzh1HdjxuGdQrBwBG1IzHT0ug6mO8fwePSbXtc="
        assertEquals(expected, Base64.encodeToString(cryptoManager.hmac("Some test data".toByteArray()), Base64.NO_WRAP))
    }

    @Test
    @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
    fun testCryptoV2() {
        val cryptoManager = Crypto.CryptoManager(2, Helpers.keyBase64, "TestSaltShouldBeJournalId")

        val clearText = "This Is Some Test Cleartext."
        val cipher = cryptoManager.encrypt(clearText.toByteArray())
        assertEquals(clearText, String(cryptoManager.decrypt(cipher!!)!!))

        val expected = "XQ/A0gentOaE98R9wzf3zEIAHj4OH1GF8J4C6JiJupo="
        assertEquals(expected, Base64.encodeToString(cryptoManager.hmac("Some test data".toByteArray()), Base64.NO_WRAP))
    }

    @Test(expected = Exceptions.VersionTooNewException::class)
    @Throws(Exceptions.IntegrityException::class, Exceptions.VersionTooNewException::class)
    fun testCryptoVersionTooNew() {
        Crypto.CryptoManager(120, Helpers.keyBase64, "TestSaltShouldBeJournalId")
    }

    @Test(expected = Exceptions.IntegrityException::class)
    @Throws(Exceptions.IntegrityException::class, Exceptions.VersionTooNewException::class)
    fun testCryptoVersionOutOfRange() {
        Crypto.CryptoManager(999, Helpers.keyBase64, "TestSaltShouldBeJournalId")
    }

    @Test
    @Throws(Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
    fun testAsymCrypto() {
        val keyPair = Crypto.generateKeyPair()
        val cryptoManager = Crypto.AsymmetricCryptoManager(keyPair!!)

        val clearText = "This Is Some Test Cleartext.".toByteArray()
        val cipher = cryptoManager.encrypt(keyPair.publicKey, clearText)
        val clearText2 = cryptoManager.decrypt(cipher!!)
        assertArrayEquals(clearText, clearText2)

        // Mostly for coverage. Make sure it's the expected sha256 value.
        assertEquals("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                Hex.toHexString(Crypto.AsymmetricCryptoManager.getKeyFingerprint("a".toByteArray())).toLowerCase())
    }
}
