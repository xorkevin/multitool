package dev.xorkevin.multitool

import org.junit.Assert
import org.junit.Test

class PGPToolTest {
    val testPublicKey = """
        -----BEGIN PGP PUBLIC KEY BLOCK-----

        mDMEaA3TdRYJKwYBBAHaRw8BAQdADYsvagEXqtrfQEldCMuc3rtF6/h81XTFavJA
        V4v7uC+0GkV4YW1wbGUgPHRlc3RAZXhhbXBsZS5jb20+iI8EExYKADcWIQRMZXMs
        8p7peWruBTo++zW4fERRpAUCaA3TdQIbAwQLCQgHBRUKCQgLBRYCAwEAAh4FAheA
        AAoJED77Nbh8RFGkrosBANcQ64SzIYNqsV+1A4mI6Jv8Px83HhC7bhXaSLHFmK/0
        AQC/rTSmJFJDLXAKjeKdnLYPzUzmdwQq01OPzTg1UTEmB7g4BGgN03USCisGAQQB
        l1UBBQEBB0CIiUC4cZ9bQudd6mtQklxpdxj4yJMOpVsMKM4uhe4OLwMBCAeIeAQY
        FgoAIBYhBExlcyzynul5au4FOj77Nbh8RFGkBQJoDdN1AhsMAAoJED77Nbh8RFGk
        27sA/jX27b7j0/oatt9yApD/NQZJGT16S10oqPgirKowCXCbAQCc8I036dGny3AB
        xq9b+EEq9Xngb3vHKa6xJfR95RCMDQ==
        =84a5
        -----END PGP PUBLIC KEY BLOCK-----
    """.trimIndent()

    val testPrivateKey = """
        -----BEGIN PGP PRIVATE KEY BLOCK-----

        lIYEaA3TdRYJKwYBBAHaRw8BAQdADYsvagEXqtrfQEldCMuc3rtF6/h81XTFavJA
        V4v7uC/+BwMCUHC4IKBRHVn/WuwpNUWagSEsA/GJt6uZOaPX5Z5eoYCxe8nspP3H
        rgMIzrEelceYPpt0Ego3Lu31455dreEVXPf3YCGyc/RXbdzrj0e/krQaRXhhbXBs
        ZSA8dGVzdEBleGFtcGxlLmNvbT6IjwQTFgoANxYhBExlcyzynul5au4FOj77Nbh8
        RFGkBQJoDdN1AhsDBAsJCAcFFQoJCAsFFgIDAQACHgUCF4AACgkQPvs1uHxEUaSu
        iwEA1xDrhLMhg2qxX7UDiYjom/w/HzceELtuFdpIscWYr/QBAL+tNKYkUkMtcAqN
        4p2ctg/NTOZ3BCrTU4/NODVRMSYHnIsEaA3TdRIKKwYBBAGXVQEFAQEHQIiJQLhx
        n1tC513qa1CSXGl3GPjIkw6lWwwozi6F7g4vAwEIB/4HAwIObBaHzE+JS/8kheF4
        BGWCZEOO24K/xb6D3E5RPnJ+4kLP5QhP5teoOzvJY1Ild72HFMXuSxolHFHRLCk8
        NqjvQqFXj5Puv3fOdiSbjw0miHgEGBYKACAWIQRMZXMs8p7peWruBTo++zW4fERR
        pAUCaA3TdQIbDAAKCRA++zW4fERRpNu7AP419u2+49P6GrbfcgKQ/zUGSRk9ektd
        KKj4IqyqMAlwmwEAnPCNN+nRp8twAcavW/hBKvV54G97xymusSX0feUQjA0=
        =stn4
        -----END PGP PRIVATE KEY BLOCK-----
    """.trimIndent()

    val testCiphertext = """
        -----BEGIN PGP MESSAGE-----

        hF4DbASE5vdi4fkSAQdAu65QyDTnM3sleTkDXArnKVoZ35U7DHnL7xxVovjLhlEw
        HIlhhSNaIDKFE2OtTI3z1Bqk4rS9snjY9NuYHyJxdTCC5CjI5U0REJxUEZg7rkHT
        0jwB2xP4B7xV/HqW9urVQUHePtct42SH/yCotz+MQKDSzR3UNhtoTxnuoIHTfecB
        Zz/hJxmcycwHJ0HoR8w=
        =koDB
        -----END PGP MESSAGE-----
    """.trimIndent()

    val testPassphrase = "passphrase123"

    @Test
    fun encryptAndDecryptMessage() {
        val publicKey = loadPublicKey(testPublicKey).getOrThrow()
        val secretKeyrings = loadSecretKeys(testPrivateKey).getOrThrow()
        val ciphertext = encryptMessage(publicKey, "test message").getOrThrow()
        Assert.assertEquals(
            "test message",
            decryptMessage(secretKeyrings, testPassphrase, ciphertext).getOrThrow()
        )
        Assert.assertEquals(
            "hello world",
            decryptMessage(secretKeyrings, testPassphrase, testCiphertext).getOrThrow()
        )
    }
}
