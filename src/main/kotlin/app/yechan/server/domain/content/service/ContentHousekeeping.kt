package app.yechan.server.domain.content.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ContentHousekeeping(
    private val history: EntryHistory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT5M")
    fun purgeTrash() {
        val purged = history.purgeTrash()
        if (purged > 0) log.info("Purged {} entries from the trash", purged)
    }
}
