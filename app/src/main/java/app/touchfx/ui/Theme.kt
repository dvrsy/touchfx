package app.touchfx.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * TouchFx look: a dark DJ-mixer style. Near-black panels with thin dividers, bright blue for
 * anything active (buttons, switches, meters), orange for peaks and hold/strobe accents, green
 * for "running", small square-ish corners and grey uppercase labels.
 */
object TouchColors {
    val blue = Color(0xFF1E8FFF)
    val orange = Color(0xFFFF6A2A)
    val green = Color(0xFF15C281)
    /** Unlit LED of a meter. */
    val ledOff = Color(0xFF2A2A2E)
    /** Background of previews and meters. */
    val well = Color(0xFF0B0B0C)
}

private val Colors = darkColorScheme(
    primary = TouchColors.blue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0D3459),
    onPrimaryContainer = Color(0xFFD5E8FF),
    inversePrimary = Color(0xFF005DB5),
    secondary = Color(0xFF8FB8E6),
    onSecondary = Color(0xFF0B1A2A),
    secondaryContainer = Color(0xFF243344),
    onSecondaryContainer = Color(0xFFDCE7F3),
    tertiary = TouchColors.orange,
    onTertiary = Color(0xFF1C0A00),
    tertiaryContainer = Color(0xFF5A2410),
    onTertiaryContainer = Color(0xFFFFDCCB),
    error = Color(0xFFFF4D4F),
    onError = Color(0xFF2A0000),
    background = Color(0xFF121214),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA8A8AE),
    surfaceTint = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF0B0B0C),
    surfaceContainerLow = Color(0xFF18181A),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF26262A),
    surfaceContainerHighest = Color(0xFF303035),
    outline = Color(0xFF4A4A50),
    outlineVariant = Color(0xFF2E2E33),
)

/** Small, square-ish corners like hardware buttons. */
private val TouchShapesTheme = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

object TouchShapes {
    /** Buttons: Material buttons are pill-shaped regardless of the theme, so they pass this. */
    val control: Shape = RoundedCornerShape(4.dp)
}

/** A segmented button's shape with TouchFx's square corners (Material's default is a pill). */
@Composable
fun segmentShape(index: Int, count: Int): Shape =
    SegmentedButtonDefaults.itemShape(index, count, RoundedCornerShape(4.dp))

@Composable
fun TouchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), shapes = TouchShapesTheme, content = content)
}
