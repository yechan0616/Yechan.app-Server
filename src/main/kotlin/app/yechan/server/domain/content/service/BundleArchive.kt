package app.yechan.server.domain.content.service

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BundleArchive {
    private const val MAX_FILES = 5_000
    private const val MAX_TOTAL_BYTES = 1L shl 30

    fun extract(
        input: InputStream,
        target: Path,
    ) {
        val root = target.toRealPath()
        var files = 0
        var total = 0L
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val file = root.resolve(entry.name).normalize()
                require(file.startsWith(root) && file != root) { "Archive entry escapes the bundle: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(file)
                    return@forEach
                }
                require(++files <= MAX_FILES) { "Archive has too many files" }
                Files.createDirectories(file.parent)
                Files.newOutputStream(file).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_TOTAL_BYTES) { "Archive is too large" }
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    fun write(
        output: OutputStream,
        files: Sequence<Pair<String, ByteArray>>,
    ) {
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
