package app.yechan.server.domain.auth.dto

import java.util.UUID

data class AdministratorPrincipal(
    val id: UUID,
    val username: String,
    val displayName: String,
    val sessionId: UUID,
)
