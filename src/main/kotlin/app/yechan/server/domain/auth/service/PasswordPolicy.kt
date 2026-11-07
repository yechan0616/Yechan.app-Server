package app.yechan.server.domain.auth.service

import app.yechan.server.global.error.FieldViolation
import org.springframework.stereotype.Component

@Component
class PasswordPolicy {
    fun check(
        password: String,
        email: String?,
        username: String?,
        field: String = "password",
    ): List<FieldViolation> {
        val length = password.codePointCount(0, password.length)
        val lowered = password.lowercase()
        val identifiers = listOfNotNull(email?.substringBefore('@'), username).filter { it.length >= 3 }
        return buildList {
            if (length !in
                MIN_LENGTH..MAX_LENGTH
            ) {
                add(FieldViolation(field, "must be $MIN_LENGTH-$MAX_LENGTH characters"))
            }
            if (password.isNotEmpty() &&
                password.all { it == password[0] }
            ) {
                add(FieldViolation(field, "must not repeat one character"))
            }
            if (lowered in COMMON) add(FieldViolation(field, "is too common"))
            if (identifiers.any {
                    lowered.contains(it.lowercase())
                }
            ) {
                add(FieldViolation(field, "must not contain your email or username"))
            }
        }
    }

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    private companion object {
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 128
        val COMMON =
            setOf(
                "password1234",
                "passwordpassword",
                "123456789012",
                "qwertyuiopas",
                "qwerty123456",
                "iloveyou1234",
                "adminadmin12",
                "administrator",
                "letmein12345",
                "welcome12345",
            )
    }
}
