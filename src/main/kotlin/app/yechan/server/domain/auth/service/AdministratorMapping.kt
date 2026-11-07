package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.AdministratorResponse
import app.yechan.server.domain.auth.entity.Administrator

internal fun Administrator.toResponse() = AdministratorResponse(id, username, displayName, email, passwordEnabled)
