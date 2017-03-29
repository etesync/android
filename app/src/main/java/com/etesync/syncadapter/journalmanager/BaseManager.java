package com.etesync.syncadapter.journalmanager;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.GsonHelper;

import org.apache.commons.codec.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Level;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

abstract class BaseManager {
    final static protected MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    protected HttpUrl remote;
    protected OkHttpClient client;

    Response newCall(Request request) throws Exceptions.HttpException {
        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            App.log.log(Level.SEVERE, "Failed while connecting to server", e);
            throw new Exceptions.ServiceUnavailableException("[" + e.getClass().getName() + "] " + e.getLocalizedMessage());
        }

        if (!response.isSuccessful()) {
            switch (response.code()) {
                case HttpURLConnection.HTTP_UNAVAILABLE:
                    throw new Exceptions.ServiceUnavailableException(response, "Service unavailable");
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    throw new Exceptions.UnauthorizedException(response, "Unauthorized auth token");
                case HttpURLConnection.HTTP_FORBIDDEN:
                    ApiError apiError = GsonHelper.gson.fromJson(response.body().charStream(), ApiError.class);
                    if (apiError.code.equals("service_inactive")) {
                        throw new Exceptions.UserInactiveException(response, apiError.detail);
                    }
                default:
                    // Fall through. We want to always throw when unsuccessful.
            }
            throw new Exceptions.HttpException(response);
        }

        return response;
    }

    static class ApiError {
        String detail;
        String code;

        ApiError() {
        }
    }

    static class Base {
        @Setter(AccessLevel.PACKAGE)
        @Getter(AccessLevel.PACKAGE)
        private byte[] content;
        @Setter(AccessLevel.PACKAGE)
        private String uid;

        public String getUuid() {
            return uid;
        }

        public String getContent(String keyBase64) {
            // FIXME: probably cache encryption object
            Crypto.CryptoManager cryptoManager = new Crypto.CryptoManager(keyBase64, null);
            return new String(cryptoManager.decrypt(content), Charsets.UTF_8);
        }

        void setContent(String keyBase64, String content) {
            // FIXME: probably cache encryption object
            Crypto.CryptoManager cryptoManager = new Crypto.CryptoManager(keyBase64, null);
            this.content = cryptoManager.encrypt(content.getBytes(Charsets.UTF_8));
        }

        byte[] calculateHmac(String keyBase64, String uuid) {
            ByteArrayOutputStream hashContent = new ByteArrayOutputStream();

            try {
                if (uuid != null) {
                    hashContent.write(uuid.getBytes(Charsets.UTF_8));
                }

                hashContent.write(content);
            } catch (IOException e) {
                // Can never happen, but just in case, return a bad hmac
                return "DEADBEEFDEADBEEFDEADBEEFDEADBEEF".getBytes();
            }

            // FIXME: probably cache encryption object
            Crypto.CryptoManager cryptoManager = new Crypto.CryptoManager(keyBase64, null);
            return cryptoManager.hmac(hashContent.toByteArray());
        }

        protected Base() {
        }

        Base(String keyBase64, String content, String uid) {
            setContent(keyBase64, content);
            setUid(uid);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "<" + uid + ">";
        }

        String toJson() {
            return GsonHelper.gson.toJson(this, getClass());
        }
    }
}
