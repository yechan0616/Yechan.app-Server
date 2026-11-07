package app.yechan.server.domain.content.controller

import app.yechan.server.domain.content.dto.LocalesResponse
import app.yechan.server.domain.content.dto.PublishedEntryResponse
import app.yechan.server.domain.content.service.PublishedContent
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.i18n.ContentLocales
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/v1/public")
class PublishedContentController(
    private val content: PublishedContent,
    private val locales: ContentLocales,
) {
    @GetMapping("/locales")
    fun locales(): ResponseEntity<LocalesResponse> = ResponseEntity.ok().cacheControl(SHARED).body(content.locales())

    @GetMapping("/collections/{key}")
    fun collection(
        @PathVariable key: String,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<PageResponse<PublishedEntryResponse>> =
        localized(locale, acceptLanguage) { content.collection(key, it, page, size) }

    @GetMapping("/collections/{key}/{slug}")
    fun entry(
        @PathVariable key: String,
        @PathVariable slug: String,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<PublishedEntryResponse> = localized(locale, acceptLanguage) { content.entry(key, slug, it) }

    @GetMapping("/singletons/{key}")
    fun singleton(
        @PathVariable key: String,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<PublishedEntryResponse> = localized(locale, acceptLanguage) { content.singleton(key, it) }

    // Only responses with an explicit ?locale= may be stored by shared caches.
    private fun <T : Any> localized(
        requested: String?,
        acceptLanguage: String?,
        load: (String) -> T,
    ): ResponseEntity<T> {
        val explicit = locales.fromTag(requested)
        val locale = explicit ?: locales.negotiate(acceptLanguage)
        return ResponseEntity
            .ok()
            .cacheControl(if (explicit != null) SHARED else PRIVATE)
            .header(HttpHeaders.CONTENT_LANGUAGE, locale)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
            .body(load(locale))
    }

    private companion object {
        val SHARED: CacheControl =
            CacheControl
                .maxAge(Duration.ofMinutes(1))
                .sMaxAge(Duration.ofMinutes(5))
                .staleWhileRevalidate(Duration.ofDays(1))
                .cachePublic()
        val PRIVATE: CacheControl = CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate()
    }
}
