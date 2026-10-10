package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ownbridge.core.CompositeClashExport
import app.ownbridge.core.CompositeV2RayExport
import app.ownbridge.core.CompositeProgress

@Composable
internal fun CompositeResultPage(
    state: CompositeDeploymentState,
    onDiscardUnstarted: () -> Unit,
    savingRecord: Boolean,
    saveRecordError: Boolean,
    onSaveRecord: () -> Unit,
    probeState: NodeProbeState,
    onTestNodes: (CompositeClashExport) -> Unit,
    subscription: SubscriptionDeliveryState,
    onRequestSubscription: () -> Unit,
) {
    val settled = state as? CompositeDeploymentState.Settled
    val export = settled?.result?.nodes?.takeIf { it.isNotEmpty() }?.let(::CompositeClashExport)
    val completed = settled?.completedExport() != null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (completed) DeploymentCompletedBanner()
        if (export != null) {
            NodeProbePanel(export.namedNodes, probeState, onTest = { onTestNodes(export) })
            if (completed) SubscriptionDeliveryPanel(subscription, onRequestSubscription)
            ResultExportSection(export.clashYaml)
            ResultContentCard(
                stringResource(R.string.result_v2ray_links),
                CompositeV2RayExport(settled.result.nodes).shareLinks,
                downloadFileName = "OwnBridge-v2ray-links.txt",
                helpTopic = ResultHelpTopic.V2RAY_LINKS,
            )
        } else if (settled != null) {
            Text(stringResource(R.string.composite_no_ready_nodes))
        }
        if (settled?.result?.journal?.noProtocolWorkStarted(settled.record.plan) == true) {
            Text(stringResource(R.string.composite_discard_keeps_server_log))
            PrimaryAction(stringResource(R.string.composite_discard_unstarted), onDiscardUnstarted)
        }
        if (completed && saveRecordError) {
            Text(stringResource(R.string.history_save_failed),
                color = MaterialTheme.colorScheme.error)
            PrimaryAction(
                stringResource(R.string.history_retry_save),
                onSaveRecord,
                enabled = !savingRecord,
                icon = IconAsset.Save,
                loading = savingRecord,
            )
        }
    }
}

internal fun CompositeDeploymentState.Settled.completedExport(): CompositeClashExport? {
    if (result.journal?.overall(record.plan) != CompositeProgress.ALL_SERVER_READY ||
        result.nodes.size != record.plan.nodes.size ||
        result.nodes.map { it.node }.toSet() != record.plan.nodes.toSet()) return null
    return CompositeClashExport(result.nodes)
}

@Composable
internal fun ResultExportSection(yaml: String) {
    ResultContentCard(stringResource(R.string.result_clash_yaml), yaml,
        downloadFileName = "OwnBridge-Clash.yaml", downloadMimeType = "application/x-yaml",
        helpTopic = ResultHelpTopic.CLASH_YAML)
}
