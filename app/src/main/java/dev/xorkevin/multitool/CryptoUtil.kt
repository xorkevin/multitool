package dev.xorkevin.multitool

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.ChaChaEngine
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.util.Pack

class CryptoUtil {
    companion object {
        const val XCHACHA20_POLY1305_KEY_SIZE = 32
        const val XCHACHA20_POLY1305_NONCE_SIZE = 24
        const val HCHACHA20_NONCE_SIZE = 16

        fun hchacha20(key: ByteArray, nonce: ByteArray): Result<ByteArray> {
            if (key.size != XCHACHA20_POLY1305_KEY_SIZE) {
                return Result.failure(IllegalArgumentException("key must be 32 bytes"))
            }
            if (nonce.size != HCHACHA20_NONCE_SIZE) {
                return Result.failure(IllegalArgumentException("nonce must be 16 bytes"))
            }

            val state = IntArray(16)
            state[0] = 0x61707865
            state[1] = 0x3320646e
            state[2] = 0x79622d32
            state[3] = 0x6b206574
            Pack.littleEndianToInt(key, 0, state, 4, 8)
            Pack.littleEndianToInt(nonce, 0, state, 12, 4)

            val x = IntArray(16)
            ChaChaEngine.chachaCore(20, state, x)
            // undo chachacore final addition
            x[0] -= state[0]
            x[1] -= state[1]
            x[2] -= state[2]
            x[3] -= state[3]
            x[4] -= state[4]
            x[5] -= state[5]
            x[6] -= state[6]
            x[7] -= state[7]
            x[8] -= state[8]
            x[9] -= state[9]
            x[10] -= state[10]
            x[11] -= state[11]
            x[12] -= state[12]
            x[13] -= state[13]
            x[14] -= state[14]
            x[15] -= state[15]

            val out = ByteArray(XCHACHA20_POLY1305_KEY_SIZE)
            Pack.intToLittleEndian(x.sliceArray(0..<4), out, 0)
            Pack.intToLittleEndian(x.sliceArray(12..<16), out, 16)
            return Result.success(out)
        }

        private fun initXChaCha20Poly1305(
            key: ByteArray, nonce: ByteArray, forEncryption: Boolean
        ): Result<ChaCha20Poly1305> {
            if (key.size != XCHACHA20_POLY1305_KEY_SIZE) {
                return Result.failure(IllegalArgumentException("key must be 32 bytes"))
            }
            if (nonce.size != XCHACHA20_POLY1305_NONCE_SIZE) {
                return Result.failure(IllegalArgumentException("nonce must be 24 bytes"))
            }

            val derivedKey = hchacha20(
                key, nonce.sliceArray(0..<HCHACHA20_NONCE_SIZE)
            ).getOrElse { return Result.failure(it) }
            // first four bytes of final nonce are unused counter space
            val nonceBytes = ByteArray(12)
            nonce.copyInto(nonceBytes, 4, HCHACHA20_NONCE_SIZE, XCHACHA20_POLY1305_NONCE_SIZE)
            val cipher = ChaCha20Poly1305()
            cipher.init(forEncryption, ParametersWithIV(KeyParameter(derivedKey), nonceBytes))
            return Result.success(cipher)
        }

        fun encryptXChaCha20Poly1305(
            key: ByteArray, nonce: ByteArray, plaintext: ByteArray
        ): Result<ByteArray> {
            val cipher =
                initXChaCha20Poly1305(key, nonce, true).getOrElse { return Result.failure(it) }
            val out =
                ByteArray(XCHACHA20_POLY1305_NONCE_SIZE + cipher.getOutputSize(plaintext.size))
            nonce.copyInto(out, 0, 0, XCHACHA20_POLY1305_NONCE_SIZE)
            val outOff = cipher.processBytes(
                plaintext, 0, plaintext.size, out, XCHACHA20_POLY1305_NONCE_SIZE
            )
            cipher.doFinal(out, XCHACHA20_POLY1305_NONCE_SIZE + outOff)
            return Result.success(out)
        }

        fun decryptXChaCha20Poly1305(
            key: ByteArray, ciphertext: ByteArray
        ): Result<ByteArray> {
            if (ciphertext.size < XCHACHA20_POLY1305_NONCE_SIZE) {
                return Result.failure(IllegalArgumentException("ciphertext too short"))
            }
            val nonce = ciphertext.sliceArray(0..<XCHACHA20_POLY1305_NONCE_SIZE)
            val cipher =
                initXChaCha20Poly1305(key, nonce, false).getOrElse { return Result.failure(it) }
            val ciphertextSize = ciphertext.size - XCHACHA20_POLY1305_NONCE_SIZE
            val out = ByteArray(cipher.getOutputSize(ciphertextSize))
            val outOff = cipher.processBytes(
                ciphertext, XCHACHA20_POLY1305_NONCE_SIZE, ciphertextSize, out, 0
            )
            try {
                cipher.doFinal(out, outOff)
            } catch (e: InvalidCipherTextException) {
                return Result.failure(e)
            }
            return Result.success(out)
        }
    }
}