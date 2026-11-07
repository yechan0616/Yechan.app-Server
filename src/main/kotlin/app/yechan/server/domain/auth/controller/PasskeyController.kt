package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.EnrollmentOptionsRequest
import app.yechan.server.domain.auth.dto.PasskeyCreationOptionsResponse
import app.yechan.server.domain.auth.dto.PasskeyResponse
import app.yechan.server.domain.auth.dto.RenamePasskeyRequest
import app.yechan.server.domain.auth.service.PasskeyManagement
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/studio/auth/passkeys")
class PasskeyController(
    private val passkeys: PasskeyManagement,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
    ): List<PasskeyResponse> = passkeys.list(principal)

    @PostMapping("/options")
    fun beginEnrollment(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @Valid @RequestBody request: EnrollmentOptionsRequest,
    ): PasskeyCreationOptionsResponse = passkeys.beginEnrollment(principal, request)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun completeEnrollment(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @Valid @RequestBody request: CeremonyCompletionRequest,
        http: HttpServletRequest,
    ): PasskeyResponse = passkeys.completeEnrollment(principal, request, http.client())

    @PatchMapping("/{passkeyId}")
    fun rename(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @PathVariable passkeyId: UUID,
        @Valid @RequestBody request: RenamePasskeyRequest,
    ): PasskeyResponse = passkeys.rename(principal, passkeyId, request.label)

    @DeleteMapping("/{passkeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @PathVariable passkeyId: UUID,
        http: HttpServletRequest,
    ) = passkeys.remove(principal, passkeyId, http.client())
}
