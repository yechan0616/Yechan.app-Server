package app.yechan.server.domain.auth.repository

import app.yechan.server.domain.auth.entity.Passkey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PasskeyRepository : JpaRepository<Passkey, UUID> {
    @Query("select p from Passkey p join fetch p.administrator where p.credentialId = :credentialId")
    fun findByCredentialId(credentialId: ByteArray): Passkey?

    fun existsByCredentialId(credentialId: ByteArray): Boolean

    fun findAllByAdministratorIdOrderByCreatedAtAsc(administratorId: UUID): List<Passkey>

    fun findByIdAndAdministratorId(
        id: UUID,
        administratorId: UUID,
    ): Passkey?

    fun countByAdministratorId(administratorId: UUID): Long
}
