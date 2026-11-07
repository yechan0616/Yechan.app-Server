package app.yechan.server

import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class ServerApplicationTests : IntegrationTest() {
    @Test
    fun `헬스 체크는 인증 없이 UP을 반환한다`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `studio 경로는 세션 없이 401을 반환한다`() {
        mockMvc.get("/api/v1/studio/blueprints").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }
}
