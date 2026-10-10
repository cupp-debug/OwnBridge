package app.ownbridge

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownbridge.core.NamedDeployedNode

@Composable
internal fun NodeProbePanel(
    nodes: List<NamedDeployedNode>,
    state: NodeProbeState,
    onTest: () -> Unit,
) {
    var showIpv6Help by remember { mutableStateOf(false) }
    var showNodeHelp by remember { mutableStateOf(false) }
    val testing = state.phase == NodeProbePhase.RUNNING
    val testAction = stringResource(when (state.phase) {
        NodeProbePhase.IDLE -> R.string.node_probe_start
        NodeProbePhase.RUNNING -> R.string.node_probe_running
        else -> R.string.node_probe_retry
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ContentCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val titleStyle = MaterialTheme.typography.containerTitle
                val iconSize = with(LocalDensity.current) { titleStyle.fontSize.toDp() * 1.2f }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(IconAsset.Mihomo.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.node_probe_title), style = titleStyle)
                        InlineHelpIcon(stringResource(R.string.help_open), titleStyle) {
                            showNodeHelp = true
                        }
                    }
                    IconButton(
                        onClick = onTest,
                        enabled = nodes.isNotEmpty() && !testing,
                        modifier = Modifier.semantics { contentDescription = testAction },
                    ) {
                        if (testing) CircularProgressIndicator(
                            modifier = Modifier.size(iconSize),
                            strokeWidth = 2.dp,
                        ) else ProjectIcon(
                            IconAsset.Rocket,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider()
                when (state.phase) {
                    NodeProbePhase.VPN_ACTIVE -> Text(
                        stringResource(R.string.node_probe_vpn_active),
                        color = MaterialTheme.colorScheme.error,
                    )
                    NodeProbePhase.ENGINE_FAILED -> Text(
                        stringResource(R.string.node_probe_engine_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                val items = if (state.items.isEmpty()) nodes.map(::NodeProbeItem) else state.items
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    val status = when (item.status) {
                        NodeProbeStatus.WAITING -> stringResource(R.string.node_probe_waiting)
                        NodeProbeStatus.TESTING -> stringResource(R.string.node_probe_testing)
                        NodeProbeStatus.REACHABLE -> stringResource(R.string.node_probe_reachable, item.delayMs ?: 0)
                        NodeProbeStatus.UNREACHABLE -> stringResource(R.string.node_probe_unreachable)
                        NodeProbeStatus.IPV6_UNAVAILABLE -> ""
                    }
                    val statusColor = when (item.status) {
                        NodeProbeStatus.REACHABLE -> MaterialTheme.colorScheme.primary
                        NodeProbeStatus.UNREACHABLE -> MaterialTheme.colorScheme.error
                        NodeProbeStatus.IPV6_UNAVAILABLE -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.node.name, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.node.deployed.node.protocol.wireName.uppercase(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.status == NodeProbeStatus.IPV6_UNAVAILABLE) {
                                InlineHelpIcon(stringResource(R.string.node_probe_ipv6_help_action),
                                    MaterialTheme.typography.bodyMedium) { showIpv6Help = true }
                            } else {
                                val icon = when (item.status) {
                                    NodeProbeStatus.REACHABLE -> IconAsset.SuccessFilled
                                    NodeProbeStatus.UNREACHABLE -> IconAsset.Error
                                    else -> null
                                }
                                if (icon != null) {
                                    ProjectIcon(icon, contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = statusColor)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(status, style = MaterialTheme.typography.bodyMedium,
                                    color = statusColor)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showIpv6Help) {
        AlertDialog(
            onDismissRequest = { showIpv6Help = false },
            text = {
                Text(
                    stringResource(R.string.node_probe_ipv6_help_body),
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { showIpv6Help = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showNodeHelp) {
        ResultHelpDialog(ResultHelpTopic.NODE_LIST) { showNodeHelp = false }
    }
}
