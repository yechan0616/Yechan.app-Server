package app.yechan.server.domain.auth

import app.yechan.server.support.IntegrationTest
import app.yechan.server.support.PasskeyDevice
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PasskeyAuthTest : IntegrationTest() {
    private val device by lazy { PasskeyDevice(json) }

    @Test
    fun `첫 관리자는 설정 토큰과 패스키로 등록하고 바로 로그인된다`() {
        mockMvc.get("$AUTH/onboarding").andExpect { jsonPath("$.available") { value(true) } }

        val session = onboard(device)

        mockMvc.get("$AUTH/session") { cookie(session) }.andExpect {
            status { isOk() }
            jsonPath("$.administrator.username") { value("yechan") }
            jsonPath("$.administrator.displayName") { value("Yechan") }
        }
        mockMvc.get("$AUTH/onboarding").andExpect { jsonPath("$.available") { value(false) } }
    }

    @Test
    fun `세션 쿠키는 HttpOnly, SameSite=Strict로 발급된다`() {
        val options = beginOnboarding()
        mockMvc
            .post("$AUTH/onboarding") {
                header(SETUP_HEADER, SETUP_TOKEN)
                jsonBody(completion(options, device.create(options["publicKey"])))
            }.andExpect {
                status { isCreated() }
                header { string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")) }
                header { string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")) }
                header { string(HttpHeaders.CACHE_CONTROL, "no-store") }
            }
    }

    @Test
    fun `설정 토큰이 없거나 틀리면 온보딩할 수 없다`() {
        mockMvc.post("$AUTH/onboarding/options") { jsonBody(ONBOARDING_REQUEST) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_SETUP_TOKEN_INVALID") }
        }
        mockMvc
            .post("$AUTH/onboarding/options") {
                header(SETUP_HEADER, "wrong-token-wrong-token-wrong-token-0000")
                jsonBody(ONBOARDING_REQUEST)
            }.andExpect { jsonPath("$.code") { value("AUTH_SETUP_TOKEN_INVALID") } }
    }

    @Test
    fun `패스키가 하나라도 있으면 온보딩은 닫힌다`() {
        onboard(device)
        mockMvc
            .post("$AUTH/onboarding/options") {
                header(SETUP_HEADER, SETUP_TOKEN)
                jsonBody(ONBOARDING_REQUEST)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_ONBOARDING_UNAVAILABLE") }
            }
    }

    @Test
    fun `한 번 쓴 챌린지는 다시 쓸 수 없다`() {
        val options = beginOnboarding()
        val credential = device.create(options["publicKey"])
        completeOnboarding(options, credential).andExpect { status { isCreated() } }

        val signIn = beginSignIn()
        val assertion = device.get(signIn["publicKey"])
        completeSignIn(signIn, assertion).andExpect { status { isOk() } }
        completeSignIn(signIn, assertion).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("AUTH_CEREMONY_INVALID") }
        }
    }

    @Test
    fun `등록한 패스키로 username 없이 로그인한다`() {
        onboard(device)

        val options = beginSignIn()
        assertEquals(0, options["publicKey"]["allowCredentials"].size())
        assertEquals("required", options["publicKey"]["userVerification"].stringValue())

        val result =
            completeSignIn(options, device.get(options["publicKey"])).andExpect {
                status { isOk() }
                jsonPath("$.administrator.username") { value("yechan") }
            }
        mockMvc.get("$AUTH/session") { cookie(sessionCookie(result)) }.andExpect { status { isOk() } }
    }

    @Test
    fun `등록되지 않은 기기로는 로그인할 수 없다`() {
        onboard(device)
        val stranger = PasskeyDevice(json)
        stranger.create(beginOnboardingUnchecked())

        val options = beginSignIn()
        completeSignIn(options, stranger.get(options["publicKey"])).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_SIGN_IN_FAILED") }
        }
    }

    @Test
    fun `허용되지 않은 출처에서 만든 응답은 거절된다`() {
        onboard(device)
        val phishing = PasskeyDevice(json, origin = "https://studio.yechan.app.evil.example")
        val options = beginSignIn()
        phishing.create(beginOnboardingUnchecked())
        completeSignIn(options, phishing.get(options["publicKey"])).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그아웃하면 세션이 폐기되고 쿠키가 지워진다`() {
        val session = onboard(device)
        mockMvc.post("$AUTH/sign-out") { cookie(session) }.andExpect {
            status { isNoContent() }
            header { string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")) }
        }
        mockMvc.get("$AUTH/session") { cookie(session) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그인한 관리자는 기기를 추가하고 관리할 수 있다`() {
        val session = onboard(device)
        val laptop = PasskeyDevice(json)

        val options = postJson("$AUTH/passkeys/options", """{"label":"Work laptop"}""", session)
        val created =
            mockMvc
                .post("$AUTH/passkeys") {
                    cookie(session)
                    jsonBody(completion(options, laptop.create(options["publicKey"])))
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.label") { value("Work laptop") }
                }.andReturn()
                .let { json.readTree(it.response.contentAsString) }

        mockMvc.get("$AUTH/passkeys") { cookie(session) }.andExpect { jsonPath("$.length()") { value(2) } }

        val laptopSignIn = beginSignIn()
        completeSignIn(laptopSignIn, laptop.get(laptopSignIn["publicKey"])).andExpect { status { isOk() } }

        mockMvc
            .delete("$AUTH/passkeys/${created["id"].stringValue()}") {
                cookie(session)
            }.andExpect { status { isNoContent() } }
        val last =
            json
                .readTree(
                    mockMvc
                        .get("$AUTH/passkeys") { cookie(session) }
                        .andReturn()
                        .response.contentAsString,
                ).first()["id"]
                .stringValue()
        mockMvc.delete("$AUTH/passkeys/$last") { cookie(session) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("AUTH_LAST_PASSKEY") }
        }
    }

    @Test
    fun `검증에 실패해도 챌린지는 소비된다`() {
        onboard(device)
        val stranger = PasskeyDevice(json)
        stranger.create(beginOnboardingUnchecked())
        val options = beginSignIn()

        completeSignIn(options, stranger.get(options["publicKey"])).andExpect { status { isUnauthorized() } }
        completeSignIn(options, device.get(options["publicKey"])).andExpect {
            jsonPath("$.code") { value("AUTH_CEREMONY_INVALID") }
        }
    }

    @Test
    fun `패스키 관리 API는 로그인이 필요하다`() {
        mockMvc.get("$AUTH/passkeys").andExpect { status { isUnauthorized() } }
        mockMvc.post("$AUTH/passkeys/options") { jsonBody("{}") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `위조된 세션 쿠키는 무시된다`() {
        onboard(device)
        mockMvc
            .get(
                "$AUTH/session",
            ) { cookie(Cookie(COOKIE, "A".repeat(43))) }
            .andExpect { status { isUnauthorized() } }
        mockMvc
            .get("$AUTH/session") {
                cookie(Cookie(COOKIE, "'; drop table administrator; --"))
            }.andExpect { status { isUnauthorized() } }
    }

    private fun onboard(device: PasskeyDevice): Cookie {
        val options = beginOnboarding()
        val result =
            completeOnboarding(
                options,
                device.create(options["publicKey"]),
            ).andExpect { status { isCreated() } }
        return sessionCookie(result)
    }

    private fun beginOnboarding(): JsonNode =
        json.readTree(
            mockMvc
                .post("$AUTH/onboarding/options") {
                    header(SETUP_HEADER, SETUP_TOKEN)
                    jsonBody(ONBOARDING_REQUEST)
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun beginOnboardingUnchecked(): JsonNode =
        json.readTree(
            """
            {"challenge":"AAAAAAAAAAAAAAAAAAAAAA","rp":{"id":"localhost","name":"test"},
             "user":{"id":"AAAAAAAAAAAAAAAAAAAAAA","name":"stranger","displayName":"Stranger"},"timeout":60000}
            """,
        )

    private fun completeOnboarding(
        options: JsonNode,
        credential: JsonNode,
    ): ResultActionsDsl =
        mockMvc.post("$AUTH/onboarding") {
            header(SETUP_HEADER, SETUP_TOKEN)
            header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_0)")
            jsonBody(completion(options, credential))
        }

    private fun beginSignIn(): JsonNode =
        json.readTree(
            mockMvc
                .post("$AUTH/sign-in/options")
                .andExpect {
                    status { isOk() }
                }.andReturn()
                .response.contentAsString,
        )

    private fun completeSignIn(
        options: JsonNode,
        credential: JsonNode,
    ): ResultActionsDsl = mockMvc.post("$AUTH/sign-in") { jsonBody(completion(options, credential)) }

    private fun postJson(
        path: String,
        body: String,
        session: Cookie,
    ): JsonNode =
        json.readTree(
            mockMvc
                .post(path) {
                    cookie(session)
                    jsonBody(body)
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun completion(
        options: JsonNode,
        credential: JsonNode,
    ): String =
        json.writeValueAsString(
            mapOf(
                "ceremonyId" to options["ceremonyId"].stringValue(),
                "credential" to credential,
            ),
        )

    private fun sessionCookie(result: ResultActionsDsl): Cookie {
        val header = assertNotNull(result.andReturn().response.getHeader(HttpHeaders.SET_COOKIE))
        val token = header.substringAfter("$COOKIE=").substringBefore(';')
        return Cookie(COOKIE, token)
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.jsonBody(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private companion object {
        const val AUTH = "/api/v1/studio/auth"
        const val COOKIE = "studio_session"
        const val SETUP_HEADER = "X-Setup-Token"
        const val SETUP_TOKEN = "test-setup-token-0123456789abcdef0123"
        const val ONBOARDING_REQUEST = """{"username":"yechan","displayName":"Yechan"}"""
    }
}
