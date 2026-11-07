package app.yechan.server.global.error

import org.springframework.http.ProblemDetail
import java.time.Clock

object ProblemDetails {
    const val CODE = "code"
    const val TIMESTAMP = "timestamp"
    const val ERRORS = "errors"

    fun of(
        errorCode: ErrorCode,
        clock: Clock,
        detail: String = errorCode.message,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.status, detail).also {
            it.title = errorCode.status.reasonPhrase
            enrich(it, errorCode, clock)
        }

    fun enrich(
        problem: ProblemDetail,
        errorCode: ErrorCode,
        clock: Clock,
    ): ProblemDetail =
        problem.apply {
            setProperty(CODE, errorCode.code)
            setProperty(TIMESTAMP, clock.instant().toString())
        }
}

data class FieldViolation(
    val field: String,
    val reason: String,
)
