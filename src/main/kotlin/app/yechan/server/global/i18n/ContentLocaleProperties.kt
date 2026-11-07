package app.yechan.server.global.i18n

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.content.locales")
data class ContentLocaleProperties(
    val supported: List<String>,
    val default: String,
) {
    init {
        require(supported.isNotEmpty()) { "app.content.locales.supported must not be empty" }
        require(
            supported.all { LANGUAGE.matches(it) },
        ) { "Locales must be lowercase ISO 639 language codes: $supported" }
        require(supported.toSet().size == supported.size) { "app.content.locales.supported has duplicates" }
        require(default in supported) { "app.content.locales.default must be one of $supported" }
    }

    private companion object {
        val LANGUAGE = Regex("^[a-z]{2,3}$")
    }
}
