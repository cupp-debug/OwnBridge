package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.ownbridge.core.HistoryDelivery
import app.ownbridge.core.HistoryDeliveryEvent
import app.ownbridge.core.HistoryLog
import app.ownbridge.core.HistoryOutcome
import app.ownbridge.core.HistoryPhase
import app.ownbridge.core.HistorySnapshot
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HistoryDetailPage(snapshot: HistorySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(snapshot.host, style = MaterialTheme.typography.titleLarge)
        HistoryStatus(snapshot.outcome, MaterialTheme.typography.titleMedium)
        Text(historyTime(snapshot.updatedAt), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.history_stage, stringResource(snapshot.phase.labelRes)))
        snapshot.step?.let { Text(stringResource(R.string.history_step, it)) }
        snapshot.reason?.let { Text(stringResource(R.string.history_reason, historyReason(it)),
            color = if (snapshot.outcome == HistoryOutcome.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant) }
        snapshot.system?.let { Text(stringResource(R.string.history_system, it)) }
        snapshot.fingerprint?.let { Text(stringResource(R.string.history_fingerprint, it)) }
        if (snapshot.archived) Text(stringResource(R.string.history_archived),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (snapshot.addresses.isNotEmpty()) ResultContentCard(
            stringResource(R.string.history_addresses), snapshot.addresses.joinToString("\n"),
            helpTopic = ResultHelpTopic.ADDRESSES)
        if (snapshot.protocols.isNotEmpty()) ResultContentCard(
            stringResource(R.string.history_protocols),
            snapshot.protocols.joinToString("\n", transform = ::historyProtocolName),
            helpTopic = ResultHelpTopic.PROTOCOLS)
        when (snapshot.delivery) {
            HistoryDelivery.NONE -> Unit
            HistoryDelivery.PUBLISHING -> Text(stringResource(R.string.history_delivery_publishing,
                snapshot.deliveryAttempts))
            HistoryDelivery.READY -> {
                Text(stringResource(R.string.history_delivery_ready, snapshot.deliveryAttempts))
                if (snapshot.deliveryFailure != null) Text(
                    stringResource(R.string.history_delivery_last_failed),
                    color = MaterialTheme.colorScheme.error)
            }
            HistoryDelivery.FAILED -> {
                Text(stringResource(R.string.history_delivery_failed, snapshot.deliveryAttempts),
                    color = MaterialTheme.colorScheme.error)
                snapshot.deliveryFailure?.let { Text(historyDeliveryReason(it),
                    color = MaterialTheme.colorScheme.error) }
            }
        }
        if (snapshot.deliveryEvents.isNotEmpty()) ResultContentCard(
            stringResource(R.string.history_delivery_events),
            historyDeliveryText(snapshot.deliveryEvents),
            helpTopic = ResultHelpTopic.DELIVERY_EVENTS)
        snapshot.subscriptionUrl?.let { ResultContentCard(
            stringResource(R.string.history_subscription_link), it,
            helpTopic = ResultHelpTopic.CLASH_SUBSCRIPTION) }
        snapshot.nodeUri?.let { ResultContentCard(stringResource(R.string.history_node_link), it,
            helpTopic = if (snapshot.protocols == listOf("SHADOWSOCKS") &&
                it.lines().size == 1) ResultHelpTopic.SS_NODE else ResultHelpTopic.V2RAY_LINKS) }
        snapshot.clashYaml?.let { ResultContentCard(
            stringResource(R.string.history_clash_yaml), it,
            downloadFileName = "OwnBridge-Clash.yaml", downloadMimeType = "application/x-yaml",
            helpTopic = ResultHelpTopic.CLASH_YAML) }
        if (snapshot.logs.isNotEmpty()) ResultContentCard(stringResource(R.string.history_log),
            historyLogText(snapshot.logs), helpTopic = ResultHelpTopic.LOGS)
    }
}

internal fun historyProtocolName(value: String): String = when (value) {
    "SHADOWSOCKS" -> "SS"
    "HYSTERIA2" -> "HY2"
    else -> value
}

internal val HistoryOutcome.labelRes: Int get() = when (this) {
    HistoryOutcome.RUNNING -> R.string.history_running
    HistoryOutcome.SUCCEEDED -> R.string.history_succeeded
    HistoryOutcome.FAILED -> R.string.history_failed
    HistoryOutcome.CANCELLED -> R.string.history_cancelled
    HistoryOutcome.INTERRUPTED -> R.string.history_interrupted
    HistoryOutcome.UNKNOWN -> R.string.history_unknown
}

private val HistoryOutcome.compactLabelRes: Int get() = when (this) {
    HistoryOutcome.INTERRUPTED -> R.string.history_interrupted_short
    HistoryOutcome.UNKNOWN -> R.string.history_unknown_short
    else -> labelRes
}

private val HistoryOutcome.iconAsset: IconAsset get() = when (this) {
    HistoryOutcome.SUCCEEDED -> IconAsset.SuccessFilled
    HistoryOutcome.FAILED -> IconAsset.Error
    HistoryOutcome.RUNNING -> IconAsset.Retry
    HistoryOutcome.CANCELLED -> IconAsset.PingCancel
    HistoryOutcome.INTERRUPTED -> IconAsset.Warning
    HistoryOutcome.UNKNOWN -> IconAsset.Help
}

@Composable
internal fun HistoryStatus(outcome: HistoryOutcome, style: TextStyle, compact: Boolean = false) {
    val color = outcome.statusColor()
    InlineIconText(
        text = stringResource(if (compact) outcome.compactLabelRes else outcome.labelRes),
        icon = outcome.iconAsset,
        style = style.copy(color = color),
        iconTint = color,
    )
}

internal val HistoryPhase.labelRes: Int get() = when (this) {
    HistoryPhase.CONNECTION -> R.string.history_phase_connection
    HistoryPhase.DEPLOYMENT -> R.string.history_phase_deployment
    HistoryPhase.DELIVERY -> R.string.history_phase_delivery
}

@Composable
internal fun HistoryOutcome.statusColor() = when (this) {
    HistoryOutcome.SUCCEEDED -> MaterialTheme.colorScheme.primary
    HistoryOutcome.FAILED -> MaterialTheme.colorScheme.error
    HistoryOutcome.INTERRUPTED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun historyTime(epochMillis: Long): String {
    if (epochMillis <= 1) return stringResource(R.string.history_time_unknown)
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(epochMillis))
}

@Composable
private fun historyReason(value: String): String {
    if (value == "legacy-summary") return stringResource(R.string.history_earlier_summary)
    val parts = value.split(':', limit = 2)
    if (parts.size != 2) return value
    val resource = runCatching { when (parts[0]) {
        "connection" -> enumValueOf<DetectionFailure>(parts[1]).messageRes
        "composite" -> enumValueOf<CompositeRunFailure>(parts[1]).messageRes
        "legacy" -> enumValueOf<DeploymentFailure>(parts[1]).messageRes
        else -> null
    } }.getOrNull()
    return resource?.let { stringResource(it) } ?: value
}

@Composable
private fun historyDeliveryReason(value: String): String {
    if (value == "INTERRUPTED") return stringResource(R.string.history_delivery_interrupted)
    val resource = runCatching { when (enumValueOf<SubscriptionFailure>(value)) {
        SubscriptionFailure.CREDENTIALS -> R.string.subscription_need_password
        SubscriptionFailure.IDENTITY -> R.string.subscription_identity_failed
        SubscriptionFailure.CONNECTION -> R.string.subscription_connection_failed
        SubscriptionFailure.REMOTE -> R.string.subscription_server_failed
        SubscriptionFailure.RETRIEVAL -> R.string.subscription_retrieval_failed
    } }.getOrNull()
    return resource?.let { stringResource(it) } ?: value
}

@Composable
private fun historyDeliveryText(events: List<HistoryDeliveryEvent>): String {
    val started = stringResource(R.string.history_delivery_started)
    val ready = stringResource(R.string.history_delivery_event_ready)
    val failed = stringResource(R.string.history_delivery_event_failed)
    return events.mapIndexed { index, event ->
        val status = when (event.state) {
            HistoryDelivery.PUBLISHING -> started
            HistoryDelivery.READY -> ready
            HistoryDelivery.FAILED -> failed
            HistoryDelivery.NONE -> started
        }
        val reason = event.failure?.let { " · ${historyDeliveryReason(it)}" }.orEmpty()
        "${index + 1}. ${historyTime(event.at)} · $status$reason"
    }.joinToString("\n")
}

@Composable
private fun historyLogText(logs: List<HistoryLog>): String {
    val sent = stringResource(R.string.history_log_command)
    val source = stringResource(R.string.history_log_source)
    val result = stringResource(R.string.history_log_result)
    val omitted = stringResource(R.string.history_log_omitted)
    val statusLabels = mapOf(
        "PASSED" to stringResource(R.string.history_log_passed),
        "FAILED" to stringResource(R.string.history_log_failed),
        "RUNNING" to stringResource(R.string.history_log_running),
        "CANCELLED" to stringResource(R.string.history_log_cancelled),
    )
    return buildString {
        logs.forEachIndexed { index, item ->
            if (index > 0) { appendLine(); appendLine() }
            if (item.status == "TRUNCATED") {
                append(omitted)
            } else {
                append("${index + 1}. ${item.action} · ${statusLabels[item.status] ?: item.status}")
                item.command?.let { append("\n$sent:\n$it") }
                item.source?.let { append("\n$source:\n$it") }
                item.result?.let { append("\n$result:\n$it") }
            }
        }
    }
}
