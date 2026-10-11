package app.ownbridge

import app.ownbridge.core.DeploymentBundle
import app.ownbridge.core.VerifiedTransfer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class CompositePackageCatalogTest {
    @Test fun shippedAssetsFormTheExactVerifiedArchive() {
        val directory = sequenceOf(
            File("src/main/assets/deployment/v2"),
            File("app/src/main/assets/deployment/v2"),
        ).firstOrNull(File::isDirectory)
        assertNotNull("v2 assets are missing", directory)
        val files = directory!!
        val names = CompositeDeploymentPackageCatalog.FILE_NAMES
        assertEquals(names.toSet(), files.listFiles()!!.filter(File::isFile).map(File::getName).toSet())
        val source = names.associateWith { name -> File(files, name).readBytes() }
        source.forEach { (name, bytes) ->
            assertFalse("Remote asset contains CRLF: $name", 13.toByte() in bytes)
        }
        val archive = DeploymentBundle(source).archive()
        assertEquals(VerifiedTransfer.sha256(archive.bytes), archive.sha256)

        val extracted = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive.bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                extracted[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals(source.keys, extracted.keys)
        source.forEach { (name, bytes) -> assertArrayEquals(bytes, extracted.getValue(name)) }
    }
}
