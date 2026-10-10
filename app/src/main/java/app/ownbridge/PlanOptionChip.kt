package app.ownbridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PlanOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label, modifier = Modifier.padding(end = 12.dp)) },
        )
        if (selected) {
            ProjectIcon(
                IconAsset.SuccessFilled,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
