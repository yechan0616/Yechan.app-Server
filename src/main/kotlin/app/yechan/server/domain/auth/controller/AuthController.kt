package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.OnboardingOptionsRequest
import app.yechan.server.domain.auth.dto.OnboardingStatusResponse
import app.yechan.server.domain.auth.dto.PasskeyCreationOptionsResponse
import app.yechan.server.domain.auth.dto.PasskeyRequestOptionsResponse
import app.yechan.server.domain.auth.dto.PasswordOnboardingRequest
import app.yechan.server.domain.auth.dto.PasswordSignInRequest
import app.yechan.server.domain.auth.dto.SessionResponse
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.service.IssuedSession
import app.yechan.server.domain.auth.service.Onboarding
import app.yechan.server.domain.auth.service.PasswordSignIn
import app.yechan.server.domain.auth.service.SecurityEvents
import app.yechan.server.domain.auth.service.SignIn
import app.yechan.server.domain.auth.service.StudioSessions
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.error.CommonErrorCode
import app.yechan.server.global.security.SecurityConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/studio/auth")
class AuthController(
    private val onboarding: Onboarding,
    private val signIn: SignIn,
    private val passwordSignIn: PasswordSignIn,
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
    private val cookies: SessionCookies,
) {
    @GetMapping("/onboarding")
    fun onboardingStatus(): OnboardingStatusResponse = onboarding.status()

    @PostMapping("/onboarding/options")
    fun beginOnboarding(
        @RequestHeader(SecurityConfig.SETUP_TOKEN_HEADER, required = false) setupToken: String?,
        @Valid @RequestBody request: OnboardingOptionsRequest,
        http: HttpServletRequest,
    ): PasskeyCreationOptionsResponse = onboarding.begin(setupToken, request, http.client())

    @PostMapping("/onboarding")
    fun completeOnboarding(
        @RequestHeader(SecurityConfig.SETUP_TOKEN_HEADER, required = false) setupToken: String?,
        @Valid @RequestBody request: CeremonyCompletionRequest,
        http: HttpServletRequest,
    ): ResponseEntity<SessionResponse> =
        signedIn(HttpStatus.CREATED, onboarding.complete(setupToken, request, http.client()))

    @PostMapping("/onboarding/password")
    fun completePasswordOnboarding(
        @RequestHeader(SecurityConfig.SETUP_TOKEN_HEADER, required = false) setupToken: String?,
        @Valid @RequestBody request: PasswordOnboardingRequest,
        http: HttpServletRequest,
    ): ResponseEntity<SessionResponse> =
        signedIn(HttpStatus.CREATED, onboarding.completeWithPassword(setupToken, request, http.client()))

    @PostMapping("/sign-in/options")
    fun beginSignIn(http: HttpServletRequest): PasskeyRequestOptionsResponse = signIn.begin(http.client())

    @PostMapping("/sign-in")
    fun completeSignIn(
        @Valid @RequestBody request: CeremonyCompletionRequest,
        http: HttpServletRequest,
    ): ResponseEntity<SessionResponse> = signedIn(HttpStatus.OK, signIn.complete(request, http.client()))

    @PostMapping("/sign-in/password")
    fun signInWithPassword(
        @Valid @RequestBody request: PasswordSignInRequest,
        http: HttpServletRequest,
    ): ResponseEntity<SessionResponse> = signedIn(HttpStatus.OK, passwordSignIn.signIn(request, http.client()))

    @PostMapping("/sign-out")
    fun signOut(
        @AuthenticationPrincipal principal: AdministratorPrincipal?,
        http: HttpServletRequest,
    ): ResponseEntity<Unit> {
        principal?.let {
            sessions.revoke(it.sessionId)
            events.record(SecurityEventType.SIGNED_OUT, it.id, http.client())
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build()
    }

    @GetMapping("/session")
    fun session(
        @AuthenticationPrincipal principal: AdministratorPrincipal,
    ): SessionResponse = sessions.describe(principal.sessionId) ?: throw BusinessException(CommonErrorCode.UNAUTHORIZED)

    private fun signedIn(
        status: HttpStatus,
        issued: IssuedSession,
    ): ResponseEntity<SessionResponse> =
        ResponseEntity
            .status(status)
            .header(HttpHeaders.SET_COOKIE, cookies.issue(issued.token))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(issued.session)
}
