package at.bitfire.davdroid.journalmanager;

import java.io.IOException;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.GsonHelper;
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

    public String getAuthToken(String username, String password) throws Exceptions.HttpException {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("username", username)
                .add("password", password);

        Request request = new Request.Builder()
                .post(formBuilder.build())
                .url(remote)
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                return GsonHelper.gson.fromJson(response.body().charStream(), AuthResponse.class).token;
            } else if (response.code() == 400) {
                throw new Exceptions.UnauthorizedException("Username or password incorrect");
            } else {
                throw new Exceptions.HttpException("Error authenticating");
            }
        } catch (IOException e) {
            App.log.log(Level.SEVERE, "Couldn't download external resource", e);
        }

        return null;
    }
}
