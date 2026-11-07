package app.yechan.server.global.error

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Request is invalid."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access is denied."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Requested media type is not acceptable."),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with the current state."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Request payload is too large."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error."),
    ;

    override val code: String get() = name

    companion object {
        fun fromStatus(status: Int): CommonErrorCode =
            entries.firstOrNull { it.status.value() == status }
                ?: if (status >= 500) INTERNAL_ERROR else INVALID_INPUT
    }
}
