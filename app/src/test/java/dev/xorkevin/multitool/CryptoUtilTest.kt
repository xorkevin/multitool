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

    @Test
    fun blake2b() {
        Assert.assertEquals(
            "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8",
            CryptoUtil.blake2b("".toByteArray(), 32).getOrThrow().toHexString()
        )
        Assert.assertEquals(
            "62fbf5098db33f5ee72f85b23b3751d39a2d8d8363f1c734bbb04e05ad2b3b58",
            CryptoUtil.blake2b("hello, world".toByteArray(), 32).getOrThrow().toHexString()
        )
        Assert.assertEquals(
            "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce",
            CryptoUtil.blake2b("".toByteArray(), 64).getOrThrow().toHexString()
        )
        Assert.assertEquals(
            "7355dd5276c21cfe0c593b5063b96af3f96a454b33216f58314f44c3ade92e9cd6cec4210a0836246780e9baf927cc50b9a3d7073e8f9bd12780fddbcb930c6d",
            CryptoUtil.blake2b("hello, world".toByteArray(), 64).getOrThrow().toHexString()
        )
    }
}
