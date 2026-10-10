package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal enum class Page(@StringRes val labelRes: Int, val icon: IconAsset) {
    INTRO(R.string.step_intro, IconAsset.Digit0),
    CONNECT(R.string.step_connect, IconAsset.Digit1),
    PLAN(R.string.step_plan, IconAsset.Digit2),
    PROGRESS(R.string.step_progress, IconAsset.Digit3),
    RESULT(R.string.step_result, IconAsset.Digit4),
}

@Composable
internal fun StepNavigation(current: Page, onSelect: (Page) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Page.entries.forEach { page ->
            val selected = page == current
            val interactionSource = remember(page) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val pressColor by animateColorAsState(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
                animationSpec = tween(UiMotion.stepSelectionDurationMillis),
                label = "step_press",
            )
            val iconColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                animationSpec = tween(UiMotion.stepSelectionDurationMillis),
                label = "step_selection_color",
            )
            val iconScale by animateFloatAsState(
                if (selected) UiMotion.selectedStepScale else 1f,
                animationSpec = tween(UiMotion.stepSelectionDurationMillis),
                label = "step_selection_scale",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(page) },
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(pressColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ProjectIcon(
                        page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                        tint = iconColor,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(stringResource(page.labelRes), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
