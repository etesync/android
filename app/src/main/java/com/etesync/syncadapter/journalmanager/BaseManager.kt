package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.App
import com.etesync.syncadapter.GsonHelper
import okhttp3.*
import org.apache.commons.codec.Charsets
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.logging.Level

abstract class BaseManager {

    protected var remote: HttpUrl? = null
    protected var client: OkHttpClient? = null

    @Throws(Exceptions.HttpException::class)
    fun newCall(request: Request): Response {
        val response: Response
        try {
            response = client!!.newCall(request).execute()
        } catch (e: IOException) {
            App.log.log(Level.SEVERE, "Failed while connecting to server", e)
            throw Exceptions.ServiceUnavailableException("[" + e.javaClass.name + "] " + e.localizedMessage)
        }

        if (!response.isSuccessful) {
            val apiError = GsonHelper.gson.fromJson(response.body()!!.charStream(), ApiError::class.java)

            when (response.code()) {
                HttpURLConnection.HTTP_UNAVAILABLE -> throw Exceptions.ServiceUnavailableException(response, "Service unavailable")
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw Exceptions.UnauthorizedException(response, "Unauthorized auth token")
                HttpURLConnection.HTTP_FORBIDDEN -> if (apiError.code == "service_inactive") {
                    throw Exceptions.UserInactiveException(response, apiError.detail)
                }
            }// Fall through. We want to always throw when unsuccessful.

            throw Exceptions.HttpException(response, apiError.detail)
        }

        return response
    }

    internal class ApiError {
        var detail: String? = null
        var code: String? = null
    }

    open class Base {
        var content: ByteArray? = null
        var uid: String? = null

        fun getContent(crypto: Crypto.CryptoManager): String {
            return String(crypto.decrypt(content!!)!!, Charsets.UTF_8)
        }

        fun setContent(crypto: Crypto.CryptoManager, content: String) {
            this.content = crypto.encrypt(content.toByteArray(Charsets.UTF_8))
        }

        fun calculateHmac(crypto: Crypto.CryptoManager, uuid: String?): ByteArray {
            val hashContent = ByteArrayOutputStream()

            try {
                if (uuid != null) {
                    hashContent.write(uuid.toByteArray(Charsets.UTF_8))
                }

                hashContent.write(content!!)
            } catch (e: IOException) {
                // Can never happen, but just in case, return a bad hmac
                return "DEADBEEFDEADBEEFDEADBEEFDEADBEEF".toByteArray()
            }

            return crypto.hmac(hashContent.toByteArray())
        }

        protected constructor() {}

        constructor(crypto: Crypto.CryptoManager, content: String, uid: String) {
            setContent(crypto, content)
            this.uid = uid
        }

        override fun toString(): String {
            return javaClass.simpleName + "<" + uid + ">"
        }

        internal open fun toJson(): String {
            return GsonHelper.gson.toJson(this, javaClass)
        }
    }

    companion object {
        val JSON = MediaType.parse("application/json; charset=utf-8")
    }
}
