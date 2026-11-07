package app.yechan.server.global.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentLocalesTest {
    private val locales = ContentLocales(ContentLocaleProperties(listOf("en", "ko"), "en"))

    @ParameterizedTest
    @CsvSource("ko, ko", "ko-KR, ko", "KO, ko", "ko_KR, ko", "en-US, en", "'  ko ', ko")
    fun `지원하는 언어 태그를 언어 코드로 바꾼다`(
        tag: String,
        expected: String,
    ) {
        assertEquals(expected, locales.fromTag(tag))
    }

    @Test
    fun `지원하지 않거나 빈 태그는 null이다`() {
        assertNull(locales.fromTag("ja"))
        assertNull(locales.fromTag(" "))
        assertNull(locales.fromTag(null))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "ko-KR,ko;q=0.9,en-US;q=0.8 | ko",
            "en-US,en;q=0.9,ko;q=0.8 | en",
            "ja-JP,ja;q=0.9,ko;q=0.5 | ko",
            "ja-JP | en",
            "* | en",
            ";;;invalid | en",
        ],
    )
    fun `Accept-Language로 협상하고 실패하면 기본 언어를 쓴다`(
        header: String,
        expected: String,
    ) {
        assertEquals(expected, locales.negotiate(header))
    }

    @Test
    fun `명시한 locale이 Accept-Language보다 우선한다`() {
        assertEquals("en", locales.select("en", "ko-KR"))
        assertEquals("ko", locales.select("ja", "ko-KR"))
    }

    @Test
    fun `잘못된 설정은 기동 시점에 거부된다`() {
        assertThrows<IllegalArgumentException> { ContentLocaleProperties(emptyList(), "en") }
        assertThrows<IllegalArgumentException> { ContentLocaleProperties(listOf("en"), "ko") }
        assertThrows<IllegalArgumentException> { ContentLocaleProperties(listOf("en", "EN-us"), "en") }
    }
}
