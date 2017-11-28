// IEteSyncService.aidl
package com.etesync.syncadapter;

// Declare any non-default types here with import statements
import com.etesync.syncadapter.remote.Journal;

interface IEteSyncService {
    boolean hasPermission(String journalType);

    void requestPermission(String journalType);

    Journal[] getJournals(String journalType);
}
