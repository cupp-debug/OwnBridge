package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeploymentJournalTest {
    @Test fun resumesAtFirstUnfinishedStep() {
        val journal = DeploymentJournalParser.parse(
            "preflight=done\nfetch=done\nconfigure=uncertain\nactivate=pending\nverify=pending\n",
        )
        assertEquals(DeploymentStep.CONFIGURE, journal.next())
    }

    @Test fun refusesIncompleteOrUnknownJournal() {
        assertThrows(IllegalArgumentException::class.java) {
            DeploymentJournalParser.parse("preflight=done\n")
        }
        assertThrows(IllegalStateException::class.java) {
            DeploymentJournalParser.parse(
                "preflight=done\nfetch=lost\nconfigure=pending\nactivate=pending\nverify=pending\n",
            )
        }
    }
}
