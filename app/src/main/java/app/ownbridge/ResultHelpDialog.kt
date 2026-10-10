package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class HelpStep(val icon: IconAsset, @StringRes val textRes: Int)
internal data class HelpLink(@StringRes val textRes: Int, val url: String)

internal object OfficialReleaseLinks {
    val mihomo = HelpLink(R.string.help_visit_mihomo,
        "https://github.com/MetaCubeX/mihomo/releases")
    val flClash = HelpLink(R.string.help_visit_flclash,
        "https://github.com/chen08209/FlClash/releases")
    val v2rayN = HelpLink(R.string.help_visit_v2rayn,
        "https://github.com/2dust/v2rayN/releases")
    val v2rayNG = HelpLink(R.string.help_visit_v2rayng,
        "https://github.com/2dust/v2rayNG/releases")
}

/** Shared by the live result and its historical snapshot. */
internal enum class ResultHelpTopic(
    @StringRes val bodyRes: Int,
    val steps: List<HelpStep> = emptyList(),
    val links: List<HelpLink> = emptyList(),
) {
    NODE_LIST(R.string.help_node_list, links = listOf(OfficialReleaseLinks.mihomo)),
    CLASH_SUBSCRIPTION(
        R.string.help_clash_subscription,
        listOf(
            HelpStep(IconAsset.Copy, R.string.help_copy_subscription),
            HelpStep(IconAsset.Link, R.string.help_import_subscription),
        ),
        listOf(OfficialReleaseLinks.flClash),
    ),
    CLASH_YAML(
        R.string.help_clash_yaml,
        listOf(HelpStep(IconAsset.Download, R.string.help_download_yaml)),
        listOf(OfficialReleaseLinks.flClash),
    ),
    V2RAY_LINKS(
        R.string.help_v2ray_links,
        listOf(HelpStep(IconAsset.Copy, R.string.help_copy_v2ray)),
        listOf(
            OfficialReleaseLinks.v2rayN,
            OfficialReleaseLinks.v2rayNG,
        ),
    ),
    SS_NODE(
        R.string.help_ss_node,
        listOf(HelpStep(IconAsset.Copy, R.string.help_copy_ss_node)),
    ),
    ADDRESSES(R.string.help_history_addresses),
    PROTOCOLS(R.string.help_history_protocols),
    LOGS(R.string.help_history_logs),
    DELIVERY_EVENTS(R.string.help_history_delivery_events),
}

@Composable
internal fun ResultHelpDialog(topic: ResultHelpTopic, onDismiss: () -> Unit) {
    var linkFailed by remember(topic) { mutableStateOf(false) }
    val actionStyle = MaterialTheme.typography.bodyLarge
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(topic.bodyRes), style = actionStyle)
                topic.steps.forEach { step ->
                    InlineIconText(stringResource(step.textRes), step.icon,
                        style = actionStyle, iconTint = MaterialTheme.colorScheme.primary)
                }
                topic.links.forEach { link ->
                    HelpProjectLink(link) { linkFailed = true }
                }
                if (linkFailed) Text(stringResource(R.string.settings_about_link_failed),
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
internal fun HelpProjectLink(link: HelpLink, onOpenFailed: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = {
            if (runCatching { uriHandler.openUri(link.url) }.isFailure) onOpenFailed()
        },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        InlineIconText(stringResource(link.textRes), IconAsset.GitHub,
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}
