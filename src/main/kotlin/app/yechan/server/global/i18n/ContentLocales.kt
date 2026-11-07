package app.yechan.server.global.i18n

import org.springframework.stereotype.Component
import java.util.Locale

@Component
class ContentLocales(
    properties: ContentLocaleProperties,
) {
    val supported: List<String> = properties.supported
    val default: String = properties.default

    fun isSupported(tag: String): Boolean = tag in supported

    fun select(
        requested: String?,
        acceptLanguage: String?,
    ): String = fromTag(requested) ?: negotiate(acceptLanguage)

    fun fromTag(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val language = Locale.forLanguageTag(tag.trim().replace('_', '-')).language
        return language.takeIf { it in supported }
    }

    fun negotiate(acceptLanguage: String?): String {
        if (acceptLanguage.isNullOrBlank()) return default
        val ranges = runCatching { Locale.LanguageRange.parse(acceptLanguage) }.getOrNull() ?: return default
        return Locale.lookupTag(ranges, supported)?.let(::fromTag) ?: default
    }
}
