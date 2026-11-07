package app.yechan.server.global.error

open class BusinessException(
    val errorCode: ErrorCode,
    val detail: String = errorCode.message,
    val violations: List<FieldViolation> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(detail, cause)
