package app.ownbridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsLanguagePage(language: AppLanguage, onSelect: (AppLanguage) -> Boolean) {
    var saveFailed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentCard {
            AppLanguage.entries.forEach { option ->
                val title = when (option) {
                    AppLanguage.SYSTEM -> R.string.settings_language_system
                    AppLanguage.CHINESE -> R.string.settings_language_chinese
                    AppLanguage.TRADITIONAL_CHINESE -> R.string.settings_language_traditional_chinese
                    AppLanguage.ENGLISH -> R.string.settings_language_english
                    AppLanguage.RUSSIAN -> R.string.settings_language_russian
                    AppLanguage.SPANISH -> R.string.settings_language_spanish
                    AppLanguage.TURKISH -> R.string.settings_language_turkish
                    AppLanguage.PERSIAN -> R.string.settings_language_persian
                }
                ListItem(
                    headlineContent = { Text(stringResource(title)) },
                    trailingContent = { RadioButton(selected = option == language, onClick = null) },
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton) {
                        saveFailed = !onSelect(option)
                    },
                )
            }
        }
        Text(stringResource(R.string.settings_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (saveFailed) Text(stringResource(R.string.settings_language_failed),
            color = MaterialTheme.colorScheme.error)
    }
}
