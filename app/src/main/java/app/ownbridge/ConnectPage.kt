package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectPage(
    host: String,
    onHostChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    detectionState: DetectionUiState,
    vpnActive: Boolean,
    pingState: PingUiState,
    onHostBlur: () -> Unit,
    onPing: () -> Unit,
    onCancelPing: () -> Unit,
    onDetect: () -> Unit,
    onTrustChangedHost: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
) {
    var hostWasFocused by remember { mutableStateOf(false) }
    val hostKeyChanged = (detectionState as? DetectionUiState.Failed)
        ?.takeIf { it.reason == DetectionFailure.HOST_KEY_CHANGED }
        ?.hostKeyChange != null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ContentCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                        if (focus.isFocused) hostWasFocused = true
                        else if (hostWasFocused) {
                            hostWasFocused = false
                            onHostBlur()
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.vps_address)) },
                    placeholder = { Text(stringResource(R.string.vps_address_example)) },
                    leadingIcon = { ProjectIcon(IconAsset.Address, contentDescription = null) },
                    trailingIcon = {
                        when {
                            host.isBlank() || pingState.availability == PingAvailability.HIDDEN -> Unit
                            pingState.availability == PingAvailability.CHECKING ->
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            pingState.runStatus == PingRunStatus.RUNNING -> IconButton(onClick = onCancelPing) {
                                ProjectIcon(
                                    IconAsset.PingCancel,
                                    contentDescription = stringResource(R.string.ping_cancel),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            pingState.availability == PingAvailability.SUPPORTED -> IconButton(
                                onClick = onPing,
                                enabled = detectionState !is DetectionUiState.Running,
                            ) {
                                ProjectIcon(
                                    IconAsset.Ping,
                                    contentDescription = stringResource(R.string.ping_start),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            else -> ProjectIcon(
                                IconAsset.Ping,
                                contentDescription = stringResource(R.string.ping_unavailable),
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.ssh_username)) },
                    leadingIcon = { ProjectIcon(IconAsset.SshUser, contentDescription = null) },
                )
                PasswordInput(password, onPasswordChange)
            }
        }
        PrimaryAction(
            text = when (detectionState) {
                is DetectionUiState.Ready -> stringResource(R.string.next)
                is DetectionUiState.Running -> stringResource(R.string.cancel_detection)
                is DetectionUiState.Failed -> if (hostKeyChanged) {
                    stringResource(R.string.trust_changed_host)
                } else stringResource(R.string.retry_detection)
                is DetectionUiState.Cancelled -> stringResource(R.string.retry_detection)
                else -> stringResource(R.string.detect_connectivity)
            },
            onClick = when (detectionState) {
                is DetectionUiState.Ready -> onNext
                is DetectionUiState.Running -> onCancel
                is DetectionUiState.Failed -> if (hostKeyChanged) onTrustChangedHost else onDetect
                else -> onDetect
            },
            icon = when (detectionState) {
                is DetectionUiState.Ready -> IconAsset.ArrowForward
                is DetectionUiState.Failed -> if (hostKeyChanged) IconAsset.Warning else IconAsset.Retry
                is DetectionUiState.Cancelled -> IconAsset.Retry
                is DetectionUiState.Idle -> IconAsset.Bolt
                is DetectionUiState.Running -> null
            },
            enabled = pingState.runStatus != PingRunStatus.RUNNING,
            warning = hostKeyChanged || vpnActive,
        )
    }
}
