package app.ownbridge

import androidx.compose.ui.unit.dp

/** Small, shared motion values; navigation never owns or restarts deployment work. */
internal object UiMotion {
    const val pageEnterDurationMillis = 200
    val pageEnterDistance = 16.dp
    const val stepSelectionDurationMillis = 160
    const val selectedStepScale = 1.06f
}

internal sealed interface ContentRoute {
    data class Step(val page: Page) : ContentRoute
    data class Settings(val destination: SettingsDestination) : ContentRoute
}

/** Positive means the new page enters from the right; negative means it enters from the left. */
internal fun ContentRoute.enterDirectionFrom(previous: ContentRoute): Float {
    fun ContentRoute.depth(): Int = when (this) {
        is ContentRoute.Step -> page.ordinal
        is ContentRoute.Settings -> Page.entries.size +
            if (destination == SettingsDestination.HOME) 0 else 1
    }
    return if (depth() >= previous.depth()) 1f else -1f
}
