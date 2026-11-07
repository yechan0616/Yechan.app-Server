package app.yechan.server.global.error

import org.apache.catalina.connector.ClientAbortException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.ConstraintViolationException.ConstraintKind
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.Clock

@RestControllerAdvice
class GlobalExceptionHandler(
    private val clock: Clock,
) : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ProblemDetail> {
        log.debug("Business exception: {}", ex.errorCode.code, ex)
        val problem = ProblemDetails.of(ex.errorCode, clock, ex.detail)
        if (ex.violations.isNotEmpty()) problem.setProperty(ProblemDetails.ERRORS, ex.violations)
        return respond(problem)
    }

    // Let Spring Security turn these into 401/403.
    @ExceptionHandler(AuthenticationException::class, AccessDeniedException::class)
    fun rethrowSecurity(ex: RuntimeException): Nothing = throw ex

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        log.warn("Concurrent modification conflict: {}", ex.javaClass.simpleName)
        return respond(ProblemDetails.of(CommonErrorCode.CONFLICT, clock))
    }

    // Unique and foreign key violations are conflicts with existing data; check and not-null violations are bad input.
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ProblemDetail> {
        val violation =
            generateSequence<Throwable>(ex) {
                it.cause
            }.filterIsInstance<ConstraintViolationException>().firstOrNull()
        val errorCode =
            when (violation?.kind) {
                ConstraintKind.CHECK, ConstraintKind.NOT_NULL -> CommonErrorCode.INVALID_INPUT
                else -> CommonErrorCode.CONFLICT
            }
        log.warn("Data integrity violation: {} ({})", violation?.constraintName, errorCode.code)
        return respond(ProblemDetails.of(errorCode, clock))
    }

    // The client went away; there is nobody left to answer and nothing to fix on the server.
    @ExceptionHandler(ClientAbortException::class)
    fun handleClientAbort(ex: ClientAbortException): ResponseEntity<Unit>? {
        log.debug("Client aborted the connection", ex)
        return null
    }

    override fun handleAsyncRequestNotUsableException(
        ex: AsyncRequestNotUsableException,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.debug("Async response is no longer usable", ex)
        return null
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception", ex)
        return respond(ProblemDetails.of(CommonErrorCode.INTERNAL_ERROR, clock))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val violations =
            ex.bindingResult.fieldErrors.map { FieldViolation(it.field, it.defaultMessage ?: "invalid") } +
                ex.bindingResult.globalErrors.map { FieldViolation(it.objectName, it.defaultMessage ?: "invalid") }
        return invalidInput(violations)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val violations =
            ex.parameterValidationResults.flatMap { result ->
                val field = result.methodParameter.parameterName ?: "parameter"
                result.resolvableErrors.map { FieldViolation(field, it.defaultMessage ?: "invalid") }
            }
        return invalidInput(violations)
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorCode = CommonErrorCode.fromStatus(statusCode.value())
        val problem =
            (body as? ProblemDetail)?.let { ProblemDetails.enrich(it, errorCode, clock) }
                ?: ProblemDetails.of(errorCode, clock)
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    private fun invalidInput(violations: List<FieldViolation>): ResponseEntity<Any> {
        val problem = ProblemDetails.of(CommonErrorCode.INVALID_INPUT, clock)
        problem.setProperty(ProblemDetails.ERRORS, violations)
        return ResponseEntity.status(problem.status).body(problem)
    }

    private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(problem.status).body(problem)
}
