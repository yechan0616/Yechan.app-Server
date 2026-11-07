package app.yechan.server.global.security

import app.yechan.server.support.ProbeController
import app.yechan.server.support.WebLayerTestConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [ProbeController::class])
@ActiveProfiles("test")
@Import(WebLayerTestConfig::class)
class SecurityConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `studio API는 인증이 없으면 Problem Details 401을 반환한다`() {
        mockMvc
            .post("/api/v1/studio/probe") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"x"}"""
            }.andExpect {
                status { isUnauthorized() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `public 경로라도 GET 이외의 메서드는 인증이 필요하다`() {
        mockMvc.post("/api/v1/public/probe/business").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `허용된 Origin의 preflight 요청은 통과한다`() {
        mockMvc
            .options("/api/v1/public/probe/business") {
                header(HttpHeaders.ORIGIN, "https://www.yechan.app")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.yechan.app") }
            }
    }

    @Test
    fun `허용되지 않은 Origin의 preflight 요청은 거부된다`() {
        mockMvc
            .options("/api/v1/public/probe/business") {
                header(HttpHeaders.ORIGIN, "https://evil.example")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `보안 헤더가 기본으로 포함된다`() {
        mockMvc.get("/api/v1/public/probe/business").andExpect {
            header { string("X-Content-Type-Options", "nosniff") }
            header { exists("X-Frame-Options") }
        }
    }
}
