package app.ownbridge

import androidx.compose.runtime.staticCompositionLocalOf

/** App-level short feedback shared by result cards and task actions. */
internal val LocalTransientNotice = staticCompositionLocalOf<(Int) -> Unit> {
    { error("App notice host is missing") }
}
