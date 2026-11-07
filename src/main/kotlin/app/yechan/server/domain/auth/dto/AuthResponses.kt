package app.yechan.server.domain.auth.dto

import java.time.Instant
import java.util.UUID

data class OnboardingStatusResponse(
    val available: Boolean,
)

data class SessionResponse(
    val administrator: AdministratorResponse,
    val expiresAt: Instant,
)

data class AdministratorResponse(
    val id: UUID,
    val username: String,
    val displayName: String,
    val email: String?,
    val passwordEnabled: Boolean,
)

data class PasskeyResponse(
    val id: UUID,
    val label: String,
    val synced: Boolean,
    val transports: Set<String>,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)
