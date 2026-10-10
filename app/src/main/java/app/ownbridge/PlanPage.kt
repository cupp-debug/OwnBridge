package app.ownbridge

import app.ownbridge.core.ServerInventory
import app.ownbridge.core.DeploymentPlanResult
import app.ownbridge.core.DeploymentPlanner
import app.ownbridge.core.PlanBlocker
import app.ownbridge.core.ShadowsocksPlan
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositeDeploymentPlanner
import app.ownbridge.core.CompositePlanBlocker
import app.ownbridge.core.CompositePlanResult
import app.ownbridge.core.DeploymentCapabilities
import app.ownbridge.core.ProxyProtocol

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal fun ServerInventory.targetAddresses(): List<String> =
    (ipv4Addresses + ipv6Addresses).distinct()

private data class PendingPlanConfirmation(
    val selection: PlanSelection,
    val execution: PlanExecution,
    val replacesExisting: Boolean,
)

@Composable
internal fun PlanPage(
    host: String,
    username: String,
    pendingPlan: ShadowsocksPlan?,
    pendingCompositePlan: CompositeDeploymentPlan?,
    compositePreview: Boolean,
    localRecordCorrupt: Boolean,
    deploymentRunning: Boolean,
    reconcilingSavedRun: Boolean,
    archivedPreviousRun: Boolean,
    inventory: ServerInventory?,
    selectedAddresses: Set<String>,
    onAddressToggle: (String) -> Unit,
    selectedProtocols: Set<String>,
    onProtocolToggle: (String) -> Unit,
    selectedClients: Set<String>,
    onClientToggle: (String) -> Unit,
    onNext: (PlanExecution, Boolean) -> Boolean,
) {
    val addresses = inventory?.targetAddresses().orEmpty()
    val selection = buildPlanSelection(
        host, inventory, selectedAddresses, selectedProtocols, selectedClients,
    )
    var helpDialog by remember { mutableStateOf<PlanHelpTopic?>(null) }
    val legacyResume = if (!compositePreview && pendingPlan != null) DeploymentPlanner.resume(
        host, username, inventory, selection.addresses, selection.protocols,
        selection.clients, pendingPlan,
    ) else null
    val legacy = if (compositePreview) null else if (legacyResume is DeploymentPlanResult.Ready) {
        legacyResume
    } else DeploymentPlanner.create(
        host, username, inventory, selection.addresses, selection.protocols, selection.clients,
    )
    // Debug deployment gate: IPv6 can be selected for server-side validation, while the
    // result page still distinguishes that from real client traffic verification.
    val capabilities = DeploymentCapabilities(ProxyProtocol.entries.toSet(), maxIpv6Addresses = 1)
    val compositeResume = if (compositePreview && pendingCompositePlan != null) {
        CompositeDeploymentPlanner.resume(
        host, username, inventory, selection.addresses, selection.compositeProtocols(),
        selection.clients, capabilities, pendingCompositePlan,
        )
    } else null
    val composite = if (!compositePreview) null
        else if (compositeResume is CompositePlanResult.Ready) compositeResume
        else CompositeDeploymentPlanner.create(
            host, username, inventory, selection.addresses, selection.compositeProtocols(),
            selection.clients, capabilities,
        )
    val replacesExisting = if (compositePreview) {
        pendingCompositePlan != null && compositeResume !is CompositePlanResult.Ready &&
            composite is CompositePlanResult.Ready
    } else pendingPlan != null && legacyResume !is DeploymentPlanResult.Ready &&
        legacy is DeploymentPlanResult.Ready
    val ready = when {
        composite is CompositePlanResult.Ready -> PlanExecution.Composite(composite.plan)
        legacy is DeploymentPlanResult.Ready -> PlanExecution.Legacy(legacy.plan)
        else -> null
    }
    var pendingConfirmation by remember { mutableStateOf<PendingPlanConfirmation?>(null) }
    var startFailed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (archivedPreviousRun) {
            Text(
                stringResource(R.string.plan_previous_run_archived),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ContentCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanSectionHeading(R.string.select_address, R.string.address_help_action) {
                    helpDialog = PlanHelpTopic.ADDRESS
                }
                if (addresses.isEmpty()) {
                    InlineIconText(
                        text = stringResource(if (inventory == null)
                            R.string.server_addresses_not_ready else R.string.no_target_addresses),
                        icon = IconAsset.Error,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.error),
                        iconTint = MaterialTheme.colorScheme.error,
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = maxWidth
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            addresses.forEach { address ->
                                val selected = address in selectedAddresses
                                PlanOptionChip(
                                    label = address,
                                    selected = selected,
                                    onClick = { onAddressToggle(address) },
                                    modifier = Modifier.widthIn(max = availableWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
        ContentCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanSectionHeading(R.string.select_protocol, R.string.protocol_help_action) {
                    helpDialog = PlanHelpTopic.PROTOCOL
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ProtocolOption.entries.forEach { option ->
                        val choice = option.id
                        PlanOptionChip(
                            label = choice,
                            selected = choice in selectedProtocols,
                            onClick = { onProtocolToggle(choice) },
                        )
                    }
                }
            }
        }
        ContentCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanSectionHeading(R.string.select_client, R.string.client_help_action) {
                    helpDialog = PlanHelpTopic.CLIENT
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ClientOption.entries.forEach { option ->
                        val selected = option.id in selectedClients
                        PlanOptionChip(
                            label = option.id,
                            selected = selected,
                            onClick = { onClientToggle(option.id) },
                        )
                    }
                }
            }
        }
        PrimaryAction(
            stringResource(R.string.next),
            onClick = {
                startFailed = false
                ready?.let { pendingConfirmation = PendingPlanConfirmation(selection, it, replacesExisting) }
            },
            enabled = selection.isComplete && ready != null && !deploymentRunning &&
                !reconcilingSavedRun && !localRecordCorrupt,
            icon = IconAsset.ArrowForward,
        )
        if (startFailed) {
            Text(
                stringResource(R.string.plan_start_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!selection.isComplete) {
            val requirementRes = when {
                inventory == null || selection.server.isBlank() -> R.string.plan_requires_detection
                selection.addresses.isEmpty() -> R.string.plan_requires_address
                selection.protocols.isEmpty() -> R.string.plan_requires_protocol
                else -> R.string.plan_requires_client
            }
            Text(
                stringResource(requirementRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (deploymentRunning) {
            Text(stringResource(R.string.plan_run_in_progress), style = MaterialTheme.typography.bodySmall)
        } else if (reconcilingSavedRun) {
            Text(stringResource(R.string.plan_archiving_missing_run), style = MaterialTheme.typography.bodySmall)
        } else if (localRecordCorrupt) {
            Text(stringResource(R.string.plan_local_record_corrupt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        } else if (legacy is DeploymentPlanResult.Blocked) {
            val messageRes = when (legacy.reason) {
                PlanBlocker.NO_INVENTORY -> R.string.plan_requires_detection
                PlanBlocker.ROOT_REQUIRED -> R.string.plan_root_required
                PlanBlocker.UNSUPPORTED_SYSTEM -> R.string.plan_unsupported_system
                PlanBlocker.UNSUPPORTED_SELECTION -> R.string.plan_first_milestone_only
                PlanBlocker.INVALID_ADDRESS -> R.string.plan_requires_address
                PlanBlocker.NO_AVAILABLE_PORT -> R.string.plan_no_port
                PlanBlocker.EXISTING_OWNBRIDGE_SERVICE -> R.string.plan_existing_service
            }
            Text(
                stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (composite is CompositePlanResult.Blocked) {
            val messageRes = when (composite.reason) {
                CompositePlanBlocker.NO_INVENTORY -> R.string.plan_requires_detection
                CompositePlanBlocker.ROOT_REQUIRED -> R.string.plan_root_required
                CompositePlanBlocker.UNSUPPORTED_SYSTEM -> R.string.plan_composite_unsupported_system
                CompositePlanBlocker.EMPTY_SELECTION -> R.string.plan_requires_protocol
                CompositePlanBlocker.UNSUPPORTED_CLIENT -> R.string.plan_composite_client_only
                CompositePlanBlocker.UNSUPPORTED_PROTOCOL -> R.string.plan_composite_protocol_unsupported
                CompositePlanBlocker.IPV6_NOT_ENABLED -> R.string.plan_ipv6_not_ready
                CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED -> R.string.plan_address_limit_not_ready
                CompositePlanBlocker.INVALID_ADDRESS -> R.string.plan_requires_address
                CompositePlanBlocker.NO_AVAILABLE_PORT -> R.string.plan_no_port
                CompositePlanBlocker.PLAN_MISMATCH -> R.string.plan_requires_detection
            }
            Text(stringResource(messageRes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    helpDialog?.let { topic ->
        PlanHelpDialog(topic, onDismiss = { helpDialog = null })
    }
    pendingConfirmation?.let { (snapshot, execution, replacing) ->
        PlanConfirmationDialog(
            selection = snapshot,
            execution = execution,
            resuming = !replacing && (if (compositePreview) pendingCompositePlan != null
                else pendingPlan != null),
            replacingExisting = replacing,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                pendingConfirmation = null
                startFailed = !onNext(execution, replacing)
            },
        )
    }
}

@Composable
private fun PlanSectionHeading(
    @StringRes titleRes: Int,
    @StringRes helpDescriptionRes: Int,
    onHelp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(titleRes))
        InlineHelpIcon(stringResource(helpDescriptionRes),
            MaterialTheme.typography.containerTitle, onHelp)
    }
}
