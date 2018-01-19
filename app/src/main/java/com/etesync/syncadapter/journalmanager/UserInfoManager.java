package com.etesync.syncadapter.journalmanager;

import com.etesync.syncadapter.GsonHelper;

import org.spongycastle.util.Arrays;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.etesync.syncadapter.journalmanager.Crypto.CryptoManager.HMAC_SIZE;
import static com.etesync.syncadapter.journalmanager.Crypto.toHex;

public class UserInfoManager extends BaseManager {
    public UserInfoManager(OkHttpClient httpClient, HttpUrl remote) {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/user")
                .addPathSegment("")
                .build();

        this.client = httpClient;
    }

    public UserInfo get(String owner) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.newBuilder().addPathSegment(owner).addPathSegment("").build();
        Request request = new Request.Builder()
                .get()
                .url(remote)
                .build();

        Response response;
        try {
            response = newCall(request);
        } catch (Exceptions.HttpException e) {
            if (e.status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            } else {
                throw e;
            }
        }

        ResponseBody body = response.body();
        UserInfo ret = GsonHelper.gson.fromJson(body.charStream(), UserInfo.class);
        ret.setOwner(owner);

        return ret;
    }

    public void delete(UserInfo userInfo) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.newBuilder().addPathSegment(userInfo.getOwner()).addPathSegment("").build();
        Request request = new Request.Builder()
                .delete()
                .url(remote)
                .build();

        newCall(request);
    }

    public void create(UserInfo userInfo) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, userInfo.toJson());

        Request request = new Request.Builder()
                .post(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public void update(UserInfo userInfo) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.newBuilder().addPathSegment(userInfo.getOwner()).addPathSegment("").build();
        RequestBody body = RequestBody.create(JSON, userInfo.toJson());

        Request request = new Request.Builder()
                .put(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public static class UserInfo {
        private transient String owner;
        private byte version;
        private byte[] pubkey;
        private byte[] content;

        public void setOwner(final String owner) {
            this.owner = owner;
        }

        public String getOwner() {
            return this.owner;
        }

        public byte getVersion() {
            return this.version;
        }

        public byte[] getPubkey() {
            return this.pubkey;
        }

        public byte[] getContent(Crypto.CryptoManager crypto) {
            byte[] content = Arrays.copyOfRange(this.content, HMAC_SIZE, this.content.length);
            return crypto.decrypt(content);
        }

        void setContent(Crypto.CryptoManager crypto, byte[] rawContent) {
            byte[] content = crypto.encrypt(rawContent);
            this.content = Arrays.concatenate(calculateHmac(crypto, content), content);
        }

        public void verify(Crypto.CryptoManager crypto) throws Exceptions.IntegrityException {
            if (this.content == null) {
                // Nothing to verify.
                return;
            }

            byte[] hmac = Arrays.copyOfRange(this.content, 0, HMAC_SIZE);
            byte[] content = Arrays.copyOfRange(this.content, HMAC_SIZE, this.content.length);

            byte[] correctHash = calculateHmac(crypto, content);
            if (!Arrays.areEqual(hmac, correctHash)) {
                throw new Exceptions.IntegrityException("Bad HMAC. " + toHex(hmac) + " != " + toHex(correctHash));
            }
        }

        private byte[] calculateHmac(Crypto.CryptoManager crypto, byte[] content) {
            return crypto.hmac(Arrays.concatenate(content, pubkey));
        }

        private UserInfo() {
        }

        public UserInfo(Crypto.CryptoManager crypto, String owner, byte[] pubkey, byte[] content) {
            this.owner = owner;
            this.pubkey = pubkey;
            version = crypto.getVersion();
            setContent(crypto, content);
        }

        public static UserInfo generate(Crypto.CryptoManager cryptoManager, String owner) throws IOException {
            Crypto.AsymmetricKeyPair keyPair = Crypto.generateKeyPair();
            return new UserInfo(cryptoManager, owner, keyPair.getPublicKey(), keyPair.getPrivateKey());
        }

        String toJson() {
            return GsonHelper.gson.toJson(this, getClass());
        }
    }
}
