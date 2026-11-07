package app.yechan.server.domain.auth

import app.yechan.server.support.IntegrationTest
import app.yechan.server.support.PasskeyDevice
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccountSecurityTest : IntegrationTest() {
    @Test
    fun `로그인한 기기 목록을 보고 다른 기기를 로그아웃시킨다`() {
        val laptop = onboard()
        val phone = cookie(signIn(userAgent = "iPhone"))
        val tablet = cookie(signIn(userAgent = "iPad"))

        val sessions =
            json.readTree(
                mockMvc
                    .get("$AUTH/sessions") { cookie(laptop) }
                    .andReturn()
                    .response.contentAsString,
            )
        assertEquals(3, sessions.size())
        assertEquals(1, sessions.count { it["current"].booleanValue() })
        val phoneSession = sessions.first { it["userAgent"].stringValue() == "iPhone" }["id"].stringValue()

        mockMvc.delete("$AUTH/sessions/$phoneSession") { cookie(laptop) }.andExpect { status { isNoContent() } }
        mockMvc.get("$AUTH/session") { cookie(phone) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("$AUTH/session") { cookie(tablet) }.andExpect { status { isOk() } }

        mockMvc.delete("$AUTH/sessions") { cookie(laptop) }.andExpect { status { isNoContent() } }
        mockMvc.get("$AUTH/session") { cookie(tablet) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("$AUTH/session") { cookie(laptop) }.andExpect { status { isOk() } }
    }

    @Test
    fun `다른 관리자의 세션은 지울 수 없다`() {
        val session = onboard()
        mockMvc.delete("$AUTH/sessions/00000000-0000-0000-0000-000000000000") { cookie(session) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("AUTH_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `로그인 성공과 실패, 계정 변경이 보안 기록에 남는다`() {
        val session = onboard()
        signIn(password = "wrong-password-0000").andExpect { status { isUnauthorized() } }
        signIn()

        mockMvc.get("$AUTH/events") { cookie(session) }.andExpect {
            jsonPath("$.items[0].type") { value("SIGNED_IN") }
            jsonPath("$.items[0].method") { value("PASSWORD") }
            jsonPath("$.items[0].userAgent") { value("Test agent") }
            jsonPath("$.items[1].type") { value("SIGN_IN_FAILED") }
            jsonPath("$.items[2].type") { value("ONBOARDED") }
            jsonPath("$.items[2].ipAddress") { exists() }
        }
    }

    @Test
    fun `패스키가 있으면 현재 비밀번호를 확인한 뒤 비밀번호 로그인을 끈다`() {
        val session = onboardWithPasskey()
        mockMvc.patch("$AUTH/administrator") {
            cookie(session)
            json("""{"email":"me@yechan.app"}""")
        }
        mockMvc.put("$AUTH/password") {
            cookie(session)
            json("""{"newPassword":"$PASSWORD"}""")
        }

        disable(session, "wrong").andExpect { jsonPath("$.code") { value("AUTH_CURRENT_PASSWORD_INCORRECT") } }
        disable(session, PASSWORD).andExpect { jsonPath("$.passwordEnabled") { value(false) } }
        signIn().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `비밀번호가 유일한 로그인 수단이면 끌 수 없다`() {
        val session = onboard()
        disable(session, PASSWORD).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("AUTH_LAST_CREDENTIAL") }
        }
    }

    private fun onboard(): Cookie =
        cookie(
            mockMvc.post("$AUTH/onboarding/password") {
                header(SETUP_HEADER, SETUP_TOKEN)
                header(HttpHeaders.USER_AGENT, "Mac")
                json("""{"username":"yechan","displayName":"Yechan","email":"me@yechan.app","password":"$PASSWORD"}""")
            },
        )

    private fun onboardWithPasskey(): Cookie {
        val device = PasskeyDevice(json)
        val options =
            json.readTree(
                mockMvc
                    .post("$AUTH/onboarding/options") {
                        header(SETUP_HEADER, SETUP_TOKEN)
                        json("""{"username":"yechan","displayName":"Yechan"}""")
                    }.andReturn()
                    .response.contentAsString,
            )
        val credential = device.create(options["publicKey"])
        val body =
            json.writeValueAsString(
                mapOf(
                    "ceremonyId" to options["ceremonyId"].stringValue(),
                    "credential" to credential,
                ),
            )
        return cookie(
            mockMvc.post("$AUTH/onboarding") {
                header(SETUP_HEADER, SETUP_TOKEN)
                json(body)
            },
        )
    }

    private fun signIn(
        password: String = PASSWORD,
        userAgent: String = "Test agent",
    ): ResultActionsDsl =
        mockMvc.post("$AUTH/sign-in/password") {
            header(HttpHeaders.USER_AGENT, userAgent)
            json("""{"email":"me@yechan.app","password":"$password"}""")
        }

    private fun disable(
        session: Cookie,
        current: String,
    ): ResultActionsDsl =
        mockMvc.delete("$AUTH/password") {
            cookie(session)
            json("""{"currentPassword":"$current"}""")
        }

    private fun cookie(result: ResultActionsDsl): Cookie {
        val header = assertNotNull(result.andReturn().response.getHeader(HttpHeaders.SET_COOKIE))
        return Cookie(COOKIE, header.substringAfter("$COOKIE=").substringBefore(';'))
    }

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private companion object {
        const val AUTH = "/api/v1/studio/auth"
        const val COOKIE = "studio_session"
        const val SETUP_HEADER = "X-Setup-Token"
        const val SETUP_TOKEN = "test-setup-token-0123456789abcdef0123"
        const val PASSWORD = "correct-horse-battery"
    }
}
