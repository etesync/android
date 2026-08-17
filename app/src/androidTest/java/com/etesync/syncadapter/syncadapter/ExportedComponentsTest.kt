package com.etesync.syncadapter.syncadapter

import android.content.ComponentName
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the exported surface of the sync and account services, as declared in the *merged*
 * AndroidManifest.xml.
 *
 * Sync adapter services must stay unexported: the system server binds them either way (it is
 * exempt from the exported check), while exporting them hands the ISyncAdapter Binder to any
 * local app, which can then crash the sync process with a malformed transaction.
 * See https://github.com/etesync/android/issues/295.
 */
class ExportedComponentsTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isExported(cls: Class<*>) =
            context.packageManager.getServiceInfo(ComponentName(context, cls), 0).exported

    @Test
    fun syncAdapterServicesAreNotExported() {
        for (cls in arrayOf(
                CalendarsSyncAdapterService::class.java,
                ContactsSyncAdapterService::class.java,
                AddressBooksSyncAdapterService::class.java,
                TasksSyncAdapterService::class.java,
                TasksOrgSyncAdapterService::class.java)) {
            assertFalse("${cls.simpleName} must not be exported: any local app could then bind it " +
                    "and crash the sync process (etesync/android#295)", isExported(cls))
        }
    }

    @Test
    fun accountAuthenticatorServiceIsNotExported() {
        assertFalse("AccountAuthenticatorService is bound by the system and must not be exported",
                isExported(AccountAuthenticatorService::class.java))
    }

    @Test
    fun nullAuthenticatorServiceIsExported() {
        // Since Android 11 this one has to stay exported, otherwise Google Contacts doesn't show
        // the address book accounts.
        assertTrue("NullAuthenticatorService must stay exported so that other apps can see the " +
                "address book accounts", isExported(NullAuthenticatorService::class.java))
    }
}
