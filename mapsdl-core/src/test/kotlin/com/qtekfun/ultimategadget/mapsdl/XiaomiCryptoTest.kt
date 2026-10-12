package com.qtekfun.ultimategadget.mapsdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks for the Mi Fitness request crypto ([XiaomiCrypto]). No Android / org.json here,
 * so the RC4-drop[1024] + SHA1-sign port can be verified without a device.
 */
class XiaomiCryptoTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun base64RoundTripAndKnownVectors() {
        assertEquals("Zg==", XiaomiCrypto.base64Encode("f".toByteArray()))
        assertEquals("Zm8=", XiaomiCrypto.base64Encode("fo".toByteArray()))
        assertEquals("Zm9v", XiaomiCrypto.base64Encode("foo".toByteArray()))
        assertEquals("Zm9vYg==", XiaomiCrypto.base64Encode("foob".toByteArray()))
        assertEquals("Zm9vYmE=", XiaomiCrypto.base64Encode("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", XiaomiCrypto.base64Encode("foobar".toByteArray()))

        for (s in listOf("", "f", "fo", "foo", "foob", "fooba", "foobar", "a longer binary-ish \u0000ÿ")) {
            val bytes = s.toByteArray(Charsets.ISO_8859_1)
            assertEquals(s, String(XiaomiCrypto.base64Decode(XiaomiCrypto.base64Encode(bytes)), Charsets.ISO_8859_1))
        }
    }

    @Test
    fun rc4KnownVector() {
        // RFC 6229-style test: key "Key", plaintext "Plaintext" -> BBF316E8D940AF0AD3.
        val cipher = XiaomiCrypto.Rc4("Key".toByteArray())
        assertEquals("bbf316e8d940af0ad3", hex(cipher.crypt("Plaintext".toByteArray())))
    }

    @Test
    fun deriveRc4KeyIsDeterministic() {
        val ssecurity = XiaomiCrypto.base64Encode("sixteen-byte-key".toByteArray())
        val nonce = XiaomiCrypto.base64Encode(ByteArray(12) { it.toByte() })
        assertEquals(
            XiaomiCrypto.deriveRc4Key(ssecurity, nonce),
            XiaomiCrypto.deriveRc4Key(ssecurity, nonce),
        )
    }

    @Test
    fun nonceIs12BytesBase64() {
        val nonce = XiaomiCrypto.base64Decode(XiaomiCrypto.generateNonce())
        assertEquals(12, nonce.size) // 8 random + 4 time bytes
    }

    @Test
    fun encryptParamsIsRecoverableWithTheSameStream() {
        val ssecurity = XiaomiCrypto.base64Encode("0123456789abcdef".toByteArray())
        val nonce = XiaomiCrypto.base64Encode(ByteArray(12) { (it * 7).toByte() })
        val data = "{\"page_size\":50,\"status\":1}"

        val enc = XiaomiCrypto.encryptParams("POST", "/app/v1/source/get_source_list", mapOf("data" to data), nonce, ssecurity)

        assertEquals(nonce, enc["_nonce"])
        assertTrue(enc.containsKey("signature"))
        assertTrue(enc.containsKey("data"))
        assertTrue(enc.containsKey("rc4_hash__"))

        // Decrypt exactly as the server would: one continuous RC4 stream over the sorted keys.
        val rc4 = XiaomiCrypto.Rc4(XiaomiCrypto.base64Decode(XiaomiCrypto.deriveRc4Key(ssecurity, nonce))).apply { drop(1024) }
        val dataPlain = String(rc4.crypt(XiaomiCrypto.base64Decode(enc["data"]!!)))
        val hashPlain = String(rc4.crypt(XiaomiCrypto.base64Decode(enc["rc4_hash__"]!!)))
        assertEquals(data, dataPlain)
        assertEquals(28, hashPlain.length) // base64 of a 20-byte SHA1 digest
    }
}
