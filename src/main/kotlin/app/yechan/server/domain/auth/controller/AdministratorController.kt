package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.AdministratorResponse
import app.yechan.server.domain.auth.dto.ChangePasswordRequest
import app.yechan.server.domain.auth.dto.DisablePasswordRequest
import app.yechan.server.domain.auth.dto.UpdateAdministratorRequest
import app.yechan.server.domain.auth.service.AdministratorAccount
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/studio/auth")
class AdministratorController(
    private val account: AdministratorAccount,
) {
    @GetMapping("/administrator")
    fun get(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
    ): AdministratorResponse = account.get(principal)

    @PatchMapping("/administrator")
    fun update(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @Valid @RequestBody request: UpdateAdministratorRequest,
        http: HttpServletRequest,
    ): AdministratorResponse = account.update(principal, request, http.client())

    @PutMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @Valid @RequestBody request: ChangePasswordRequest,
        http: HttpServletRequest,
    ): AdministratorResponse = account.changePassword(principal, request, http.client())

    @DeleteMapping("/password")
    fun disablePassword(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
        @Valid @RequestBody request: DisablePasswordRequest,
        http: HttpServletRequest,
    ): AdministratorResponse = account.disablePassword(principal, request, http.client())
}
