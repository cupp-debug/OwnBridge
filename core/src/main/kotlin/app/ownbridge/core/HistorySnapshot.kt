package app.ownbridge.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class HistoryKind { CONNECTION, DEPLOYMENT }
enum class HistoryPhase { CONNECTION, DEPLOYMENT, DELIVERY }
enum class HistoryOutcome { RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED, UNKNOWN }
enum class HistoryDelivery { NONE, PUBLISHING, READY, FAILED }

data class HistoryLog(
    val action: String,
    val status: String,
    val command: String? = null,
    val source: String? = null,
    val result: String? = null,
)

data class HistoryDeliveryEvent(
    val at: Long,
    val state: HistoryDelivery,
    val failure: String? = null,
)

/** One immutable observation of an attempt; credentials are separate encrypted fields, never SSH passwords. */
data class HistorySnapshot(
    val id: String,
    val kind: HistoryKind,
    val host: String,
    val startedAt: Long,
    val updatedAt: Long,
    val phase: HistoryPhase,
    val outcome: HistoryOutcome,
    val step: String? = null,
    val reason: String? = null,
    val system: String? = null,
    val fingerprint: String? = null,
    val addresses: List<String> = emptyList(),
    val protocols: List<String> = emptyList(),
    val logs: List<HistoryLog> = emptyList(),
    val clashYaml: String? = null,
    val nodeUri: String? = null,
    val subscriptionUrl: String? = null,
    val delivery: HistoryDelivery = HistoryDelivery.NONE,
    val deliveryFailure: String? = null,
    val deliveryAttempts: Int = 0,
    val deliveryEvents: List<HistoryDeliveryEvent> = emptyList(),
    val archived: Boolean = false,
) {
    init {
        require(ID.matches(id) && host.isNotBlank() && host.length <= 253)
        require(startedAt > 0 && updatedAt >= startedAt && deliveryAttempts in 0..1_000)
        require(addresses.size <= 64 && protocols.size <= 16 && logs.size <= 256 &&
            deliveryEvents.size <= 1_000)
    }

    fun interruptedIfRunning(): HistorySnapshot = copy(
        outcome = if (outcome == HistoryOutcome.RUNNING) HistoryOutcome.INTERRUPTED else outcome,
        delivery = if (delivery == HistoryDelivery.PUBLISHING) {
            if (subscriptionUrl != null) HistoryDelivery.READY else HistoryDelivery.FAILED
        } else delivery,
        deliveryFailure = if (delivery == HistoryDelivery.PUBLISHING) "INTERRUPTED"
            else deliveryFailure,
        deliveryEvents = if (delivery == HistoryDelivery.PUBLISHING) {
            (deliveryEvents + HistoryDeliveryEvent(updatedAt, HistoryDelivery.FAILED, "INTERRUPTED"))
                .takeLast(1_000)
        } else deliveryEvents,
    )

    companion object {
        private val ID = Regex("[a-z0-9-]{1,64}")
    }
}

/** Versioned, bounded binary codec so snapshots can be encrypted without a database dependency. */
object HistorySnapshotCodec {
    private const val MAGIC = 0x4f424831 // OBH1
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_FIELD = 256 * 1024

    fun encode(value: HistorySnapshot): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.string(value.id)
            data.string(value.kind.name)
            data.string(value.host)
            data.writeLong(value.startedAt)
            data.writeLong(value.updatedAt)
            data.string(value.phase.name)
            data.string(value.outcome.name)
            data.optional(value.step)
            data.optional(value.reason)
            data.optional(value.system)
            data.optional(value.fingerprint)
            data.strings(value.addresses)
            data.strings(value.protocols)
            data.writeInt(value.logs.size)
            value.logs.forEach { log ->
                data.string(log.action)
                data.string(log.status)
                data.optional(log.command)
                data.optional(log.source)
                data.optional(log.result)
            }
            data.optional(value.clashYaml)
            data.optional(value.nodeUri)
            data.optional(value.subscriptionUrl)
            data.string(value.delivery.name)
            data.optional(value.deliveryFailure)
            data.writeInt(value.deliveryAttempts)
            data.writeInt(value.deliveryEvents.size)
            value.deliveryEvents.forEach { event ->
                data.writeLong(event.at)
                data.string(event.state.name)
                data.optional(event.failure)
            }
            data.writeBoolean(value.archived)
        }
        return output.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): HistorySnapshot = try {
        decodeChecked(bytes)
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid history snapshot", error)
    }

    private fun decodeChecked(bytes: ByteArray): HistorySnapshot {
        require(bytes.size in 1..MAX_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC)
        val id = input.string()
        val kind = enumValueOf<HistoryKind>(input.string())
        val host = input.string()
        val startedAt = input.readLong()
        val updatedAt = input.readLong()
        val phase = enumValueOf<HistoryPhase>(input.string())
        val outcome = enumValueOf<HistoryOutcome>(input.string())
        val step = input.optional()
        val reason = input.optional()
        val system = input.optional()
        val fingerprint = input.optional()
        val addresses = input.strings(64)
        val protocols = input.strings(16)
        val logs = List(input.count(256)) {
            HistoryLog(input.string(), input.string(), input.optional(), input.optional(), input.optional())
        }
        val clashYaml = input.optional()
        val nodeUri = input.optional()
        val subscriptionUrl = input.optional()
        val delivery = enumValueOf<HistoryDelivery>(input.string())
        val deliveryFailure = input.optional()
        val deliveryAttempts = input.readInt()
        val deliveryEvents = List(input.count(1_000)) {
            HistoryDeliveryEvent(input.readLong(), enumValueOf(input.string()), input.optional())
        }
        val archived = input.readBoolean()
        val result = HistorySnapshot(
            id, kind, host, startedAt, updatedAt, phase, outcome, step, reason, system,
            fingerprint, addresses, protocols, logs, clashYaml, nodeUri, subscriptionUrl,
            delivery, deliveryFailure, deliveryAttempts, deliveryEvents, archived,
        )
        require(input.available() == 0)
        return result
    }

    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.optional(value: String?) {
        writeBoolean(value != null)
        if (value != null) string(value)
    }

    private fun DataOutputStream.strings(values: List<String>) {
        writeInt(values.size)
        values.forEach { string(it) }
    }

    private fun DataInputStream.string(): String {
        val size = readInt()
        require(size in 0..MAX_FIELD && size <= available())
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.optional(): String? = if (readBoolean()) string() else null

    private fun DataInputStream.count(limit: Int): Int = readInt().also { require(it in 0..limit) }

    private fun DataInputStream.strings(limit: Int): List<String> = List(count(limit)) { string() }
}
