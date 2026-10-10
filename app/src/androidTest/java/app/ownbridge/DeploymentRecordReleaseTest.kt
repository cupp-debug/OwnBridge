package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.ShadowsocksPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeploymentRecordReleaseTest {
    @Test fun onlyTheMatchingActivePointerCanBeReleased() {
        // Instrumentation context belongs to the test APK, not the user's main app.
        val store = DeploymentRecordStore(InstrumentationRegistry.getInstrumentation().context)
        val record = DeploymentRecord(UUID.randomUUID().toString().replace("-", ""),
            ShadowsocksPlan("203.0.113.9", "203.0.113.9", 28443, "Ubuntu 24.04"),
            DeploymentPackageCatalog.CURRENT_VERSION)
        assertTrue(store.save(record))
        assertFalse(store.release(record.copy(runId = "0".repeat(32))))
        assertEquals(record, store.load())
        assertTrue(store.release(record))
        assertNull(store.load())
    }
}
