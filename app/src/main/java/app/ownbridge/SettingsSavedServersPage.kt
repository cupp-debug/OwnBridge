package app.ownbridge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSavedServersPage(
    hostKeys: HostKeyStore,
    onForget: (String) -> Boolean,
) {
    var identities by remember(hostKeys) { mutableStateOf(hostKeys.savedIdentities()) }
    var pendingForget by remember { mutableStateOf<String?>(null) }
    var forgetFailed by remember { mutableStateOf(false) }

    Column {
        InlineIconText(stringResource(R.string.settings_saved_servers_explanation),
            IconAsset.Lightbulb,
            style = MaterialTheme.typography.bodyMedium,
            iconTint = MaterialTheme.colorScheme.tertiary)
        if (identities.isEmpty()) {
            Text(stringResource(R.string.settings_saved_servers_empty),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium)
        } else {
            ContentCard(modifier = Modifier.padding(top = 16.dp)) {
                identities.entries.forEachIndexed { index, (host, fingerprint) ->
                    if (index > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(host) },
                        supportingContent = { Text(fingerprint, fontFamily = FontFamily.Monospace) },
                        trailingContent = {
                            IconButton(onClick = { pendingForget = host }) {
                                ProjectIcon(IconAsset.KeyOff,
                                    contentDescription = stringResource(
                                        R.string.settings_forget_action, host),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (forgetFailed) Text(stringResource(R.string.settings_forget_failed),
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.error)
    }
    pendingForget?.let { host ->
        AlertDialog(
            onDismissRequest = { pendingForget = null },
            title = { Text(stringResource(R.string.settings_forget_host),
                style = MaterialTheme.typography.dialogTitle) },
            text = { Text(stringResource(R.string.settings_forget_warning, host)) },
            confirmButton = {
                TextButton(onClick = {
                    forgetFailed = !onForget(host)
                    if (!forgetFailed) identities = hostKeys.savedIdentities()
                    pendingForget = null
                }) { Text(stringResource(R.string.settings_confirm_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingForget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
