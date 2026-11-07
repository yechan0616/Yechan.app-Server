package app.yechan.server.domain.content

import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

class EntityLifecycleTest : IntegrationTest() {
    @Autowired
    private lateinit var blueprints: BlueprintRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `저장한 entity는 flush 전에 지워도 실제로 지워진다`() {
        TransactionTemplate(transactionManager).execute {
            val saved = blueprints.save(Blueprint("note", BlueprintKind.COLLECTION, "Notes", null, emptyList()))
            blueprints.delete(saved)
        }

        assertEquals(0, blueprints.count())
    }
}
