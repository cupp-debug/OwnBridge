package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRunProbeTest {
    private val runId = "0123456789abcdef0123456789abcdef"

    @Test fun commandInspectsOnlyTheValidatedTaskDirectory() {
        val command = SavedRunProbe.command(runId)
        assertTrue(command.contains("test -d /var/lib/ownbridge/runs/$runId"))
        assertFalse(command.contains("install "))
        assertThrows(IllegalArgumentException::class.java) {
            SavedRunProbe.command("$runId; touch /tmp/unsafe")
        }
    }

    @Test fun outputMustBeExactlyPresentOrMissing() {
        assertEquals(SavedRunPresence.PRESENT, SavedRunProbe.parse("present\n"))
        assertEquals(SavedRunPresence.MISSING, SavedRunProbe.parse("missing\n"))
        assertNull(SavedRunProbe.parse("permission denied"))
        assertNull(SavedRunProbe.parse("present\nmissing\n"))
    }
}
