package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.SecurityEventResponse
import app.yechan.server.domain.auth.dto.SessionSummaryResponse
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.service.SecurityEvents
import app.yechan.server.domain.auth.service.StudioSessions
import app.yechan.server.global.common.response.PageResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/studio/auth")
class SessionController(
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
) {
    @GetMapping("/sessions")
    fun list(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
    ): List<SessionSummaryResponse> = sessions.list(principal)

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @PathVariable sessionId: UUID,
        http: HttpServletRequest,
    ) {
        sessions.revoke(principal, sessionId)
        events.record(SecurityEventType.SESSION_REVOKED, principal.id, http.client(), detail = sessionId.toString())
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeOthers(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        http: HttpServletRequest,
    ) {
        val count = sessions.revokeOthers(principal)
        events.record(SecurityEventType.SESSION_REVOKED, principal.id, http.client(), detail = "others: $count")
    }

    @GetMapping("/events")
    fun events(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "30") @Min(1) @Max(100) size: Int,
    ): PageResponse<SecurityEventResponse> = events.list(principal, page, size)
}
