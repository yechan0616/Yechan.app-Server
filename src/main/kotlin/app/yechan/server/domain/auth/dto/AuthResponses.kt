package app.yechan.server.domain.auth.dto

import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.entity.SignInMethod
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

data class SessionSummaryResponse(
    val id: UUID,
    val ipAddress: String?,
    val userAgent: String?,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val expiresAt: Instant,
    val current: Boolean,
)

data class SecurityEventResponse(
    val id: UUID,
    val type: SecurityEventType,
    val method: SignInMethod?,
    val ipAddress: String?,
    val userAgent: String?,
    val detail: String?,
    val createdAt: Instant,
)
