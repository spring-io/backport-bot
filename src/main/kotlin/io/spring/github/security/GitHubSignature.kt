package io.spring.github.security

import org.springframework.security.crypto.codec.Hex
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


/**
 * @author Rob Winch
 */
class GitHubSignature(val key : String) {

    fun check(gitHubSignature: String?, body: String?): Boolean {
        if (gitHubSignature == null || body == null || !gitHubSignature.startsWith(SIGNATURE_PREFIX)) {
            return false
        }

        val providedHmac = gitHubSignature.substring(SIGNATURE_PREFIX.length)

        val providedHmacBytes = Hex.decode(providedHmac)

        val expectedBytes = sign(body!!, key)

        return MessageDigest.isEqual(providedHmacBytes, expectedBytes)
    }

    fun create(body: String, token: String): String {
        return SIGNATURE_PREFIX + String(Hex.encode(sign(body, token)))
    }

    private fun sign(body: String, token: String): ByteArray {
        val signingKey = SecretKeySpec(token.toByteArray(Charsets.UTF_8), HMAC_SHA1_ALGORITHM);
        val mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        val expectedBytes = mac.doFinal(body.toByteArray(Charsets.UTF_8));
        return expectedBytes;
    }

    companion object {
        internal val SIGNATURE_PREFIX = "sha1="

        private val HMAC_SHA1_ALGORITHM = "HmacSHA1"
    }
}