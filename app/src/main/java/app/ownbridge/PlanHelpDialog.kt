package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal enum class ProtocolOption(
    val id: String,
    @StringRes val descriptionRes: Int,
) {
    VLESS("VLESS", R.string.protocol_vless_description),
    SHADOWSOCKS("SS", R.string.protocol_ss_description),
    HYSTERIA2("HY2", R.string.protocol_hy2_description),
}

internal enum class ClientOption(
    val id: String,
    val helpLabel: String,
    @StringRes val descriptionRes: Int,
) {
    CLASH("Clash / Mihomo", "Clash", R.string.client_clash_description),
    V2RAY("v2rayN / v2rayNG", "v2rayN/NG", R.string.client_v2ray_description),
}

internal enum class PlanHelpTopic { ADDRESS, PROTOCOL, CLIENT }

private data class HelpEntry(
    val label: String,
    @StringRes val descriptionRes: Int,
    val links: List<HelpLink> = emptyList(),
)

private val ClientOption.releaseLinks: List<HelpLink> get() = when (this) {
    ClientOption.CLASH -> listOf(OfficialReleaseLinks.flClash)
    ClientOption.V2RAY -> listOf(OfficialReleaseLinks.v2rayN, OfficialReleaseLinks.v2rayNG)
}

@Composable
internal fun PlanHelpDialog(topic: PlanHelpTopic, onDismiss: () -> Unit) {
    val entries = when (topic) {
        PlanHelpTopic.ADDRESS -> listOf(
            HelpEntry("IPv4", R.string.address_ipv4_description),
            HelpEntry("IPv6", R.string.address_ipv6_description),
        )
        PlanHelpTopic.PROTOCOL -> ProtocolOption.entries.map { HelpEntry(it.id, it.descriptionRes) }
        PlanHelpTopic.CLIENT -> listOf(HelpEntry(
            stringResource(R.string.client_what_is_this_tab),
            R.string.client_what_is_this_description,
        )) + ClientOption.entries.map { HelpEntry(it.helpLabel, it.descriptionRes, it.releaseLinks) }
    }
    var selectedIndex by rememberSaveable(topic) { mutableIntStateOf(0) }
    var linkFailed by remember(topic) { mutableStateOf(false) }
    val activeIndex = selectedIndex.coerceIn(entries.indices)
    val detailsScrollState = rememberScrollState()
    val links = entries[activeIndex].links

    LaunchedEffect(selectedIndex) {
        detailsScrollState.scrollTo(0)
        linkFailed = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryTabRow(selectedTabIndex = activeIndex) {
                    entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = activeIndex == index,
                            onClick = { selectedIndex = index },
                            text = { Text(entry.label) },
                        )
                    }
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(detailsScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(entries[activeIndex].descriptionRes),
                        style = MaterialTheme.typography.bodyLarge)
                    links.forEach { link ->
                        HelpProjectLink(link) { linkFailed = true }
                    }
                    if (linkFailed) Text(stringResource(R.string.settings_about_link_failed),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
