package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsAboutPage() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context.packageName) {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    var linkFailed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.app_name))
        Text(stringResource(R.string.settings_about_version, version),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_about_description),
            style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_about_data_title))
        Text(stringResource(R.string.settings_about_data),
            style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_about_open_source_title))
        Text(stringResource(R.string.settings_about_open_source),
            style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = {
                linkFailed = runCatching { uriHandler.openUri(OWNBRIDGE_REPOSITORY_URL) }.isFailure
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            InlineIconText(OWNBRIDGE_REPOSITORY_URL.removePrefix("https://"), IconAsset.GitHub,
                style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.settings_about_third_party),
            style = MaterialTheme.typography.bodyMedium)
        thirdPartyLinks.forEach { link ->
            TextButton(
                onClick = {
                    linkFailed = runCatching { uriHandler.openUri(link.url) }.isFailure
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(link.titleRes)) }
        }
        if (linkFailed) Text(stringResource(R.string.settings_about_link_failed),
            color = MaterialTheme.colorScheme.error)
    }
}

private data class ThirdPartyLink(@StringRes val titleRes: Int, val url: String)

private const val OWNBRIDGE_REPOSITORY_URL = "https://github.com/cupp-debug/OwnBridge"

private val thirdPartyLinks = listOf(
    ThirdPartyLink(R.string.settings_about_mihomo, "https://github.com/MetaCubeX/mihomo"),
    ThirdPartyLink(R.string.settings_about_mihomo_bridge, "https://github.com/oviron/libmihomo-android"),
    ThirdPartyLink(R.string.settings_about_sing_box, "https://github.com/SagerNet/sing-box"),
    ThirdPartyLink(R.string.settings_about_material_symbols,
        "https://developers.google.com/fonts/docs/material_symbols"),
    ThirdPartyLink(R.string.settings_about_bootstrap_icons, "https://icons.getbootstrap.com/"),
    ThirdPartyLink(R.string.settings_about_metacubexd, "https://github.com/MetaCubeX/metacubexd"),
)
