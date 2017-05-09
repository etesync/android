// IEteSyncService.aidl
package com.etesync.syncadapter;

// Declare any non-default types here with import statements
import com.etesync.syncadapter.model.CollectionInfo;

interface IEteSyncService {
    boolean hasPermission(String journalType);

    void requestPermission(String journalType);

    CollectionInfo[] getJournalEntries(String journalType);
}
