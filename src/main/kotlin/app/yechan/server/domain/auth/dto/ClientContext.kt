package app.yechan.server.domain.auth.dto

data class ClientContext(
    val ipAddress: String?,
    val userAgent: String?,
) {
    companion object {
        val UNKNOWN = ClientContext(null, null)
    }
}
