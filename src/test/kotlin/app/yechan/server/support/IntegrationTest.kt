package app.yechan.server.support

import app.yechan.server.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var json: JsonMapper

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbc.execute(
            "truncate table studio_session, passkey_ceremony, passkey, administrator, entry, blueprint, asset cascade",
        )
    }
}
