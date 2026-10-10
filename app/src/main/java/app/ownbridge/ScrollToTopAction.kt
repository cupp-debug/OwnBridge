package app.ownbridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Page-independent scroll affordance for any screen backed by a LazyListState. */
@Composable
internal fun ScrollToTopAction(
    listState: LazyListState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val thresholdPx = with(LocalDensity.current) { 120.dp.roundToPx() }
    val scrolledDown by remember(listState, thresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
    if (!enabled || !scrolledDown) return

    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.back_to_top)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        ) {
            ProjectIcon(IconAsset.BackToTop, contentDescription = null)
        }
    }
}
