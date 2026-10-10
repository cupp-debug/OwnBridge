package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionUrlTest {
    private val token = "a".repeat(64)

    @Test fun ipv4AndDomain() {
        assertEquals("http://203.0.113.8:28600/s/$token.yaml",
            SubscriptionUrl.build("203.0.113.8", 28600, token))
        assertEquals("http://example.com:28619/s/$token.yaml",
            SubscriptionUrl.build("example.com", 28619, token))
    }

    @Test fun ipv6IsBracketed() {
        assertEquals("http://[2001:db8::1]:28601/s/$token.yaml",
            SubscriptionUrl.build("2001:db8::1", 28601, token))
    }

    @Test fun rejectsUnsafeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionUrl.build("example.com/path", 28600, token)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionUrl.build("example.com", 28600, "guessable")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionUrl.build("example.com", 80, token)
        }
    }
}
