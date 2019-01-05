package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.GsonHelper
import com.etesync.syncadapter.journalmanager.Crypto.CryptoManager.Companion.HMAC_SIZE
import com.etesync.syncadapter.journalmanager.Crypto.toHex
import okhttp3.*
import org.spongycastle.util.Arrays
import java.io.IOException
import java.net.HttpURLConnection

class UserInfoManager(httpClient: OkHttpClient, remote: HttpUrl) : BaseManager() {
    init {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/user")
                .addPathSegment("")
                .build()

        this.client = httpClient
    }

    @Throws(Exceptions.HttpException::class)
    operator fun get(owner: String): UserInfo? {
        val remote = this.remote!!.newBuilder().addPathSegment(owner).addPathSegment("").build()
        val request = Request.Builder()
                .get()
                .url(remote)
                .build()

        val response: Response
        try {
            response = newCall(request)
        } catch (e: Exceptions.HttpException) {
            return if (e.status == HttpURLConnection.HTTP_NOT_FOUND) {
                null
            } else {
                throw e
            }
        }

        val body = response.body()
        val ret = GsonHelper.gson.fromJson(body!!.charStream(), UserInfo::class.java)
        ret.owner = owner

        return ret
    }

    @Throws(Exceptions.HttpException::class)
    fun delete(userInfo: UserInfo) {
        val remote = this.remote!!.newBuilder().addPathSegment(userInfo.owner!!).addPathSegment("").build()
        val request = Request.Builder()
                .delete()
                .url(remote)
                .build()

        newCall(request)
    }

    @Throws(Exceptions.HttpException::class)
    fun create(userInfo: UserInfo) {
        val body = RequestBody.create(BaseManager.JSON, userInfo.toJson())

        val request = Request.Builder()
                .post(body)
                .url(remote!!)
                .build()

        newCall(request)
    }

    @Throws(Exceptions.HttpException::class)
    fun update(userInfo: UserInfo) {
        val remote = this.remote!!.newBuilder().addPathSegment(userInfo.owner!!).addPathSegment("").build()
        val body = RequestBody.create(BaseManager.JSON, userInfo.toJson())

        val request = Request.Builder()
                .put(body)
                .url(remote)
                .build()

        newCall(request)
    }

    class UserInfo {
        @Transient
        var owner: String? = null
        val version: Byte?
        val pubkey: ByteArray?
        private var content: ByteArray? = null

        fun getContent(crypto: Crypto.CryptoManager): ByteArray? {
            val content = Arrays.copyOfRange(this.content!!, HMAC_SIZE, this.content!!.size)
            return crypto.decrypt(content)
        }

        fun setContent(crypto: Crypto.CryptoManager, rawContent: ByteArray) {
            val content = crypto.encrypt(rawContent)
            this.content = Arrays.concatenate(calculateHmac(crypto, content), content)
        }

        @Throws(Exceptions.IntegrityException::class)
        fun verify(crypto: Crypto.CryptoManager) {
            if (this.content == null) {
                // Nothing to verify.
                return
            }

            val hmac = Arrays.copyOfRange(this.content!!, 0, HMAC_SIZE)
            val content = Arrays.copyOfRange(this.content!!, HMAC_SIZE, this.content!!.size)

            val correctHash = calculateHmac(crypto, content)
            if (!Arrays.areEqual(hmac, correctHash)) {
                throw Exceptions.IntegrityException("Bad HMAC. " + toHex(hmac) + " != " + toHex(correctHash))
            }
        }

        private fun calculateHmac(crypto: Crypto.CryptoManager, content: ByteArray?): ByteArray {
            return crypto.hmac(Arrays.concatenate(content, pubkey))
        }

        private constructor() {
            this.version = null
            this.pubkey = null
        }

        constructor(crypto: Crypto.CryptoManager, owner: String, pubkey: ByteArray, content: ByteArray) {
            this.owner = owner
            this.pubkey = pubkey
            version = crypto.version
            setContent(crypto, content)
        }

        internal fun toJson(): String {
            return GsonHelper.gson.toJson(this, javaClass)
        }

        companion object {

            @JvmStatic
            @Throws(IOException::class)
            fun generate(cryptoManager: Crypto.CryptoManager, owner: String): UserInfo {
                val keyPair = Crypto.generateKeyPair()
                return UserInfo(cryptoManager, owner, keyPair!!.publicKey, keyPair.privateKey)
            }
        }
    }
}
