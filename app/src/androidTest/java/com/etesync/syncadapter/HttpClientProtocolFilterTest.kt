package com.etesync.syncadapter

import java.util.Arrays
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpClientProtocolFilterTest {
    @Test
    fun filterEnabledTlsProtocols_removesLegacyProtocolsWithoutAddingOrReorderingProtocols() {
        val providerEnabledProtocols = arrayOf(
            "SSL",
            "SSLv2Hello",
            "SSLv3",
            "TLSv1",
            "TLSv1.0",
            "TLSv1.1",
            "TLSv1.2",
            "TLSv1.3",
            "TLSv1.4"
        )

        val filteredProtocols = filterEnabledTlsProtocols(providerEnabledProtocols)

        assertEquals(
            Arrays.asList("TLSv1.2", "TLSv1.3", "TLSv1.4"),
            filteredProtocols.toList()
        )
        assertTrue(filteredProtocols.size < providerEnabledProtocols.size)
        for (protocol in filteredProtocols) {
            assertTrue(
                "Filtered protocol $protocol was not enabled by the provider",
                providerEnabledProtocols.contains(protocol)
            )
        }
        assertFalse(filteredProtocols.contains("TLSv1.5"))
    }

    @Test
    fun filterEnabledTlsProtocols_failsClosedWhenOnlyLegacyProtocolsAreEnabled() {
        try {
            filterEnabledTlsProtocols(
                arrayOf("SSL", "SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.0", "TLSv1.1")
            )
            fail("Filtering an all-legacy provider protocol list must fail closed")
        } catch (_: SSLException) {
            // Expected: assigning an empty or broadened protocol list is forbidden.
        }
    }
}
