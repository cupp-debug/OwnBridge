package app.ownbridge

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val lightPageBackground = Color(0xFFECECEC)
private val lightContainerSurface = Color.White

private val appShapes = Shapes(
    medium = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

internal val Typography.containerTitle: TextStyle
    get() = titleMedium.copy(fontWeight = FontWeight.W700)

internal val Typography.dialogTitle: TextStyle
    get() = headlineSmall.copy(fontWeight = FontWeight.W700)

private val lightColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F1D8),
    onPrimaryContainer = Color(0xFF00382B),
    secondaryContainer = Color(0xFFDCEDE4),
    tertiary = Color(0xFF996300),
    tertiaryContainer = Color(0xFFFFCE54),
    onTertiaryContainer = Color(0xFF382900),
    background = lightPageBackground,
    surface = lightContainerSurface,
    surfaceContainerLowest = lightContainerSurface,
    surfaceContainerLow = lightContainerSurface,
    surfaceContainer = lightContainerSurface,
    surfaceContainerHigh = lightContainerSurface,
    surfaceContainerHighest = lightContainerSurface,
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF80D9B8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFFB6F1D8),
    tertiary = Color(0xFFFFD54F),
    tertiaryContainer = Color(0xFFE4B743),
    onTertiaryContainer = Color(0xFF2D2100),
)

@Composable
internal fun OwnBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        shapes = appShapes,
        content = content,
    )
}
