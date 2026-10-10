package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.HistoryKind
import app.ownbridge.core.HistoryOutcome
import app.ownbridge.core.HistoryPhase
import app.ownbridge.core.HistorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryEncryptedStoreTest {
    @Test fun roundTripIsEncryptedAndTamperingIsReported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HistoryEncryptedStore(context)
        val id = UUID.randomUUID().toString().replace("-", "").let { "test-$it" }
        val secret = "private-subscription-token-test-only"
        val snapshot = HistorySnapshot(id, HistoryKind.DEPLOYMENT, "203.0.113.9",
            100, 200, HistoryPhase.DELIVERY, HistoryOutcome.SUCCEEDED,
            subscriptionUrl = "http://203.0.113.9/s/$secret.yaml")
        val file = File(context.filesDir, "history-v1/$id.obh")
        try {
            store.save(snapshot)
            val ciphertext = file.readBytes()
            assertFalse(ciphertext.toString(Charsets.UTF_8).contains(secret))
            assertEquals(snapshot, store.load().snapshots.first { it.id == id })

            ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
            file.writeBytes(ciphertext)
            val read = store.load()
            assertTrue(read.snapshots.none { it.id == id })
            assertTrue(read.unreadable >= 1)
        } finally {
            file.delete()
            File(file.path + ".new").delete()
            File(file.path + ".bak").delete()
        }
    }
}
