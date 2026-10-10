package app.ownbridge

import app.ownbridge.core.ServerInventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ServerInventoryPanel(inventory: ServerInventory) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TerminalPalette.background,
        contentColor = TerminalPalette.foreground,
        shape = RectangleShape,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TerminalText(stringResource(R.string.server_inventory), title = true)
                HorizontalDivider(color = TerminalPalette.separator)
                TerminalField(stringResource(R.string.system), inventory.system)
                TerminalField(
                    stringResource(R.string.ipv4_addresses),
                    inventory.ipv4Addresses.ifEmpty { listOf(stringResource(R.string.none_found)) }.joinToString("\n"),
                )
                TerminalField(
                    stringResource(R.string.ipv6_addresses),
                    inventory.ipv6Addresses.ifEmpty { listOf(stringResource(R.string.none_found)) }.joinToString("\n"),
                )
                TerminalField(stringResource(R.string.firewall), inventory.firewall)
                HorizontalDivider(color = TerminalPalette.separator)
                TerminalText(stringResource(R.string.listening_ports), title = true)
                if (inventory.listeningPorts.isEmpty()) {
                    TerminalText(stringResource(R.string.none_found))
                } else {
                    inventory.listeningPorts.forEach { listener ->
                        TerminalText(
                            stringResource(
                                R.string.listener_entry,
                                listener.protocol,
                                listener.address,
                                listener.port,
                                listener.process,
                            ),
                        )
                    }
                }
                HorizontalDivider(color = TerminalPalette.separator)
                TerminalText(stringResource(R.string.running_services), title = true)
                if (inventory.runningServices.isEmpty()) {
                    TerminalText(stringResource(R.string.none_found))
                } else {
                    inventory.runningServices.forEach { TerminalText(it) }
                }
            }
        }
    }
}
