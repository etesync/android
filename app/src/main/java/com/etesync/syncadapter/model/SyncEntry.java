package com.etesync.syncadapter.model;

import com.etesync.syncadapter.GsonHelper;
import com.etesync.syncadapter.journalmanager.Crypto;
import com.etesync.syncadapter.journalmanager.JournalEntryManager;

import java.io.Serializable;

public class SyncEntry implements Serializable {
    private String content;
    private Actions action;

    public String getContent() {
        return content;
    }

    public Actions getAction() {
        return action;
    }

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

    public static SyncEntry fromJournalEntry(Crypto.CryptoManager crypto, JournalEntryManager.Entry entry) {
        return fromJson(entry.getContent(crypto));
    }

    static SyncEntry fromJson(String json) {
        return GsonHelper.gson.fromJson(json, SyncEntry.class);
    }

    public String toJson() {
        return GsonHelper.gson.toJson(this, this.getClass());
    }
}
