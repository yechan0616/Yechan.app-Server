package app.yechan.server.domain.auth.repository

import app.yechan.server.domain.auth.entity.Administrator
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdministratorRepository : JpaRepository<Administrator, UUID> {
    fun findByUsername(username: String): Administrator?

    fun findByEmail(email: String): Administrator?

    fun existsByEmailAndIdNot(
        email: String,
        id: UUID,
    ): Boolean

    fun countByPasswordHashIsNotNull(): Long
}
