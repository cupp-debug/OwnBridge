package app.ownbridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.ownbridge.core.HistoryKind
import app.ownbridge.core.HistorySnapshot

@Composable
internal fun SettingsHistoryPage(
    records: List<HistorySnapshot>,
    loading: Boolean,
    storageError: Boolean,
    unreadableCount: Int,
    selectedId: String?,
    onSelectedIdChange: (String?) -> Unit,
) {
    val selected = records.firstOrNull { it.id == selectedId }
    if (selected != null) {
        HistoryDetailPage(selected)
        return
    }
    Column {
        if (storageError) Text(stringResource(R.string.history_storage_error),
            color = MaterialTheme.colorScheme.error)
        if (unreadableCount > 0) Text(stringResource(R.string.history_unreadable, unreadableCount),
            color = MaterialTheme.colorScheme.error)
        if (loading) Text(stringResource(R.string.history_loading),
            style = MaterialTheme.typography.bodyMedium)
        if (!loading && records.isEmpty()) Text(stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium)
        if (records.isNotEmpty()) ContentCard {
            val leadingIconSize = with(LocalDensity.current) {
                MaterialTheme.typography.bodyLarge.fontSize.toDp()
            }
            records.forEachIndexed { index, item ->
                ListItem(
                    headlineContent = { Text(item.host, maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        val connection = item.kind == HistoryKind.CONNECTION
                        ProjectIcon(if (connection) IconAsset.Bolt else IconAsset.Rocket,
                            contentDescription = stringResource(if (connection)
                                R.string.history_connection else R.string.history_deployment),
                            modifier = Modifier.size(leadingIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        HistoryStatus(item.outcome, MaterialTheme.typography.bodyMedium,
                            compact = true)
                    },
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        onSelectedIdChange(item.id)
                    },
                )
                if (index != records.lastIndex) HorizontalDivider()
            }
        }
    }
}
