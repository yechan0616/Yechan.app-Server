package app.yechan.server.domain.media

import app.yechan.server.domain.media.service.ImageFormat
import app.yechan.server.domain.media.service.ImageProbe
import app.yechan.server.domain.media.service.MetadataStripper
import app.yechan.server.support.Images
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetadataStripperTest {
    private val stripper = MetadataStripper()

    @Test
    fun `JPEG의 EXIF는 지우고 회전 값만 남긴다`() {
        val jpeg = withExif(Images.encode("jpg", 40, 20), orientation = 6, secret = "GPS 37.5665N 126.9780E")
        assertTrue(jpeg.containsText("GPS 37.5665N"))

        val stripped = stripper.strip(jpeg, ImageFormat.JPEG)

        assertFalse(stripped.bytes.containsText("GPS 37.5665N"))
        assertEquals(6, stripped.orientation)
        assertTrue(stripped.rotated)
        assertEquals(6, stripper.strip(stripped.bytes, ImageFormat.JPEG).orientation)
        assertNotNull(ImageIO.read(ByteArrayInputStream(stripped.bytes)))
        assertEquals(40, ImageProbe().inspect(stripped.bytes)?.width)
    }

    @Test
    fun `회전이 없는 JPEG는 EXIF 블록을 통째로 지운다`() {
        val stripped =
            stripper.strip(
                withExif(Images.encode("jpg", 8, 8), orientation = 1, secret = "Camera serial 1234"),
                ImageFormat.JPEG,
            )
        assertFalse(stripped.bytes.containsText("Exif"))
        assertFalse(stripped.bytes.containsText("Camera serial"))
        assertNotNull(ImageIO.read(ByteArrayInputStream(stripped.bytes)))
    }

    @Test
    fun `PNG의 텍스트 메타데이터 청크를 지운다`() {
        val png = withTextChunk(Images.encode("png", 10, 10), "Author", "home address")
        val stripped = stripper.strip(png, ImageFormat.PNG).bytes
        assertFalse(stripped.containsText("home address"))
        assertNotNull(ImageIO.read(ByteArrayInputStream(stripped)))
    }

    @Test
    fun `WebP의 EXIF와 XMP 청크를 지우고 플래그와 크기를 고친다`() {
        val webp = Images.webpExtended(100, 50, metadata = "location=secret")
        assertTrue(webp.containsText("location=secret"))

        val stripped = stripper.strip(webp, ImageFormat.WEBP).bytes

        assertFalse(stripped.containsText("location=secret"))
        assertEquals(0, stripped[20].toInt() and 0x0C)
        assertEquals(stripped.size - 8, ByteBuffer.wrap(stripped, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(100, ImageProbe().inspect(stripped)?.width)
    }

    private fun ByteArray.containsText(text: String) = String(this, Charsets.ISO_8859_1).contains(text)

    private fun withExif(
        jpeg: ByteArray,
        orientation: Int,
        secret: String,
    ): ByteArray {
        val description = secret.toByteArray() + 0
        val entries = 2
        val ifdSize = 2 + entries * 12 + 4
        val tiff =
            ByteBuffer
                .allocate(8 + ifdSize + description.size)
                .apply {
                    put("MM".toByteArray()).putShort(0x2A).putInt(8)
                    putShort(entries.toShort())
                    putShort(0x010E).putShort(2).putInt(description.size).putInt(8 + ifdSize)
                    putShort(0x0112)
                        .putShort(3)
                        .putInt(1)
                        .putShort(orientation.toShort())
                        .putShort(0)
                    putInt(0)
                    put(description)
                }.array()
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun withTextChunk(
        png: ByteArray,
        key: String,
        value: String,
    ): ByteArray {
        val data = key.toByteArray() + 0 + value.toByteArray()
        val type = "tEXt".toByteArray()
        val crc = CRC32().apply { update(type + data) }.value.toInt()
        val chunk =
            ByteBuffer
                .allocate(12 + data.size)
                .putInt(data.size)
                .put(type)
                .put(data)
                .putInt(crc)
                .array()
        return png.copyOfRange(0, 33) + chunk + png.copyOfRange(33, png.size)
    }
}
