package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.GsonHelper
import okhttp3.*
import java.io.IOException
import java.net.HttpURLConnection

class JournalAuthenticator(private val client: OkHttpClient, private val remote: HttpUrl) {
    private inner class AuthResponse private constructor() {
        val token: String? = null
    }

    @Throws(Exceptions.HttpException::class, IOException::class)
    fun getAuthToken(username: String, password: String): String? {
        val remote = remote.newBuilder()
                .addPathSegments("api-token-auth")
                .addPathSegment("")
                .build()
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

    fun invalidateAuthToken(authToken: String) {
        val remote = remote.newBuilder()
                .addPathSegments("api/logout")
                .addPathSegment("")
                .build()

        val body = RequestBody.create(null, byteArrayOf())
        val request = Request.Builder()
                .post(body)
                .url(remote)
                .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            return
        } else {
            when (response.code()) {
                HttpURLConnection.HTTP_BAD_GATEWAY -> throw Exceptions.BadGatewayException(response, "Bad gateway: most likely a server restart")
                HttpURLConnection.HTTP_UNAVAILABLE -> throw Exceptions.ServiceUnavailableException(response, "Service unavailable")
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw Exceptions.UnauthorizedException(response, "Unauthorized auth token")
            }
        }
    }
}
