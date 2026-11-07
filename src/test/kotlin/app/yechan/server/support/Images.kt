package app.yechan.server.support

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object Images {
    fun encode(
        format: String,
        width: Int,
        height: Int,
        seed: Int = 0,
    ): ByteArray {
        val type = if (format == "jpg") BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val image = BufferedImage(width, height, type)
        image.setRGB(0, 0, seed)
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    fun webpLossless(
        width: Int,
        height: Int,
    ): ByteArray {
        val w = width - 1
        val h = height - 1
        val bits =
            byteArrayOf(
                0x2F,
                (w and 0xFF).toByte(),
                (((w shr 8) and 0x3F) or ((h and 0x03) shl 6)).toByte(),
                ((h shr 2) and 0xFF).toByte(),
                ((h shr 10) and 0x0F).toByte(),
            )
        return riff("VP8L", bits)
    }

    fun webpExtended(
        width: Int,
        height: Int,
    ): ByteArray {
        val w = width - 1
        val h = height - 1
        val data =
            byteArrayOf(0, 0, 0, 0) +
                byteArrayOf((w and 0xFF).toByte(), ((w shr 8) and 0xFF).toByte(), ((w shr 16) and 0xFF).toByte()) +
                byteArrayOf((h and 0xFF).toByte(), ((h shr 8) and 0xFF).toByte(), ((h shr 16) and 0xFF).toByte())
        return riff("VP8X", data)
    }

    fun webpLossy(
        width: Int,
        height: Int,
    ): ByteArray {
        val data =
            byteArrayOf(0, 0, 0, 0x9D.toByte(), 0x01, 0x2A) +
                byteArrayOf((width and 0xFF).toByte(), ((width shr 8) and 0x3F).toByte()) +
                byteArrayOf((height and 0xFF).toByte(), ((height shr 8) and 0x3F).toByte())
        return riff("VP8 ", data)
    }

    private fun riff(
        chunk: String,
        data: ByteArray,
    ): ByteArray {
        val body = "WEBP".toByteArray() + chunk.toByteArray() + littleEndian(data.size) + data
        return "RIFF".toByteArray() + littleEndian(body.size) + body
    }

    private fun littleEndian(value: Int) =
        byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (
                value shr
                    24
            ).toByte(),
        )
}
