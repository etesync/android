package com.etesync.syncadapter.journalmanager

import com.etesync.syncadapter.App
import com.etesync.syncadapter.journalmanager.util.ByteUtil
import com.etesync.syncadapter.utils.Base64
import org.apache.commons.codec.Charsets
import org.apache.commons.lang3.ArrayUtils
import org.spongycastle.crypto.AsymmetricBlockCipher
import org.spongycastle.crypto.BufferedBlockCipher
import org.spongycastle.crypto.InvalidCipherTextException
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.encodings.OAEPEncoding
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.engines.RSAEngine
import org.spongycastle.crypto.generators.RSAKeyPairGenerator
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PKCS7Padding
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.crypto.params.ParametersWithIV
import org.spongycastle.crypto.params.RSAKeyGenerationParameters
import org.spongycastle.crypto.util.PrivateKeyFactory
import org.spongycastle.crypto.util.PrivateKeyInfoFactory
import org.spongycastle.crypto.util.PublicKeyFactory
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.spongycastle.util.encoders.Hex
import java.io.IOException
import java.io.Serializable
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

object Crypto {
    @JvmStatic
    fun deriveKey(salt: String, password: String): String {
        val keySize = 190

        return Base64.encodeToString(SCrypt.generate(password.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), 16384, 8, 1, keySize), Base64.NO_WRAP)
    }

    @JvmStatic
    fun generateKeyPair(): AsymmetricKeyPair? {
        val keyPairGenerator = RSAKeyPairGenerator()
        keyPairGenerator.init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), SecureRandom(), 3072, 160))
        val keyPair = keyPairGenerator.generateKeyPair()
        try {
            val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyPair.private)
            val publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(keyPair.public)
            return AsymmetricKeyPair(privateKeyInfo.encoded, publicKeyInfo.encoded)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    class AsymmetricKeyPair(val privateKey: ByteArray, val publicKey: ByteArray) : Serializable

    class AsymmetricCryptoManager(private val keyPair: AsymmetricKeyPair) {

        fun encrypt(pubkey: ByteArray, content: ByteArray?): ByteArray? {
            var cipher: AsymmetricBlockCipher = RSAEngine()
            cipher = OAEPEncoding(cipher)
            try {
                cipher.init(true, PublicKeyFactory.createKey(pubkey))
                return cipher.processBlock(content, 0, content!!.size)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InvalidCipherTextException) {
                e.printStackTrace()
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(content, Base64.NO_WRAP))
            }

            return null
        }

        fun decrypt(cipherText: ByteArray): ByteArray? {
            var cipher: AsymmetricBlockCipher = RSAEngine()
            cipher = OAEPEncoding(cipher)
            try {
                cipher.init(false, PrivateKeyFactory.createKey(keyPair.privateKey))
                return cipher.processBlock(cipherText, 0, cipherText.size)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InvalidCipherTextException) {
                e.printStackTrace()
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(cipherText, Base64.NO_WRAP))
            }

            return null
        }

        companion object {

            fun getKeyFingerprint(pubkey: ByteArray): ByteArray {
                return sha256(pubkey)
            }

            @JvmStatic
            fun getPrettyKeyFingerprint(pubkey: ByteArray): String {
                val fingerprint = Crypto.AsymmetricCryptoManager.getKeyFingerprint(pubkey)
                val spacing = "   "
                val ret = getEncodedChunk(fingerprint, 0) + spacing +
                        getEncodedChunk(fingerprint, 4) + spacing +
                        getEncodedChunk(fingerprint, 8) + spacing +
                        getEncodedChunk(fingerprint, 12) + "\n" +
                        getEncodedChunk(fingerprint, 16) + spacing +
                        getEncodedChunk(fingerprint, 20) + spacing +
                        getEncodedChunk(fingerprint, 24) + spacing +
                        getEncodedChunk(fingerprint, 28)
                return ret.trim { it <= ' ' }
            }

            private fun getEncodedChunk(hash: ByteArray, offset: Int): String {
                val chunk = ByteUtil.byteArray4ToLong(hash, offset) % 100000
                return String.format(Locale.getDefault(), "%05d", chunk)
            }
        }
    }

    class CryptoManager {
        val version: Byte
        private var cipherKey: ByteArray? = null
        private var hmacKey: ByteArray? = null
        private var derivedKey: ByteArray? = null

        private val random: SecureRandom
            get() = SecureRandom()

        private fun setDerivedKey(derivedKey: ByteArray?) {
            cipherKey = hmac256("aes".toByteArray(Charsets.UTF_8), derivedKey)
            hmacKey = hmac256("hmac".toByteArray(Charsets.UTF_8), derivedKey)
        }

        constructor(version: Int, keyPair: AsymmetricKeyPair, encryptedKey: ByteArray) {
            val cryptoManager = Crypto.AsymmetricCryptoManager(keyPair)
            derivedKey = cryptoManager.decrypt(encryptedKey)

            this.version = version.toByte()
            setDerivedKey(derivedKey)
        }

        @Throws(Exceptions.IntegrityException::class, Exceptions.VersionTooNewException::class)
        constructor(version: Int, keyBase64: String, salt: String) {
            if (version > java.lang.Byte.MAX_VALUE) {
                throw Exceptions.IntegrityException("Version is out of range.")
            } else if (version > Constants.CURRENT_VERSION) {
                throw Exceptions.VersionTooNewException("Version to new: " + version.toString())
            } else if (version == 1) {
                derivedKey = Base64.decode(keyBase64, Base64.NO_WRAP)
            } else {
                derivedKey = hmac256(salt.toByteArray(Charsets.UTF_8), Base64.decode(keyBase64, Base64.NO_WRAP))
            }

            this.version = version.toByte()
            setDerivedKey(derivedKey)
        }

        private fun getCipher(iv: ByteArray, encrypt: Boolean): BufferedBlockCipher {
            val key = KeyParameter(cipherKey!!)
            val params = ParametersWithIV(key, iv)

            val padding = PKCS7Padding()
            val cipher = PaddedBufferedBlockCipher(
                    CBCBlockCipher(AESEngine()), padding)
            cipher.reset()
            cipher.init(encrypt, params)

            return cipher
        }

        fun decrypt(_data: ByteArray): ByteArray? {
            val iv = Arrays.copyOfRange(_data, 0, blockSize)
            val data = Arrays.copyOfRange(_data, blockSize, _data.size)

            val cipher = getCipher(iv, false)

            val buf = ByteArray(cipher.getOutputSize(data.size))
            var len = cipher.processBytes(data, 0, data.size, buf, 0)
            try {
                len += cipher.doFinal(buf, len)
            } catch (e: InvalidCipherTextException) {
                e.printStackTrace()
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(_data, Base64.NO_WRAP))
                return null
            }

            // remove padding
            val out = ByteArray(len)
            System.arraycopy(buf, 0, out, 0, len)

            return out
        }

        fun encrypt(data: ByteArray): ByteArray? {
            val iv = ByteArray(blockSize)
            random.nextBytes(iv)

            val cipher = getCipher(iv, true)

            val buf = ByteArray(cipher.getOutputSize(data.size) + blockSize)
            System.arraycopy(iv, 0, buf, 0, iv.size)
            val len = iv.size + cipher.processBytes(data, 0, data.size, buf, iv.size)
            try {
                cipher.doFinal(buf, len)
            } catch (e: InvalidCipherTextException) {
                App.log.severe("Invalid ciphertext: " + Base64.encodeToString(data, Base64.NO_WRAP))
                e.printStackTrace()
                return null
            }

            return buf
        }

        fun hmac(data: ByteArray): ByteArray {
            return if (version.toInt() == 1) {
                hmac256(hmacKey, data)
            } else {
                // Starting from version 2 we hmac the version too.
                hmac256(hmacKey, ArrayUtils.add(data, version))
            }
        }

        fun getEncryptedKey(keyPair: AsymmetricKeyPair, publicKey: ByteArray): ByteArray? {
            val cryptoManager = AsymmetricCryptoManager(keyPair)
            return cryptoManager.encrypt(publicKey, derivedKey)
        }

        companion object {
            val HMAC_SIZE = 256 / 8 // hmac256 in bytes

            private val blockSize = 16 // AES's block size in bytes

            private fun hmac256(keyByte: ByteArray?, data: ByteArray?): ByteArray {
                val hmac = HMac(SHA256Digest())
                val key = KeyParameter(keyByte!!)
                val ret = ByteArray(hmac.macSize)
                hmac.init(key)
                hmac.update(data, 0, data!!.size)
                hmac.doFinal(ret, 0)
                return ret
            }
        }
    }

    internal fun sha256(base: String): String {
        return toHex(sha256(base.toByteArray(Charsets.UTF_8)))
    }

    private fun sha256(base: ByteArray): ByteArray {
        val digest = SHA256Digest()
        digest.update(base, 0, base.size)
        val ret = ByteArray(digest.digestSize)
        digest.doFinal(ret, 0)
        return ret
    }

    internal fun toHex(bytes: ByteArray): String {
        return Hex.toHexString(bytes).toLowerCase()
    }
}
