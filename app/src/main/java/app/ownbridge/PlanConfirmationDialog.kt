package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PlanConfirmationDialog(
    selection: PlanSelection,
    execution: PlanExecution,
    resuming: Boolean,
    replacingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(
            when {
                replacingExisting -> R.string.plan_new_task_title
                resuming -> R.string.plan_resume_title
                else -> R.string.plan_confirmation_title
            },
        ), style = MaterialTheme.typography.dialogTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        R.string.plan_confirmation_summary,
                        selection.addresses.size,
                        selection.protocols.size,
                        selection.clients.size,
                        selection.possibleNodeCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                PlanConfirmationDetail(R.string.plan_server, listOf(selection.server))
                PlanConfirmationDetail(R.string.select_address, selection.addresses)
                PlanConfirmationDetail(R.string.select_protocol, selection.protocols)
                PlanConfirmationDetail(R.string.select_client, selection.clients)
                PlanConfirmationDetail(R.string.plan_port, when (execution) {
                    is PlanExecution.Legacy -> listOf(execution.plan.port.toString())
                    is PlanExecution.Composite -> execution.plan.nodes.map { node ->
                        "${node.address.value} · ${node.protocol.wireName.uppercase()} · ${node.port}"
                    }
                })
                Text(
                    stringResource(R.string.plan_node_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (replacingExisting) {
                    Text(
                        stringResource(R.string.plan_new_task_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(when {
                        resuming && execution is PlanExecution.Composite -> R.string.plan_composite_resume_warning
                        resuming -> R.string.plan_resume_warning
                        execution is PlanExecution.Composite -> R.string.plan_composite_write_warning
                        else -> R.string.plan_write_warning
                    }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (replacingExisting) R.string.history_start_new_task else R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PlanConfirmationDetail(@StringRes labelRes: Int, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        values.forEach { value ->
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
