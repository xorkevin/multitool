package dev.xorkevin.multitool

import org.junit.Test

class GitToolTest {
    private val testPrivateKey = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABDJy8k98u
        lrUBjX9eETuvd4AAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIPThEM0ecQj4kscL
        V+JYUltE4IDwm9XMx5ft/BD5+mc2AAAAkMiOR7/RjG7JXutg2PGjtKLSq88AdFKaEPPGcR
        GN4X909YfdGR0TbMt6S5VEzH0/PV7JoPUFPMA4NciImG/ba2T/WfqHuKdJYId+75+15SHF
        K/SaAex6WUPhTxOUV1IRKW83dgBNtZGMBW+BNrRrDgRDqm2RZCKW78GQdX0LOmvVE2yjVM
        J+HJ6VHEnyT8lAdg==
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()
    private val testPassphrase = "passphrase123"

    @Test
    fun loadKey() {
        loadSSHPrivateKey(testPrivateKey, testPassphrase).getOrThrow()
    }
}