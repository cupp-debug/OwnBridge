package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class DeploymentBundleTest {
    @Test fun archiveIsDeterministicAndSorted() {
        val first = DeploymentBundle(mapOf("b.sh" to byteArrayOf(2), "a.sh" to byteArrayOf(1))).archive()
        val second = DeploymentBundle(mapOf("a.sh" to byteArrayOf(1), "b.sh" to byteArrayOf(2))).archive()
        assertEquals(first.sha256, second.sha256)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(first.bytes)).use { zip ->
            while (true) names += zip.nextEntry?.name ?: break
        }
        assertEquals(listOf("a.sh", "b.sh"), names)
    }

    @Test fun rejectsTraversalAndOversizedSource() {
        assertThrows(IllegalArgumentException::class.java) {
            DeploymentBundle(mapOf("../escape.sh" to byteArrayOf(1))).archive()
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeploymentBundle(mapOf("large.sh" to ByteArray(128 * 1024 + 1))).archive()
        }
    }
}
