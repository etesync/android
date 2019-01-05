package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.App
import com.etesync.syncadapter.GsonHelper
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class JournalEntryManager(httpClient: OkHttpClient, remote: HttpUrl, val uid: String) : BaseManager() {

    init {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journals")
                .addPathSegments(uid)
                .addPathSegment("entries")
                .addPathSegment("")
                .build()
        App.log.info("Created for: " + this.remote!!.toString())

        this.client = httpClient
    }

    @Throws(Exceptions.HttpException::class, Exceptions.IntegrityException::class)
    fun list(crypto: Crypto.CryptoManager, last: String?, limit: Int): List<Entry> {
        var previousEntry: Entry? = null
        val urlBuilder = this.remote!!.newBuilder()
        if (last != null) {
            urlBuilder.addQueryParameter("last", last)
            previousEntry = Entry.getFakeWithUid(last)
        }

        if (limit > 0) {
            urlBuilder.addQueryParameter("limit", limit.toString())
        }

        val remote = urlBuilder.build()

        val request = Request.Builder()
                .get()
                .url(remote)
                .build()

        val response = newCall(request)
        val body = response.body()
        val ret = GsonHelper.gson.fromJson<List<Entry>>(body!!.charStream(), entryType)

        for (entry in ret) {
            entry.verify(crypto, previousEntry)
            previousEntry = entry
        }

        return ret
    }

    @Throws(Exceptions.HttpException::class)
    fun create(entries: List<Entry>, last: String?) {
        val urlBuilder = this.remote!!.newBuilder()
        if (last != null) {
            urlBuilder.addQueryParameter("last", last)
        }

        val remote = urlBuilder.build()

        val body = RequestBody.create(BaseManager.JSON, GsonHelper.gson.toJson(entries, entryType))

        val request = Request.Builder()
                .post(body)
                .url(remote)
                .build()

        newCall(request)
    }

    class Entry : BaseManager.Base() {

        fun update(crypto: Crypto.CryptoManager, content: String, previous: Entry) {
            setContent(crypto, content)
            uid = calculateHmac(crypto, previous)
        }

        @Throws(Exceptions.IntegrityException::class)
        internal fun verify(crypto: Crypto.CryptoManager, previous: Entry?) {
            val correctHash = calculateHmac(crypto, previous)
            if (uid != correctHash) {
                throw Exceptions.IntegrityException("Bad HMAC. $uid != $correctHash")
            }
        }

        private fun calculateHmac(crypto: Crypto.CryptoManager, previous: Entry?): String {
            var uuid: String? = null
            if (previous != null) {
                uuid = previous.uid
            }

            return Crypto.toHex(calculateHmac(crypto, uuid))
        }

        companion object {
            @JvmStatic
            fun getFakeWithUid(uid: String): Entry {
                val ret = Entry()
                ret.uid = uid
                return ret
            }
        }
    }

    companion object {
        private val entryType = object : TypeToken<List<Entry>>() {

        }.type
    }

}
