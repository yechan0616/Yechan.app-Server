package app.yechan.server.domain.auth

import app.yechan.server.domain.auth.dto.ClientContext
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.repository.PasskeyCeremonyRepository
import app.yechan.server.domain.auth.service.PasskeyCeremonies
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PasskeyCeremoniesTest : IntegrationTest() {
    @Autowired
    private lateinit var ceremonies: PasskeyCeremonies

    @Autowired
    private lateinit var repository: PasskeyCeremonyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `한 주소는 ceremony를 20개까지만 열 수 있고 다른 주소는 영향을 받지 않는다`() {
        repeat(PasskeyCeremonies.MAX_PENDING_PER_ADDRESS.toInt()) {
            beginSignIn("203.0.113.10").andExpect { status { isOk() } }
        }

        beginSignIn("203.0.113.10").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("AUTH_RATE_LIMITED") }
        }
        beginSignIn("203.0.113.11").andExpect { status { isOk() } }
    }

    @Test
    fun `전체 상한에 닿으면 가장 오래된 ceremony를 지우고 새로 연다`() {
        val oldest =
            TransactionTemplate(transactionManager).execute {
                (0 until PasskeyCeremonies.MAX_PENDING.toInt())
                    .map { index ->
                        ceremonies.open(
                            CeremonyPurpose.SIGN_IN,
                            challenge(),
                            ClientContext("10.0.${index / 20}.${index % 20}", null),
                        )
                    }.first()
                    .id
            }

        beginSignIn("203.0.113.12").andExpect { status { isOk() } }

        assertEquals(PasskeyCeremonies.MAX_PENDING, repository.count())
        assertFalse(repository.existsById(checkNotNull(oldest)))
    }

    private fun beginSignIn(address: String) =
        mockMvc.post("/api/v1/studio/auth/sign-in/options") {
            with { request ->
                request.remoteAddr = address
                request
            }
        }

    private fun challenge(): ByteArray = UUID.randomUUID().toString().toByteArray()
}
