package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ownbridge.core.ShadowsocksExport

@Composable
internal fun ResultPage(
    state: DeploymentState,
    subscription: SubscriptionDeliveryState,
    onRequestSubscription: () -> Unit,
    savingRecord: Boolean,
    saveRecordError: Boolean,
    onSaveRecord: () -> Unit,
) {
    val ready = state as? DeploymentState.ServerReady
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (ready != null) {
            DeploymentCompletedBanner()
            val export = ShadowsocksExport(ready.plan, ready.secret)
            ResultContentCard(stringResource(R.string.result_node_link), export.nodeUri,
                helpTopic = ResultHelpTopic.SS_NODE)
            SubscriptionDeliveryPanel(subscription, onRequestSubscription)
            ResultExportSection(export.clashYaml)
            if (saveRecordError) {
                Text(stringResource(R.string.history_save_failed),
                    color = MaterialTheme.colorScheme.error)
                PrimaryAction(stringResource(R.string.history_retry_save), onSaveRecord,
                    enabled = !savingRecord,
                    icon = IconAsset.Save, loading = savingRecord)
            }
        }
    }
}
