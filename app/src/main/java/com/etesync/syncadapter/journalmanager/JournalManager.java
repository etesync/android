package com.etesync.syncadapter.journalmanager;

import com.google.gson.reflect.TypeToken;

import org.spongycastle.util.Arrays;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.GsonHelper;

import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.etesync.syncadapter.journalmanager.Crypto.sha256;
import static com.etesync.syncadapter.journalmanager.Crypto.toHex;

public class JournalManager extends BaseManager {
    final static private Type journalType = new TypeToken<List<Journal>>() {
    }.getType();


    public JournalManager(OkHttpClient httpClient, HttpUrl remote) {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journals")
                .addPathSegment("")
                .build();
        App.log.info("Created for: " + this.remote.toString());

        this.client = httpClient;
    }

    public List<Journal> getJournals(String keyBase64) throws Exceptions.HttpException, Exceptions.IntegrityException, Exceptions.GenericCryptoException {
        Request request = new Request.Builder()
                .get()
                .url(remote)
                .build();

        Response response = newCall(request);
        ResponseBody body = response.body();
        List<Journal> ret = GsonHelper.gson.fromJson(body.charStream(), journalType);

        for (Journal journal : ret) {
            Crypto.CryptoManager crypto = new Crypto.CryptoManager(journal.getVersion(), keyBase64, journal.getUid());
            journal.processFromJson();
            journal.verify(crypto);
        }

        return ret;
    }

    public void deleteJournal(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUid() + "/");
        Request request = new Request.Builder()
                .delete()
                .url(remote)
                .build();

        newCall(request);
    }

    public void putJournal(Journal journal) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .post(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public void updateJournal(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUid() + "/");
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .put(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public static class Journal extends Base {
        @Getter
        private int version = -1;

        final private transient int hmacSize = 256 / 8; // hmac256 in bytes
        private transient byte[] hmac = null;

        @SuppressWarnings("unused")
        private Journal() {
            super();
        }

        public Journal(Crypto.CryptoManager crypto, String content, String uid) {
            super(crypto, content, uid);
            hmac = calculateHmac(crypto);
            version = crypto.getVersion();
        }

        private void processFromJson() {
            hmac = Arrays.copyOfRange(getContent(), 0, hmacSize);
            setContent(Arrays.copyOfRange(getContent(), hmacSize, getContent().length));
        }

        void verify(Crypto.CryptoManager crypto) throws Exceptions.IntegrityException {
            if (hmac == null) {
                throw new Exceptions.IntegrityException("HMAC is null!");
            }

            byte[] correctHash = calculateHmac(crypto);
            if (!Arrays.areEqual(hmac, correctHash)) {
                throw new Exceptions.IntegrityException("Bad HMAC. " + toHex(hmac) + " != " + toHex(correctHash));
            }
        }

        byte[] calculateHmac(Crypto.CryptoManager crypto) {
            return super.calculateHmac(crypto, getUid());
        }

        public static String genUid() {
            return sha256(UUID.randomUUID().toString());
        }

        @Override
        String toJson() {
            byte[] rawContent = getContent();
            setContent(Arrays.concatenate(hmac, rawContent));
            String ret = super.toJson();
            setContent(rawContent);
            return ret;
        }
    }
}
