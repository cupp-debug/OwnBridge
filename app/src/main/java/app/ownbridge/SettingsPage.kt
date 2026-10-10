package app.ownbridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role

internal enum class SettingsDestination { HOME, HISTORY, SERVERS, LANGUAGE, ABOUT }

@Composable
internal fun SettingsPage(
    onOpen: (SettingsDestination) -> Unit,
) {
    ContentCard {
        SettingsLink(R.string.settings_history, IconAsset.History) {
            onOpen(SettingsDestination.HISTORY)
        }
        SettingsLink(R.string.settings_saved_servers, IconAsset.Key) {
            onOpen(SettingsDestination.SERVERS)
        }
        SettingsLink(R.string.settings_language, IconAsset.Web) {
            onOpen(SettingsDestination.LANGUAGE)
        }
        SettingsLink(R.string.settings_about, IconAsset.Info) {
            onOpen(SettingsDestination.ABOUT)
        }
    }
}

@Composable
private fun SettingsLink(titleRes: Int, icon: IconAsset, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        leadingContent = { ProjectIcon(icon, contentDescription = null) },
        trailingContent = { ProjectIcon(IconAsset.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    )
}
