package app.yechan.server.global.error

import app.yechan.server.support.FIXED_INSTANT
import app.yechan.server.support.ProbeController
import app.yechan.server.support.WebLayerTestConfig
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [ProbeController::class])
@ActiveProfiles("test")
@Import(WebLayerTestConfig::class)
class GlobalExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `BusinessException은 ErrorCode의 상태와 code로 변환된다`() {
        mockMvc.get("/api/v1/public/probe/business").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("PROBE_NOT_FOUND") }
            jsonPath("$.detail") { value("Probe was not found.") }
            jsonPath("$.timestamp") { value(FIXED_INSTANT.toString()) }
        }
    }

    @Test
    fun `예상하지 못한 예외는 내부 메시지를 숨기고 500을 반환한다`() {
        mockMvc.get("/api/v1/public/probe/boom").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            content { string(not(containsString("secret internal detail"))) }
        }
    }

    @Test
    fun `제약 위반은 종류에 따라 400, 500 또는 409로 나뉜다`() {
        mockMvc.get("/api/v1/public/probe/constraint?kind=CHECK").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        mockMvc.get("/api/v1/public/probe/constraint?kind=NOT_NULL").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
        }
        listOf("UNIQUE", "FOREIGN_KEY", "OTHER").forEach { kind ->
            mockMvc.get("/api/v1/public/probe/constraint?kind=$kind").andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("CONFLICT") }
            }
        }
    }

    @Test
    fun `클라이언트가 연결을 끊으면 에러 응답을 만들지 않는다`() {
        mockMvc.get("/api/v1/public/probe/abort").andExpect {
            status { isOk() }
            content { string("") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMINISTRATOR"])
    fun `RequestBody 검증 실패는 필드별 errors와 함께 400을 반환한다`() {
        mockMvc
            .post("/api/v1/studio/probe") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":" "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("title") }
            }
    }

    @Test
    fun `메서드 파라미터 검증 실패는 400을 반환한다`() {
        mockMvc.get("/api/v1/public/probe/page?page=0").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("page") }
        }
    }

    @Test
    fun `타입 변환 실패도 공통 code를 가진다`() {
        mockMvc.get("/api/v1/public/probe/page?page=abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `존재하지 않는 공개 경로는 RESOURCE_NOT_FOUND를 반환한다`() {
        mockMvc.get("/api/v1/public/missing").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMINISTRATOR"])
    fun `지원하지 않는 메서드는 METHOD_NOT_ALLOWED를 반환한다`() {
        mockMvc.delete("/api/v1/public/probe/business").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
        }
    }
}
