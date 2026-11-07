package app.yechan.server.domain.media.service

import org.springframework.stereotype.Component

enum class ImageFormat(
    val contentType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
}

data class ImageInfo(
    val format: ImageFormat,
    val width: Int,
    val height: Int,
)

// Reads only file headers; never decodes pixels, so oversized or malicious images cost nothing.
@Component
class ImageProbe {
    fun inspect(bytes: ByteArray): ImageInfo? =
        runCatching {
            when {
                bytes.startsWith(PNG_SIGNATURE) -> png(bytes)
                bytes.startsWith(GIF87) || bytes.startsWith(GIF89) -> gif(bytes)
                bytes.startsWith(JPEG_SIGNATURE) -> jpeg(bytes)
                bytes.startsWith(RIFF) && bytes.startsWith(WEBP, offset = 8) -> webp(bytes)
                else -> null
            }
        }.getOrNull()

    private fun png(bytes: ByteArray): ImageInfo? {
        if (!bytes.startsWith(IHDR, offset = 12)) return null
        return ImageInfo(ImageFormat.PNG, bytes.int32BigEndian(16), bytes.int32BigEndian(20))
    }

    private fun gif(bytes: ByteArray) =
        ImageInfo(ImageFormat.GIF, bytes.uint16LittleEndian(6), bytes.uint16LittleEndian(8))

    private fun jpeg(bytes: ByteArray): ImageInfo? {
        var offset = 2
        while (offset + 8 < bytes.size) {
            if (bytes.u8(offset) != 0xFF) return null
            val marker = bytes.u8(offset + 1)
            when {
                marker == 0xFF -> {
                    offset++
                }

                marker == 0x01 || marker in 0xD0..0xD7 -> {
                    offset += 2
                }

                marker == 0xD9 || marker == 0xDA -> {
                    return null
                }

                marker in 0xC0..0xCF && marker !in setOf(0xC4, 0xC8, 0xCC) -> {
                    return ImageInfo(
                        ImageFormat.JPEG,
                        bytes.uint16BigEndian(offset + 7),
                        bytes.uint16BigEndian(offset + 5),
                    )
                }

                else -> {
                    offset += 2 + bytes.uint16BigEndian(offset + 2)
                }
            }
        }
        return null
    }

    private fun webp(bytes: ByteArray): ImageInfo? =
        when {
            bytes.startsWith(VP8X, offset = 12) -> {
                ImageInfo(ImageFormat.WEBP, 1 + bytes.uint24LittleEndian(24), 1 + bytes.uint24LittleEndian(27))
            }

            bytes.startsWith(VP8L, offset = 12) && bytes.u8(20) == 0x2F -> {
                val b1 = bytes.u8(21)
                val b2 = bytes.u8(22)
                val b3 = bytes.u8(23)
                val b4 = bytes.u8(24)
                ImageInfo(
                    ImageFormat.WEBP,
                    1 + (((b2 and 0x3F) shl 8) or b1),
                    1 + (((b4 and 0x0F) shl 10) or (b3 shl 2) or ((b2 and 0xC0) shr 6)),
                )
            }

            bytes.startsWith(VP8, offset = 12) && bytes.startsWith(VP8_START_CODE, offset = 23) -> {
                ImageInfo(
                    ImageFormat.WEBP,
                    bytes.uint16LittleEndian(26) and 0x3FFF,
                    bytes.uint16LittleEndian(28) and 0x3FFF,
                )
            }

            else -> {
                null
            }
        }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val IHDR = "IHDR".toByteArray()
        val GIF87 = "GIF87a".toByteArray()
        val GIF89 = "GIF89a".toByteArray()
        val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val RIFF = "RIFF".toByteArray()
        val WEBP = "WEBP".toByteArray()
        val VP8 = "VP8 ".toByteArray()
        val VP8L = "VP8L".toByteArray()
        val VP8X = "VP8X".toByteArray()
        val VP8_START_CODE = byteArrayOf(0x9D.toByte(), 0x01, 0x2A)

        fun ByteArray.startsWith(
            prefix: ByteArray,
            offset: Int = 0,
        ): Boolean = size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

        fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

        fun ByteArray.uint16BigEndian(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

        fun ByteArray.uint16LittleEndian(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

        fun ByteArray.uint24LittleEndian(index: Int): Int = u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16)

        fun ByteArray.int32BigEndian(index: Int): Int = (uint16BigEndian(index) shl 16) or uint16BigEndian(index + 2)
    }
}
