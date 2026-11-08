package app.yechan.server.domain.auth

import app.yechan.server.support.IntegrationTest
import app.yechan.server.support.PasskeyDevice
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordAuthTest : IntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `이메일과 비밀번호로 첫 관리자를 만들고 바로 로그인된다`() {
        val session =
            onboardWithPassword()
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.administrator.email") { value("me@yechan.app") }
                    jsonPath("$.administrator.passwordEnabled") { value(true) }
                }.let(::cookie)

        mockMvc.get("$AUTH/session") { cookie(session) }.andExpect { status { isOk() } }
        mockMvc.get("$AUTH/onboarding").andExpect { jsonPath("$.available") { value(false) } }
        onboardWithPassword().andExpect { jsonPath("$.code") { value("AUTH_ONBOARDING_UNAVAILABLE") } }
    }

    @Test
    fun `비밀번호는 Argon2로 해시되어 저장된다`() {
        onboardWithPassword()
        val hash = assertNotNull(jdbc.queryForObject("select password_hash from administrator", String::class.java))
        assertTrue(hash.startsWith("{argon2}\$argon2id\$"), hash)
    }

    @Test
    fun `이메일은 대소문자와 공백을 무시하고 비밀번호로 로그인한다`() {
        onboardWithPassword()
        signIn("  ME@Yechan.app ", PASSWORD).andExpect {
            status { isOk() }
            jsonPath("$.administrator.username") { value("yechan") }
            header { exists(HttpHeaders.SET_COOKIE) }
        }
    }

    @Test
    fun `틀린 비밀번호와 없는 이메일은 같은 응답을 받는다`() {
        onboardWithPassword()
        listOf("me@yechan.app" to "wrong-password-123", "nobody@yechan.app" to PASSWORD).forEach { (email, password) ->
            signIn(email, password).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_SIGN_IN_FAILED") }
            }
        }
    }

    @Test
    fun `다섯 번 틀리면 올바른 비밀번호로도 잠시 로그인할 수 없다`() {
        onboardWithPassword()
        repeat(5) { signIn("me@yechan.app", "wrong-password-$it-x") }
        signIn("me@yechan.app", PASSWORD).andExpect { jsonPath("$.code") { value("AUTH_SIGN_IN_FAILED") } }

        jdbc.update("update administrator set locked_until = now() - interval '1 second'")
        signIn("me@yechan.app", PASSWORD).andExpect { status { isOk() } }
    }

    @Test
    fun `약한 비밀번호는 이유와 함께 거절한다`() {
        onboardWithPassword(password = "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("password") }
        }
        onboardWithPassword(
            password = "yechan-is-my-name",
        ).andExpect { jsonPath("$.code") { value("AUTH_PASSWORD_WEAK") } }
        onboardWithPassword(
            password = "aaaaaaaaaaaaaa",
        ).andExpect { jsonPath("$.code") { value("AUTH_PASSWORD_WEAK") } }
    }

    @Test
    fun `패스키로 가입한 관리자는 이메일을 정한 뒤 비밀번호 로그인을 켤 수 있다`() {
        val session = onboardWithPasskey()

        put("$AUTH/password", """{"newPassword":"$PASSWORD"}""", session).andExpect {
            jsonPath("$.code") { value("AUTH_EMAIL_REQUIRED") }
        }
        mockMvc
            .patch("$AUTH/administrator") {
                cookie(session)
                json("""{"email":"Me@Yechan.app","displayName":"Yechan Moon"}""")
            }.andExpect {
                jsonPath("$.email") { value("me@yechan.app") }
                jsonPath("$.displayName") { value("Yechan Moon") }
            }
        put("$AUTH/password", """{"newPassword":"$PASSWORD"}""", session).andExpect {
            jsonPath("$.passwordEnabled") { value(true) }
        }

        signIn("me@yechan.app", PASSWORD).andExpect { status { isOk() } }
    }

    @Test
    fun `이메일은 앞뒤 공백을 지우고 소문자로 저장하며 형식이 틀리면 400이다`() {
        val session = onboardWithPasskey()

        mockMvc
            .patch("$AUTH/administrator") {
                cookie(session)
                json("""{"email":"  Me@Yechan.app  "}""")
            }.andExpect {
                status { isOk() }
                jsonPath("$.email") { value("me@yechan.app") }
            }
        listOf("not-an-email", "me@yechan", " @yechan.app ").forEach { email ->
            mockMvc
                .patch("$AUTH/administrator") {
                    cookie(session)
                    json("""{"email":"$email"}""")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_INPUT") }
                    jsonPath("$.errors[0].field") { value("email") }
                }
        }
    }

    @Test
    fun `비밀번호를 바꾸려면 현재 비밀번호가 필요하고 바꾸면 다른 세션은 끊긴다`() {
        val first = cookie(onboardWithPassword())
        val second = cookie(signIn("me@yechan.app", PASSWORD))

        put("$AUTH/password", """{"newPassword":"another-long-passphrase"}""", first)
            .andExpect { jsonPath("$.code") { value("AUTH_CURRENT_PASSWORD_INCORRECT") } }
        put("$AUTH/password", """{"currentPassword":"nope","newPassword":"another-long-passphrase"}""", first)
            .andExpect { jsonPath("$.code") { value("AUTH_CURRENT_PASSWORD_INCORRECT") } }
        put("$AUTH/password", """{"currentPassword":"$PASSWORD","newPassword":"another-long-passphrase"}""", first)
            .andExpect { status { isOk() } }

        mockMvc.get("$AUTH/session") { cookie(first) }.andExpect { status { isOk() } }
        mockMvc.get("$AUTH/session") { cookie(second) }.andExpect { status { isUnauthorized() } }
        signIn("me@yechan.app", PASSWORD).andExpect { status { isUnauthorized() } }
        signIn("me@yechan.app", "another-long-passphrase").andExpect { status { isOk() } }
    }

    @Test
    fun `비밀번호가 있으면 마지막 패스키도 지울 수 있다`() {
        val session = onboardWithPasskey()
        mockMvc.patch("$AUTH/administrator") {
            cookie(session)
            json("""{"email":"me@yechan.app"}""")
        }
        val passkeyId =
            json
                .readTree(
                    mockMvc
                        .get("$AUTH/passkeys") {
                            cookie(session)
                        }.andReturn()
                        .response.contentAsString,
                )[0]["id"]
                .stringValue()

        mockMvc
            .delete("$AUTH/passkeys/$passkeyId") {
                cookie(session)
            }.andExpect { jsonPath("$.code") { value("AUTH_LAST_PASSKEY") } }
        put("$AUTH/password", """{"newPassword":"$PASSWORD"}""", session)
        mockMvc.delete("$AUTH/passkeys/$passkeyId") { cookie(session) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `계정 API는 로그인이 필요하다`() {
        mockMvc.get("$AUTH/administrator").andExpect { status { isUnauthorized() } }
        mockMvc
            .put(
                "$AUTH/password",
            ) { json("""{"newPassword":"$PASSWORD"}""") }
            .andExpect { status { isUnauthorized() } }
    }

    private fun onboardWithPassword(password: String = PASSWORD): ResultActionsDsl =
        mockMvc.post("$AUTH/onboarding/password") {
            header(SETUP_HEADER, SETUP_TOKEN)
            json("""{"username":"yechan","displayName":"Yechan","email":"Me@Yechan.app","password":"$password"}""")
        }

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
        val body =
            json.writeValueAsString(
                mapOf(
                    "ceremonyId" to options["ceremonyId"].stringValue(),
                    "credential" to device.create(options["publicKey"]),
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
        email: String,
        password: String,
    ): ResultActionsDsl =
        mockMvc.post("$AUTH/sign-in/password") { json("""{"email":"$email","password":"$password"}""") }

    private fun put(
        path: String,
        body: String,
        session: Cookie,
    ): ResultActionsDsl =
        mockMvc.put(path) {
            cookie(session)
            json(body)
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
