package com.etesync.syncadapter.model

import com.etesync.syncadapter.GsonHelper
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.JournalEntryManager

import java.io.Serializable

class SyncEntry : Serializable {
    val content: String
    val action: Actions

    enum class Actions constructor(private val text: String) {
        ADD("ADD"),
        CHANGE("CHANGE"),
        DELETE("DELETE");

        override fun toString(): String {
            return text
        }
    }

    private constructor() {
        this.content = ""
        this.action = Actions.ADD
    }

    constructor(content: String, action: Actions) {
        this.content = content
        this.action = action
    }

    fun isAction(action: Actions): Boolean {
        return this.action == action
    }

    fun toJson(): String {
        return GsonHelper.gson.toJson(this, this.javaClass)
    }

    companion object {
        @JvmStatic
        fun fromJournalEntry(crypto: Crypto.CryptoManager, entry: JournalEntryManager.Entry): SyncEntry {
            return fromJson(entry.getContent(crypto))
        }

        @JvmStatic
        fun fromJson(json: String): SyncEntry {
            return GsonHelper.gson.fromJson(json, SyncEntry::class.java)
        }
    }
}
