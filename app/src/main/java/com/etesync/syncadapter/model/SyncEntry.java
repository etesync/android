package com.etesync.syncadapter.model;

import com.etesync.syncadapter.GsonHelper;
import com.etesync.syncadapter.journalmanager.JournalEntryManager;

import lombok.Getter;

public class SyncEntry {
    @Getter
    private String content;
    @Getter
    private Actions action;

    public enum Actions {
        ADD("ADD"),
        CHANGE("CHANGE"),
        DELETE("DELETE");

        private final String text;

        Actions(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @SuppressWarnings("unused")
    private SyncEntry() {
    }

    public SyncEntry(String content, Actions action) {
        this.content = content;
        this.action = action;
    }

    public boolean isAction(Actions action) {
        return this.action.equals(action);
    }

    public static SyncEntry fromJournalEntry(String keyBase64, JournalEntryManager.Entry entry) {
        return GsonHelper.gson.fromJson(entry.getContent(keyBase64), SyncEntry.class);
    }

    public String toJson() {
        return GsonHelper.gson.toJson(this, this.getClass());
    }
}
