package app.yechan.server.domain.media.service

import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

class StrippedImage(
    val bytes: ByteArray,
    val orientation: Int,
) {
    val rotated: Boolean get() = orientation in 5..8
}

// Drops EXIF, XMP, IPTC and text chunks byte-for-byte (no re-encoding), keeping only the JPEG orientation.
@Component
class MetadataStripper {
    fun strip(
        bytes: ByteArray,
        format: ImageFormat,
    ): StrippedImage =
        when (format) {
            ImageFormat.JPEG -> jpeg(bytes)
            ImageFormat.PNG -> StrippedImage(png(bytes), 1)
            ImageFormat.WEBP -> StrippedImage(webp(bytes), 1)
            ImageFormat.GIF -> StrippedImage(bytes, 1)
        }

    private fun jpeg(bytes: ByteArray): StrippedImage {
        val out = ByteArrayOutputStream(bytes.size)
        var orientation = 1
        var offset = 2
        val kept = ByteArrayOutputStream()
        while (offset + 4 <= bytes.size) {
            if (bytes.u8(offset) != 0xFF) break
            val marker = bytes.u8(offset + 1)
            if (marker == 0xFF) {
                offset++
                continue
            }
            if (marker == SOS || marker == EOI || marker in 0xD0..0xD7 || marker == 0x01) break
            val length = bytes.u16be(offset + 2)
            val end = offset + 2 + length
            require(length >= 2 && end <= bytes.size) { "Corrupt JPEG segment" }
            val segment = bytes.copyOfRange(offset, end)
            val dropped =
                when (marker) {
                    APP1 -> {
                        if (bytes.startsWith(EXIF_HEADER, offset + 4)) {
                            orientation =
                                exifOrientation(bytes, offset + 4 + EXIF_HEADER.size, end)
                        }
                        true
                    }

                    APP13, COM -> {
                        true
                    }

                    else -> {
                        false
                    }
                }
            if (!dropped) kept.write(segment)
            offset = end
        }
        out.write(bytes, 0, 2)
        if (orientation in 2..8) out.write(orientationSegment(orientation))
        out.write(kept.toByteArray())
        out.write(bytes, offset, bytes.size - offset)
        return StrippedImage(out.toByteArray(), orientation)
    }

    private fun exifOrientation(
        bytes: ByteArray,
        tiff: Int,
        end: Int,
    ): Int =
        runCatching {
            val bigEndian =
                when {
                    bytes[tiff] == 'M'.code.toByte() && bytes[tiff + 1] == 'M'.code.toByte() -> true
                    bytes[tiff] == 'I'.code.toByte() && bytes[tiff + 1] == 'I'.code.toByte() -> false
                    else -> return 1
                }
            val u16 = { at: Int -> if (bigEndian) bytes.u16be(at) else bytes.u8(at) or (bytes.u8(at + 1) shl 8) }
            val u32 = { at: Int -> if (bigEndian) (u16(at) shl 16) or u16(at + 2) else u16(at) or (u16(at + 2) shl 16) }
            val ifd = tiff + u32(tiff + 4)
            val count = u16(ifd)
            (0 until count)
                .map { ifd + 2 + it * 12 }
                .filter { it + 12 <= end }
                .firstOrNull { u16(it) == ORIENTATION_TAG }
                ?.let { u16(it + 8) }
                ?.takeIf { it in 1..8 } ?: 1
        }.getOrDefault(1)

    private fun orientationSegment(orientation: Int): ByteArray {
        val tiff =
            byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 0x2A, 0, 0, 0, 8) +
                byteArrayOf(0, 1) +
                byteArrayOf(0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0) +
                byteArrayOf(0, 0, 0, 0)
        val payload = EXIF_HEADER + tiff
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), APP1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private fun png(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, PNG_SIGNATURE_LENGTH)
        var offset = PNG_SIGNATURE_LENGTH
        while (offset + 12 <= bytes.size) {
            val length = (bytes.u16be(offset) shl 16) or bytes.u16be(offset + 2)
            val end = offset + 12 + length
            require(length >= 0 && end <= bytes.size) { "Corrupt PNG chunk" }
            val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
            if (type !in PNG_METADATA) out.write(bytes, offset, end - offset)
            offset = end
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    private fun webp(bytes: ByteArray): ByteArray {
        val body = ByteArrayOutputStream(bytes.size)
        body.write(bytes, 8, 4)
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val type = String(bytes, offset, 4, Charsets.ISO_8859_1)
            val size =
                bytes.u8(offset + 4) or (bytes.u8(offset + 5) shl 8) or (bytes.u8(offset + 6) shl 16) or
                    (bytes.u8(offset + 7) shl 24)
            val end = offset + 8 + size + (size and 1)
            require(size >= 0 && offset + 8 + size <= bytes.size) { "Corrupt WebP chunk" }
            when (type) {
                "EXIF", "XMP " -> {
                    Unit
                }

                "VP8X" -> {
                    val chunk = bytes.copyOfRange(offset, minOf(end, bytes.size))
                    chunk[8] = (chunk[8].toInt() and (WEBP_EXIF_FLAG or WEBP_XMP_FLAG).inv()).toByte()
                    body.write(chunk)
                }

                else -> {
                    body.write(bytes, offset, minOf(end, bytes.size) - offset)
                }
            }
            offset = end
        }
        val payload = body.toByteArray()
        val size = payload.size
        return "RIFF".toByteArray() +
            byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) +
            payload
    }

    private companion object {
        const val SOS = 0xDA
        const val EOI = 0xD9
        const val APP1 = 0xE1
        const val APP13 = 0xED
        const val COM = 0xFE
        const val ORIENTATION_TAG = 0x0112
        const val PNG_SIGNATURE_LENGTH = 8
        const val WEBP_EXIF_FLAG = 0x08
        const val WEBP_XMP_FLAG = 0x04
        val EXIF_HEADER = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)
        val PNG_METADATA = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")

        fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

        fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

        fun ByteArray.startsWith(
            prefix: ByteArray,
            offset: Int,
        ): Boolean = size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }
    }
}
