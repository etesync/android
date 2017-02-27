package com.etesync.syncadapter.journalmanager;

import java.io.IOException;
import java.net.HttpURLConnection;

import com.etesync.syncadapter.GsonHelper;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JournalAuthenticator {
    private HttpUrl remote;
    private OkHttpClient client;

    public JournalAuthenticator(OkHttpClient client, HttpUrl remote) {
        this.client = client;
        this.remote = remote.newBuilder()
                .addPathSegments("api-token-auth")
                .addPathSegment("")
                .build();
    }

    private class AuthResponse {
        private String token;

        private AuthResponse() {
        }
    }

    public String getAuthToken(String username, String password) throws Exceptions.HttpException, IOException {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("username", username)
                .add("password", password);

        Request request = new Request.Builder()
                .post(formBuilder.build())
                .url(remote)
                .build();

        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            return GsonHelper.gson.fromJson(response.body().charStream(), AuthResponse.class).token;
        } else if (response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
            throw new Exceptions.UnauthorizedException("Username or password incorrect");
        } else {
            throw new Exceptions.HttpException(response);
        }
    }
}
