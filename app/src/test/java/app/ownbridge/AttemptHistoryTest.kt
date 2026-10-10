package app.ownbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptHistoryTest {
    @Test fun recentCompositeLogIsRetainedAfterNewAttemptMarker() {
        val previous = (1..70).map { index -> CompositeRunEvent(
            CompositeRunAction.STEP, null, null, null, CompositeRunEventStatus.PASSED,
            detail = index.toString(),
        ) }
        val history = AttemptHistory.composite(previous)
        assertEquals(CompositeRunAction.PRIOR_ATTEMPT, history.first().action)
        assertEquals(61, history.size)
        assertEquals("11", history[1].detail)
        assertEquals("70", history.last().detail)
        assertTrue(AttemptHistory.composite(emptyList()).isEmpty())
    }

    @Test fun legacyLogAlsoRemainsAvailableOnRetry() {
        val previous = listOf(DeploymentEvent(DeploymentAction.STEP, null, null, null,
            "Exit 1", null, DeploymentEventStatus.FAILED))
        val history = AttemptHistory.legacy(previous)
        assertEquals(DeploymentAction.PRIOR_ATTEMPT, history.first().action)
        assertEquals("Exit 1", history.last().result)
    }
}
