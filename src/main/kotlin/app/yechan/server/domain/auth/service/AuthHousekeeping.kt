package app.yechan.server.domain.auth.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AuthHousekeeping(
    private val ceremonies: PasskeyCeremonies,
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    fun purgeExpired() {
        val ceremonyCount = ceremonies.purgeExpired()
        val sessionCount = sessions.purgeExpired()
        events.purgeExpired()
        if (ceremonyCount + sessionCount > 0) {
            log.debug("Purged {} ceremonies and {} sessions", ceremonyCount, sessionCount)
        }
    }
}
