package app.yechan.server.domain.content.service

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Signing key lives only in memory: preview links expire anyway and die with a restart.
@Component
class PreviewTokens(
    private val clock: Clock,
) {
    private val key = SecretKeySpec(ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes), ALGORITHM)

    fun issue(entryId: UUID): IssuedPreview {
        val expiresAt = clock.instant().plus(TTL).epochSecond
        val payload = "$entryId.$expiresAt"
        return IssuedPreview(
            "${encode(payload.toByteArray())}.${encode(sign(payload))}",
            Instant.ofEpochSecond(expiresAt),
        )
    }

    fun verify(token: String): UUID? {
        val parts = token.split('.')
        if (parts.size != 2 || token.length > MAX_TOKEN_LENGTH) return null
        val payload = runCatching { String(DECODER.decode(parts[0])) }.getOrNull() ?: return null
        val signature = runCatching { DECODER.decode(parts[1]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(signature, sign(payload))) return null
        val (id, expiresAt) = payload.split('.').takeIf { it.size == 2 } ?: return null
        if (clock.instant().epochSecond >= (expiresAt.toLongOrNull() ?: return null)) return null
        return runCatching { UUID.fromString(id) }.getOrNull()
    }

    private fun sign(payload: String): ByteArray =
        Mac
            .getInstance(ALGORITHM)
            .apply {
                init(key)
            }.doFinal(payload.toByteArray())

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val KEY_BYTES = 32
        const val MAX_TOKEN_LENGTH = 256
        val TTL: Duration = Duration.ofMinutes(30)
        val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}

class IssuedPreview(
    val token: String,
    val expiresAt: Instant,
)
