package app.ownbridge

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun HistoryDeleteDialog(
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.history_delete_title),
            style = MaterialTheme.typography.dialogTitle) },
        text = { Text(stringResource(R.string.history_delete_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(stringResource(R.string.history_delete_record))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
