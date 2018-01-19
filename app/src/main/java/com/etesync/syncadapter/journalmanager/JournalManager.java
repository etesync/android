package com.etesync.syncadapter.journalmanager;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.GsonHelper;
import com.google.gson.reflect.TypeToken;

import org.spongycastle.util.Arrays;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.etesync.syncadapter.journalmanager.Crypto.CryptoManager.HMAC_SIZE;
import static com.etesync.syncadapter.journalmanager.Crypto.sha256;
import static com.etesync.syncadapter.journalmanager.Crypto.toHex;

public class JournalManager extends BaseManager {
    final static private Type journalType = new TypeToken<List<Journal>>() {
    }.getType();
    final static private Type memberType = new TypeToken<List<Member>>() {
    }.getType();


    public JournalManager(OkHttpClient httpClient, HttpUrl remote) {
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journals")
                .addPathSegment("")
                .build();
        App.log.info("Created for: " + this.remote.toString());

        this.client = httpClient;
    }

    public List<Journal> list() throws Exceptions.HttpException {
        Request request = new Request.Builder()
                .get()
                .url(remote)
                .build();

        Response response = newCall(request);
        ResponseBody body = response.body();
        List<Journal> ret = GsonHelper.gson.fromJson(body.charStream(), journalType);

        for (Journal journal : ret) {
            journal.processFromJson();
        }

        return ret;
    }

    public void delete(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUid() + "/");
        Request request = new Request.Builder()
                .delete()
                .url(remote)
                .build();

        newCall(request);
    }

    public void create(Journal journal) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .post(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public void update(Journal journal) throws Exceptions.HttpException {
        HttpUrl remote = this.remote.resolve(journal.getUid() + "/");
        RequestBody body = RequestBody.create(JSON, journal.toJson());

        Request request = new Request.Builder()
                .put(body)
                .url(remote)
                .build();

        newCall(request);
    }

    private HttpUrl getMemberRemote(Journal journal, String user) {
        HttpUrl.Builder bulider = this.remote.newBuilder();
        bulider.addPathSegment(journal.getUid())
                .addPathSegment("members");
        if (user != null) {
            bulider.addPathSegment(user);
        }
        bulider.addPathSegment("");
        return bulider.build();
    }

    public List<Member> listMembers(Journal journal) throws Exceptions.HttpException, Exceptions.IntegrityException, Exceptions.GenericCryptoException {
        Request request = new Request.Builder()
                .get()
                .url(getMemberRemote(journal, null))
                .build();

        Response response = newCall(request);
        ResponseBody body = response.body();
        return GsonHelper.gson.fromJson(body.charStream(), memberType);
    }

    public void deleteMember(Journal journal, Member member) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, member.toJson());

        Request request = new Request.Builder()
                .delete(body)
                .url(getMemberRemote(journal, member.getUser()))
                .build();

        newCall(request);
    }

    public void addMember(Journal journal, Member member) throws Exceptions.HttpException {
        RequestBody body = RequestBody.create(JSON, member.toJson());

        Request request = new Request.Builder()
                .post(body)
                .url(getMemberRemote(journal, null))
                .build();

        newCall(request);
    }

    public static class Journal extends Base {
        private String owner;
        private byte[] key;
        private int version = -1;
        private boolean readOnly = false;

        public String getOwner() {
            return this.owner;
        }

        public byte[] getKey() {
            return this.key;
        }

        public int getVersion() {
            return this.version;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        private transient byte[] hmac = null;

        @SuppressWarnings("unused")
        private Journal() {
            super();
        }

        public static Journal fakeWithUid(String uid) {
            Journal ret = new Journal();
            ret.setUid(uid);
            return ret;
        }

        public Journal(Crypto.CryptoManager crypto, String content, String uid) {
            super(crypto, content, uid);
            hmac = calculateHmac(crypto);
            version = crypto.getVersion();
        }

        private void processFromJson() {
            hmac = Arrays.copyOfRange(getContent(), 0, HMAC_SIZE);
            setContent(Arrays.copyOfRange(getContent(), HMAC_SIZE, getContent().length));
        }

        public void verify(Crypto.CryptoManager crypto) throws Exceptions.IntegrityException {
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

    public static class Member {
        private String user;
        private byte[] key;

        public String getUser() {
            return user;
        }

        public byte[] getKey() {
            return key;
        }

        @SuppressWarnings("unused")
        private Member() {
        }

        public Member(String user, byte[] encryptedKey) {
            this.user = user;
            this.key = encryptedKey;
        }

        String toJson() {
            return GsonHelper.gson.toJson(this, getClass());
        }
    }
}
