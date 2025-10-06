package dev.xorkevin.multitool

import org.junit.Assert
import org.junit.Test

class CryptoUtilTest {
    private val testCiphertext =
        "313233343536373839303132333435363738393031323334294aa9252af708f13a97729811b53cb0ddc85d147211f6ce2fb5fcd6"
    private val testKey = "passphrase1234567890123456789012"
    private val testNonce = "123456789012345678901234"

    @Test
    fun decryptXChaCha20() {
        Assert.assertEquals(
            "hello, world", CryptoUtil.decryptXChaCha20Poly1305(
                testKey.toByteArray(), testCiphertext.hexToByteArray()
            ).getOrThrow().toString(Charsets.UTF_8)
        )
    }

    @Test
    fun encryptAndDecryptXChaCha20() {
        val ciphertext = CryptoUtil.encryptXChaCha20Poly1305(
            testKey.toByteArray(), testNonce.toByteArray(), "hello, world".toByteArray()
        ).getOrThrow()
        Assert.assertEquals(testCiphertext, ciphertext.toHexString())
    }

    @Test
    fun hchacha20() {
        val key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val nonce = "000000090000004a0000000031415927"
        val res = CryptoUtil.hchacha20(key.hexToByteArray(), nonce.hexToByteArray()).getOrThrow()
        Assert.assertEquals(
            "82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc", res.toHexString()
        )
    }
}
