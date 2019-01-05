package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.GsonHelper
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection

class JournalAuthenticator(private val client: OkHttpClient, remote: HttpUrl) {
    private val remote: HttpUrl

    init {
        this.remote = remote.newBuilder()
                .addPathSegments("api-token-auth")
                .addPathSegment("")
                .build()
    }

    private inner class AuthResponse private constructor() {
        val token: String? = null
    }

    @Throws(Exceptions.HttpException::class, IOException::class)
    fun getAuthToken(username: String, password: String): String? {
        val formBuilder = FormBody.Builder()
                .add("username", username)
                .add("password", password)

        val request = Request.Builder()
                .post(formBuilder.build())
                .url(remote)
                .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            return GsonHelper.gson.fromJson(response.body()!!.charStream(), AuthResponse::class.java).token
        } else if (response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
            throw Exceptions.UnauthorizedException(response, "Username or password incorrect")
        } else {
            throw Exceptions.HttpException(response)
        }
    }
}
