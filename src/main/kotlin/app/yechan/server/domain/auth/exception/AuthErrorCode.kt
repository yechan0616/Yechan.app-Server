package app.yechan.server.domain.auth.exception

import app.yechan.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    AUTH_ONBOARDING_UNAVAILABLE(HttpStatus.FORBIDDEN, "Onboarding is closed."),
    AUTH_SETUP_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Setup token is invalid."),
    AUTH_CEREMONY_INVALID(HttpStatus.BAD_REQUEST, "Passkey ceremony is invalid or has expired."),
    AUTH_PASSKEY_REJECTED(HttpStatus.BAD_REQUEST, "Passkey response could not be verified."),
    AUTH_PASSKEY_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Passkey is already registered."),
    AUTH_PASSKEY_NOT_FOUND(HttpStatus.NOT_FOUND, "Passkey was not found."),
    AUTH_LAST_PASSKEY(HttpStatus.CONFLICT, "The last passkey cannot be removed."),
    AUTH_SIGN_IN_FAILED(HttpStatus.UNAUTHORIZED, "Sign-in failed."),
    AUTH_EMAIL_TAKEN(HttpStatus.CONFLICT, "Email is already in use."),
    AUTH_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "Set an email before enabling password sign-in."),
    AUTH_PASSWORD_WEAK(HttpStatus.BAD_REQUEST, "Password does not meet the policy."),
    AUTH_CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect."),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many pending passkey ceremonies."),
    ;

    override val code: String get() = name
}
