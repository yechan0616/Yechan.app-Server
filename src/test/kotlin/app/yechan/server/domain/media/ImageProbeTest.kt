package app.yechan.server.domain.media

import app.yechan.server.domain.media.service.ImageFormat
import app.yechan.server.domain.media.service.ImageInfo
import app.yechan.server.domain.media.service.ImageProbe
import app.yechan.server.support.Images
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageProbeTest {
    private val probe = ImageProbe()

    @Test
    fun `PNG, JPEG, GIF의 형식과 크기를 헤더에서 읽는다`() {
        assertEquals(ImageInfo(ImageFormat.PNG, 320, 200), probe.inspect(Images.encode("png", 320, 200)))
        assertEquals(ImageInfo(ImageFormat.JPEG, 640, 480), probe.inspect(Images.encode("jpg", 640, 480)))
        assertEquals(ImageInfo(ImageFormat.GIF, 17, 9), probe.inspect(Images.encode("gif", 17, 9)))
    }

    @Test
    fun `WebP는 손실, 무손실, 확장 형식을 모두 읽는다`() {
        assertEquals(ImageInfo(ImageFormat.WEBP, 1200, 630), probe.inspect(Images.webpLossy(1200, 630)))
        assertEquals(ImageInfo(ImageFormat.WEBP, 1024, 768), probe.inspect(Images.webpLossless(1024, 768)))
        assertEquals(ImageInfo(ImageFormat.WEBP, 4000, 3000), probe.inspect(Images.webpExtended(4000, 3000)))
    }

    @Test
    fun `이미지가 아니거나 잘린 파일은 거부한다`() {
        assertNull(probe.inspect("<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray()))
        assertNull(probe.inspect("%PDF-1.7".toByteArray()))
        assertNull(probe.inspect(ByteArray(0)))
        assertNull(probe.inspect(Images.encode("png", 10, 10).copyOf(20)))
        assertNull(probe.inspect(Images.encode("jpg", 10, 10).copyOf(12)))
    }
}
