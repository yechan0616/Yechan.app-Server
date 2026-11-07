package app.yechan.server.domain.content

import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TryCastFunctionsTest : IntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `try_timestamptz는 세션 시간대에 따르므로 stable이고 try_numeric은 immutable이다`() {
        assertEquals("s", volatility("try_timestamptz"))
        assertEquals("i", volatility("try_numeric"))
    }

    @Test
    fun `캐스트할 수 없는 값은 null이 된다`() {
        assertNull(jdbc.queryForObject("select try_numeric('abc')", BigDecimal::class.java))
        assertNull(jdbc.queryForObject("select try_timestamptz('not a date')", OffsetDateTime::class.java))
        assertEquals(BigDecimal("42"), jdbc.queryForObject("select try_numeric('42')", BigDecimal::class.java))
    }

    private fun volatility(name: String): String =
        checkNotNull(jdbc.queryForObject("select provolatile from pg_proc where proname = ?", String::class.java, name))
}
