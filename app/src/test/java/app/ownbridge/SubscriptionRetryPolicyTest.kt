package app.ownbridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRetryPolicyTest {
    @Test fun onlyPostSshFailuresAreRetriedWithinThreeAttempts() {
        assertEquals(3, SubscriptionRetryPolicy.MAX_ATTEMPTS)
        assertTrue(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.REMOTE, 1))
        assertTrue(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.RETRIEVAL, 2))
        assertFalse(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.REMOTE, 3))
        assertFalse(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.CONNECTION, 1))
        assertFalse(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.CREDENTIALS, 1))
        assertFalse(SubscriptionRetryPolicy.shouldRetry(SubscriptionFailure.IDENTITY, 1))
    }

    @Test fun retryWaitIsShortAndBounded() {
        assertEquals(1_000L, SubscriptionRetryPolicy.delayAfter(1))
        assertEquals(2_000L, SubscriptionRetryPolicy.delayAfter(2))
    }

    @Test fun coldStartFailureRetriesAndThenReturnsOneLink() = runBlocking {
        var calls = 0
        val attempts = mutableListOf<Int>()
        val transient = mutableListOf<SubscriptionFailure>()
        val waits = mutableListOf<Long>()
        val link = SubscriptionRetryPolicy.execute(
            publish = {
                calls++
                if (calls == 1) throw SubscriptionPublishException(SubscriptionFailure.REMOTE)
                "http://203.0.113.7/link"
            },
            onAttempt = { attempts += it },
            onRetryableFailure = { _, reason -> transient += reason },
            wait = { waits += it },
        )
        assertEquals("http://203.0.113.7/link", link)
        assertEquals(listOf(1, 2), attempts)
        assertEquals(listOf(SubscriptionFailure.REMOTE), transient)
        assertEquals(listOf(1_000L), waits)
    }

    @Test fun repeatedRemoteFailureStopsAfterThirdAttempt() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        var terminal: SubscriptionFailure? = null
        try {
            SubscriptionRetryPolicy.execute(
                publish = {
                    calls++
                    throw SubscriptionPublishException(SubscriptionFailure.REMOTE)
                },
                onAttempt = {},
                onRetryableFailure = { _, _ -> },
                wait = { waits += it },
            )
        } catch (error: SubscriptionPublishException) {
            terminal = error.reason
        }
        assertEquals(3, calls)
        assertEquals(listOf(1_000L, 2_000L), waits)
        assertEquals(SubscriptionFailure.REMOTE, terminal)
    }

    @Test fun connectionAndCancellationDoNotRetry() = runBlocking {
        for (error in listOf(
            SubscriptionPublishException(SubscriptionFailure.CONNECTION),
            CancellationException(),
        )) {
            var calls = 0
            var waits = 0
            var terminal: Exception? = null
            try {
                SubscriptionRetryPolicy.execute(
                    publish = { calls++; throw error },
                    onAttempt = {},
                    onRetryableFailure = { _, _ -> },
                    wait = { waits++ },
                )
            } catch (observed: Exception) {
                terminal = observed
            }
            if (error is SubscriptionPublishException) {
                assertEquals(error.reason, (terminal as? SubscriptionPublishException)?.reason)
            } else {
                assertTrue(terminal is CancellationException)
            }
            assertEquals(1, calls)
            assertEquals(0, waits)
        }
    }
}
