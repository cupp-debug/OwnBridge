package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavedServerIdentitiesTest {
    @Test fun rememberedFingerprintsCanBeListedAndForgottenIndividually() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HostKeyStore(context)
        val host = "ob-test-${UUID.randomUUID()}.invalid"
        try {
            assertTrue(store.pin(host, "SHA256:test-fingerprint"))
            assertEquals("SHA256:test-fingerprint", store.savedIdentities()[host])
            assertTrue(store.forget(host))
            assertFalse(store.savedIdentities().containsKey(host))
        } finally {
            store.forget(host)
        }
    }
}
