package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.App
import com.etesync.syncadapter.GsonHelper
import com.etesync.syncadapter.journalmanager.Crypto.CryptoManager.Companion.HMAC_SIZE
import com.etesync.syncadapter.journalmanager.Crypto.sha256
import com.etesync.syncadapter.journalmanager.Crypto.toHex
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.spongycastle.util.Arrays
import java.util.*

class JournalManager(httpClient: OkHttpClient, remote: HttpUrl) : BaseManager() {
    init {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journals")
                .addPathSegment("")
                .build()
        App.log.info("Created for: " + this.remote!!.toString())

        this.client = httpClient
    }

    @Throws(Exceptions.HttpException::class)
    fun list(): List<Journal> {
        val request = Request.Builder()
                .get()
                .url(remote!!)
                .build()

        val response = newCall(request)
        val body = response.body()
        val ret = GsonHelper.gson.fromJson<List<Journal>>(body!!.charStream(), journalType)

        for (journal in ret) {
            journal.processFromJson()
        }

        return ret
    }

    @Throws(Exceptions.HttpException::class)
    fun delete(journal: Journal) {
        val remote = this.remote!!.resolve(journal.uid!! + "/")
        val request = Request.Builder()
                .delete()
                .url(remote!!)
                .build()

        newCall(request)
    }

    @Throws(Exceptions.HttpException::class)
    fun create(journal: Journal) {
        val body = RequestBody.create(BaseManager.JSON, journal.toJson())

        val request = Request.Builder()
                .post(body)
                .url(remote!!)
                .build()

        newCall(request)
    }

    @Throws(Exceptions.HttpException::class)
    fun update(journal: Journal) {
        val remote = this.remote!!.resolve(journal.uid!! + "/")
        val body = RequestBody.create(BaseManager.JSON, journal.toJson())

        val request = Request.Builder()
                .put(body)
                .url(remote!!)
                .build()

        newCall(request)
    }

    private fun getMemberRemote(journal: Journal, user: String?): HttpUrl {
        val bulider = this.remote!!.newBuilder()
        bulider.addPathSegment(journal.uid!!)
                .addPathSegment("members")
        if (user != null) {
            bulider.addPathSegment(user)
        }
        bulider.addPathSegment("")
        return bulider.build()
    }

    @Throws(Exceptions.HttpException::class, Exceptions.IntegrityException::class, Exceptions.GenericCryptoException::class)
    fun listMembers(journal: Journal): List<Member> {
        val request = Request.Builder()
                .get()
                .url(getMemberRemote(journal, null))
                .build()

        val response = newCall(request)
        val body = response.body()
        return GsonHelper.gson.fromJson(body!!.charStream(), memberType)
    }

    @Throws(Exceptions.HttpException::class)
    fun deleteMember(journal: Journal, member: Member) {
        val body = RequestBody.create(BaseManager.JSON, member.toJson())

        val request = Request.Builder()
                .delete(body)
                .url(getMemberRemote(journal, member.user))
                .build()

        newCall(request)
    }

    @Throws(Exceptions.HttpException::class)
    fun addMember(journal: Journal, member: Member) {
        val body = RequestBody.create(BaseManager.JSON, member.toJson())

        val request = Request.Builder()
                .post(body)
                .url(getMemberRemote(journal, null))
                .build()

        newCall(request)
    }

    class Journal : BaseManager.Base {
        val owner: String? = null
        val key: ByteArray? = null
        var version = -1
        val readOnly = false

        @Transient
        private var hmac: ByteArray? = null

        private constructor() : super() {}

        constructor(crypto: Crypto.CryptoManager, content: String, uid: String) : super(crypto, content, uid) {
            hmac = calculateHmac(crypto)
            version = crypto.version.toInt()
        }

        fun processFromJson() {
            hmac = Arrays.copyOfRange(content!!, 0, HMAC_SIZE)
            content = Arrays.copyOfRange(content!!, HMAC_SIZE, content!!.size)
        }

        @Throws(Exceptions.IntegrityException::class)
        fun verify(crypto: Crypto.CryptoManager) {
            val hmac = this.hmac;

            if (hmac == null) {
                throw Exceptions.IntegrityException("HMAC is null!")
            }

            val correctHash = calculateHmac(crypto)
            if (!Arrays.areEqual(hmac, correctHash)) {
                throw Exceptions.IntegrityException("Bad HMAC. " + toHex(hmac) + " != " + toHex(correctHash))
            }
        }

        internal fun calculateHmac(crypto: Crypto.CryptoManager): ByteArray {
            return super.calculateHmac(crypto, uid)
        }

        internal override fun toJson(): String {
            val rawContent = content
            content = Arrays.concatenate(hmac, rawContent)
            val ret = super.toJson()
            content = rawContent
            return ret
        }

        companion object {
            @JvmStatic
            fun fakeWithUid(uid: String): Journal {
                val ret = Journal()
                ret.uid = uid
                return ret
            }

            @JvmStatic
            fun genUid(): String {
                return sha256(UUID.randomUUID().toString())
            }
        }
    }

    class Member {
        val user: String?
        val key: ByteArray?
        val readOnly: Boolean

        private constructor() {
            this.user = null
            this.key = null
            this.readOnly = false
        }

        constructor(user: String, encryptedKey: ByteArray, readOnly: Boolean = false) {
            this.user = user
            this.key = encryptedKey
            this.readOnly = readOnly
        }

        internal fun toJson(): String {
            return GsonHelper.gson.toJson(this, javaClass)
        }
    }

    companion object {
        private val journalType = object : TypeToken<List<Journal>>() {

        }.type
        private val memberType = object : TypeToken<List<Member>>() {

        }.type
    }
}
