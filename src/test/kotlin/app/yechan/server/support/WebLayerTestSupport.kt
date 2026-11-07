package app.yechan.server.support

import app.yechan.server.global.config.CorsProperties
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.error.ErrorCode
import app.yechan.server.global.error.GlobalExceptionHandler
import app.yechan.server.global.security.ProblemDetailSecurityHandler
import app.yechan.server.global.security.SecurityConfig
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties::class)
@Import(GlobalExceptionHandler::class, SecurityConfig::class, ProblemDetailSecurityHandler::class)
class WebLayerTestConfig {
    @Bean
    fun clock(): Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
}

enum class ProbeErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    PROBE_NOT_FOUND(HttpStatus.NOT_FOUND, "Probe was not found."),
    ;

    override val code: String get() = name
}

data class ProbeRequest(
    @field:NotBlank val title: String,
)

@RestController
@RequestMapping("/api/v1")
class ProbeController {
    @GetMapping("/public/probe/business")
    fun business(): Nothing = throw BusinessException(ProbeErrorCode.PROBE_NOT_FOUND)

    @GetMapping("/public/probe/boom")
    fun boom(): Nothing = throw IllegalStateException("secret internal detail")

    @GetMapping("/public/probe/page")
    fun page(
        @RequestParam @Min(1) page: Int,
    ): Int = page

    @PostMapping("/studio/probe")
    fun create(
        @Valid @RequestBody request: ProbeRequest,
    ): String = request.title
}
