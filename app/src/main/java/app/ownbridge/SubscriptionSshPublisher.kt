package app.ownbridge

import android.content.res.AssetManager
import app.ownbridge.core.SubscriptionUrl
import app.ownbridge.core.VerifiedTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

internal enum class SubscriptionFailure {
    CREDENTIALS, IDENTITY, CONNECTION, REMOTE, RETRIEVAL,
}

internal class SubscriptionPublishException(val reason: SubscriptionFailure) : Exception()

/** Publishes the existing verified YAML over SSH, then fetches the public URL independently. */
internal class SubscriptionSshPublisher(private val assets: AssetManager) {
    private val active = AtomicReference<CompositeSshTransport?>()

    fun cancel() { active.getAndSet(null)?.cancel() }

    suspend fun publish(
        runId: String,
        sshHost: String,
        publicHost: String,
        username: String,
        password: String,
        trustedFingerprint: String?,
        yaml: String,
    ): String = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-f]{32}").matches(runId))
        if (password.isBlank()) throw SubscriptionPublishException(SubscriptionFailure.CREDENTIALS)
        if (trustedFingerprint.isNullOrBlank()) {
            throw SubscriptionPublishException(SubscriptionFailure.IDENTITY)
        }
        val profile = yaml.toByteArray(Charsets.UTF_8)
        require(profile.isNotEmpty() && profile.size <= 128 * 1024)
        val profileHash = VerifiedTransfer.sha256(profile)
        val directory = "/var/lib/ownbridge/runs/$runId/subscription-v1"
        val transport = CompositeSshTransport(sshHost, username, password, trustedFingerprint)
        active.set(transport)
        var phase = SubscriptionFailure.CONNECTION
        try {
            transport.connect()
            phase = SubscriptionFailure.REMOTE
            val files = listOf("server.py", "publish.py").map { name ->
                RemoteFile(name, assets.open("subscription/v1/$name").use { it.readBytes() })
            } + RemoteFile("profile.yaml", profile)
            transport.ensureFiles(directory, files)
            val response = transport.checked(
                "python3 -I $directory/publish.py $runId $profileHash", 45_000L,
            ).text.trim()
            val match = requireNotNull(Regex("(286[01][0-9]) ([0-9a-f]{64}) ([0-9a-f]{64})")
                .matchEntire(response))
            require(match.groupValues[3] == profileHash)
            val url = SubscriptionUrl.build(publicHost, match.groupValues[1].toInt(), match.groupValues[2])
            phase = SubscriptionFailure.RETRIEVAL
            verifyPublicProfile(url, profileHash)
            url
        } catch (error: CancellationException) {
            throw error
        } catch (error: SubscriptionPublishException) {
            throw error
        } catch (_: Exception) {
            throw SubscriptionPublishException(if (transport.hostKeyChanged) SubscriptionFailure.IDENTITY else phase)
        } finally {
            transport.close()
            active.compareAndSet(transport, null)
        }
    }

    private fun verifyPublicProfile(url: String, expectedSha256: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            require(connection.responseCode == 200)
            require(connection.contentType?.startsWith("application/x-yaml") == true)
            val body = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(body.size() + read <= 128 * 1024)
                    body.write(buffer, 0, read)
                }
            }
            require(VerifiedTransfer.sha256(body.toByteArray()) == expectedSha256)
        } finally {
            connection.disconnect()
        }
    }
}
