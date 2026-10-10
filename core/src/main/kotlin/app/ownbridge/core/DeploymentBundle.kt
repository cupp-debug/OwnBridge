package app.ownbridge.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DeploymentArchive(val bytes: ByteArray, val sha256: String)

class DeploymentBundle(private val files: Map<String, ByteArray>) {
    fun archive(): DeploymentArchive {
        require(files.isNotEmpty() && files.size <= MAX_FILES)
        require(files.values.sumOf { it.size } <= MAX_SOURCE_BYTES)
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                files.toSortedMap().forEach { (path, content) ->
                    require(SAFE_PATH.matches(path)) { "Unsafe bundle path" }
                    val entry = ZipEntry(path).apply { time = 0L }
                    zip.putNextEntry(entry)
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return DeploymentArchive(bytes, digest)
    }

    private companion object {
        val SAFE_PATH = Regex("[a-z0-9][a-z0-9_-]*\\.(sh|py|service|txt)")
        const val MAX_FILES = 16
        const val MAX_SOURCE_BYTES = 128 * 1024
    }
}
