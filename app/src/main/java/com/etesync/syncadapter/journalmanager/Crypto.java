package com.etesync.syncadapter.journalmanager;

import android.support.annotation.NonNull;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.journalmanager.util.ByteUtil;
import com.etesync.syncadapter.utils.Base64;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.asn1.pkcs.PrivateKeyInfo;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.engines.RSAEngine;
import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.generators.SCrypt;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.BlockCipherPadding;
import org.spongycastle.crypto.paddings.PKCS7Padding;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.util.PrivateKeyFactory;
import org.spongycastle.crypto.util.PrivateKeyInfoFactory;
import org.spongycastle.crypto.util.PublicKeyFactory;
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

public class Crypto {
    public static String deriveKey(String salt, String password) {
        final int keySize = 190;

        return Base64.encodeToString(SCrypt.generate(password.getBytes(Charsets.UTF_8), salt.getBytes(Charsets.UTF_8), 16384, 8, 1, keySize), Base64.NO_WRAP);
    }

    public static AsymmetricKeyPair generateKeyPair() {
        RSAKeyPairGenerator keyPairGenerator = new RSAKeyPairGenerator();
        keyPairGenerator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), 3072, 160));
        AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();
        try {
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyPair.getPrivate());
            SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(keyPair.getPublic());
            return new AsymmetricKeyPair(privateKeyInfo.getEncoded(), publicKeyInfo.getEncoded());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static class AsymmetricKeyPair implements Serializable {
        private final byte[] privateKey;
        private final byte[] publicKey;

        public byte[] getPrivateKey() {
            return privateKey;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public AsymmetricKeyPair(final byte[] privateKey, final byte[] publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    public static class AsymmetricCryptoManager {
        private final AsymmetricKeyPair keyPair;

        public AsymmetricCryptoManager(AsymmetricKeyPair keyPair) {
            this.keyPair = keyPair;
        }

        public byte[] encrypt(byte[] pubkey, byte[] content) {
            AsymmetricBlockCipher cipher = new RSAEngine();
            cipher = new OAEPEncoding(cipher);
            try {
                cipher.init(true, PublicKeyFactory.createKey(pubkey));
                return cipher.processBlock(content, 0, content.length);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvalidCipherTextException e) {
                e.printStackTrace();
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(content, Base64.NO_WRAP));
            }

            return null;
        }

        public byte[] decrypt(byte[] cipherText) {
            AsymmetricBlockCipher cipher = new RSAEngine();
            cipher = new OAEPEncoding(cipher);
            try {
                cipher.init(false, PrivateKeyFactory.createKey(keyPair.getPrivateKey()));
                return cipher.processBlock(cipherText, 0, cipherText.length);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvalidCipherTextException e) {
                e.printStackTrace();
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(cipherText, Base64.NO_WRAP));
            }

            return null;
        }

        public static byte[] getKeyFingerprint(byte[] pubkey) {
            return sha256(pubkey);
        }

        public static String getPrettyKeyFingerprint(byte[] pubkey) {
            byte[] fingerprint = Crypto.AsymmetricCryptoManager.getKeyFingerprint(pubkey);
            String spacing = "   ";
            String ret = getEncodedChunk(fingerprint, 0) + spacing +
                    getEncodedChunk(fingerprint, 4)  + spacing +
                    getEncodedChunk(fingerprint, 8)  + spacing +
                    getEncodedChunk(fingerprint, 12) + "\n" +
                    getEncodedChunk(fingerprint, 16) + spacing +
                    getEncodedChunk(fingerprint, 20) + spacing +
                    getEncodedChunk(fingerprint, 24) + spacing +
                    getEncodedChunk(fingerprint, 28);
            return ret.trim();
        }

        private static String getEncodedChunk(byte[] hash, int offset) {
            long chunk = ByteUtil.byteArray4ToLong(hash, offset) % 100000;
            return String.format(Locale.getDefault(), "%05d", chunk);
        }
    }

    public static class CryptoManager {
        final static int HMAC_SIZE = 256 / 8; // hmac256 in bytes

        private SecureRandom _random = null;
        private final byte version;
        private byte[] cipherKey;
        private byte[] hmacKey;
        private byte[] derivedKey;

        public byte getVersion() {
            return version;
        }

        private void setDerivedKey(byte[] derivedKey) {
            cipherKey = hmac256("aes".getBytes(Charsets.UTF_8), derivedKey);
            hmacKey = hmac256("hmac".getBytes(Charsets.UTF_8), derivedKey);
        }

        public CryptoManager(int version, AsymmetricKeyPair keyPair, byte[] encryptedKey) {
            Crypto.AsymmetricCryptoManager cryptoManager = new Crypto.AsymmetricCryptoManager(keyPair);
            derivedKey = cryptoManager.decrypt(encryptedKey);

            this.version = (byte) version;
            setDerivedKey(derivedKey);
        }

        public CryptoManager(int version, @NonNull String keyBase64, @NonNull String salt) throws Exceptions.IntegrityException, Exceptions.VersionTooNewException {
            if (version > Byte.MAX_VALUE) {
                throw new Exceptions.IntegrityException("Version is out of range.");
            } else if (version > Constants.CURRENT_VERSION) {
                throw new Exceptions.VersionTooNewException("Version to new: " + String.valueOf(version));
            } else if (version == 1) {
                derivedKey = Base64.decode(keyBase64, Base64.NO_WRAP);
            } else {
                derivedKey = hmac256(salt.getBytes(Charsets.UTF_8), Base64.decode(keyBase64, Base64.NO_WRAP));
            }

            this.version = (byte) version;
            setDerivedKey(derivedKey);
        }

        private static final int blockSize = 16; // AES's block size in bytes

        private BufferedBlockCipher getCipher(byte[] iv, boolean encrypt) {
            KeyParameter key = new KeyParameter(cipherKey);
            CipherParameters params = new ParametersWithIV(key, iv);

            BlockCipherPadding padding = new PKCS7Padding();
            BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new AESEngine()), padding);
            cipher.reset();
            cipher.init(encrypt, params);

            return cipher;
        }

        byte[] decrypt(byte[] _data) {
            byte[] iv = Arrays.copyOfRange(_data, 0, blockSize);
            byte[] data = Arrays.copyOfRange(_data, blockSize, _data.length);

            BufferedBlockCipher cipher = getCipher(iv, false);

            byte[] buf = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, buf, 0);
            try {
                len += cipher.doFinal(buf, len);
            } catch (InvalidCipherTextException e) {
                e.printStackTrace();
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(_data, Base64.NO_WRAP));
                return null;
            }

            // remove padding
            byte[] out = new byte[len];
            System.arraycopy(buf, 0, out, 0, len);

            return out;
        }

        byte[] encrypt(byte[] data) {
            byte[] iv = new byte[blockSize];
            getRandom().nextBytes(iv);

            BufferedBlockCipher cipher = getCipher(iv, true);

            byte[] buf = new byte[cipher.getOutputSize(data.length) + blockSize];
            System.arraycopy(iv, 0, buf, 0, iv.length);
            int len = iv.length + cipher.processBytes(data, 0, data.length, buf, iv.length);
            try {
                cipher.doFinal(buf, len);
            } catch (InvalidCipherTextException e) {
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(data, Base64.NO_WRAP));
                e.printStackTrace();
                return null;
            }

            return buf;
        }

        byte[] hmac(byte[] data) {
            if (version == 1) {
                return hmac256(hmacKey, data);
            } else {
                // Starting from version 2 we hmac the version too.
                return hmac256(hmacKey, ArrayUtils.add(data, version));
            }
        }

        private SecureRandom getRandom() {
            if (_random == null) {
                _random = new SecureRandom();
            }
            return _random;
        }

        private static byte[] hmac256(byte[] keyByte, byte[] data) {
            HMac hmac = new HMac(new SHA256Digest());
            KeyParameter key = new KeyParameter(keyByte);
            byte[] ret = new byte[hmac.getMacSize()];
            hmac.init(key);
            hmac.update(data, 0, data.length);
            hmac.doFinal(ret, 0);
            return ret;
        }

        public byte[] getEncryptedKey(AsymmetricKeyPair keyPair, byte[] publicKey) {
            AsymmetricCryptoManager cryptoManager = new AsymmetricCryptoManager(keyPair);
            return cryptoManager.encrypt(publicKey, derivedKey);
        }
    }

    static String sha256(String base) {
        return toHex(sha256(base.getBytes(Charsets.UTF_8)));
    }

    private static byte[] sha256(byte[] base) {
        SHA256Digest digest = new SHA256Digest();
        digest.update(base, 0, base.length);
        byte[] ret = new byte[digest.getDigestSize()];
        digest.doFinal(ret, 0);
        return ret;
    }

    static String toHex(byte[] bytes) {
        return Hex.toHexString(bytes).toLowerCase();
    }
}
