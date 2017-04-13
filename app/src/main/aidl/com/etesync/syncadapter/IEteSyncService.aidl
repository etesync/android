// IEteSyncService.aidl
package com.etesync.syncadapter;

// Declare any non-default types here with import statements

interface IEteSyncService {
    boolean hasPermission(String journalType);

    void requestPermission(String journalType);
}

