package app.ownbridge.core

import java.net.URI

/** A bearer URL: the random token is separate from the public host and run ID. */
object SubscriptionUrl {
    private val tokenPattern = Regex("[0-9a-f]{64}")

    fun build(host: String, port: Int, token: String): String {
        require(host.isNotBlank() && port in 28600..28619 && tokenPattern.matches(token))
        val targetHost = host.trim()
        require(Regex("[A-Za-z0-9.:-]+").matches(targetHost))
        val result = URI("http", null, targetHost, port, "/s/$token.yaml", null, null)
        require(result.host != null && result.userInfo == null)
        return result.toASCIIString()
    }
}
