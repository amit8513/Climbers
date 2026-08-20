package com.example.climb.clubs

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Computes the HMAC-SHA256 tag-UID hash [WristbandCredential.tagUidHash] requires — always with
 * a server-held secret key, never a bare hash of the tag UID alone (a short NFC tag UID has
 * limited entropy and would be brute-forceable/rainbow-tableable if hashed unsalted). This
 * function itself is pure/deterministic; where the secret key actually lives (a server-side
 * secret manager, an environment variable on the verifying Cloud Function, etc.) is a Phase 7
 * deployment concern, out of scope here. */
object WristbandCredentialHashing {
    fun hmacSha256Hex(rawTagUid: String, serverSecret: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(serverSecret, "HmacSHA256"))
        val bytes = mac.doFinal(rawTagUid.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
