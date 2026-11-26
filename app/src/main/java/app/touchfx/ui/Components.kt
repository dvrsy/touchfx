package app.touchfx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.touchfx.effects.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun rgbColor(c: Int) = Color(0xFF000000.toInt() or c)

/** Live LED strip preview. Pixels are 0xRRGGBB; null shows an unlit strip. */
@Composable
fun LedStrip(pixels: IntArray?, ledCount: Int, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(TouchColors.well)
    ) {
        val n = max(1, pixels?.size ?: ledCount)
        // Draw at most one LED per ~3 px so long strips stay readable.
        val cells = min(n, max(1, (size.width / 3f).toInt()))
        val cellW = size.width / cells
        val gap = if (cellW > 6f) 1.5f else 0f
        for (c in 0 until cells) {
            val idx = (c.toLong() * n / cells).toInt()
            val px = pixels?.getOrNull(idx) ?: 0
            val color = if (px == 0) TouchColors.ledOff else rgbColor(px)
            // Soft glow behind lit LEDs.
            if (px != 0) {
                drawRect(
                    color = color.copy(alpha = 0.35f),
                    topLeft = Offset(c * cellW - cellW * 0.3f, 0f),
                    size = Size(cellW * 1.6f, size.height),
                )
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(c * cellW + gap / 2, size.height * 0.2f),
                size = Size(cellW - gap, size.height * 0.6f),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }
}

@Composable
fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val colors = palette.previewColors.map { rgbColor(it) }
    val brush = if (colors.size == 1) Brush.horizontalGradient(listOf(colors[0], colors[0]))
    else Brush.horizontalGradient(colors)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .size(68.dp, 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            palette.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange)
    }
}

/**
 * Live matrix preview: [pixels] row-major, [rows] rows of `pixels.size / rows`. Drawn with the
 * matrix's aspect ratio, at most [maxHeight] tall. Null shows an unlit matrix of [ledCount].
 */
@Composable
fun MatrixPreview(pixels: IntArray?, ledCount: Int, rows: Int, modifier: Modifier = Modifier, maxHeight: Dp = 180.dp) {
    val n = pixels?.size ?: ledCount
    val h = rows.coerceIn(1, max(1, n))
    val w = max(1, n / h)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = min(maxWidth.value / w, maxHeight.value / h)
        Canvas(
            Modifier
                .size((cell * w).dp, (cell * h).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(TouchColors.well)
        ) {
            val cw = size.width / w
            val ch = size.height / h
            val gap = if (cw > 5f) 1f else 0f
            for (y in 0 until h) for (x in 0 until w) {
                val px = pixels?.getOrNull(y * w + x) ?: 0
                drawRect(
                    color = if (px == 0) TouchColors.ledOff else rgbColor(px),
                    topLeft = Offset(x * cw + gap / 2, y * ch + gap / 2),
                    size = Size(cw - gap, ch - gap),
                )
            }
        }
    }
}

/**
 * A row of LED dots like a mixer's level meter: lit dots in blue, the top ones in orange (peak),
 * unlit dots dark. [marker] (0..1) draws a thin line, e.g. a threshold.
 */
@Composable
fun DotMeter(value: Float, modifier: Modifier = Modifier, dots: Int = 32, lit: Color = TouchColors.blue, marker: Float? = null) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val slot = size.width / dots
        val d = minOf(slot * 0.7f, size.height)
        val litDots = (value.coerceIn(0f, 1f) * dots + 0.5f).toInt()
        for (i in 0 until dots) {
            val color = when {
                i >= litDots -> TouchColors.ledOff
                i >= dots * 0.85f -> TouchColors.orange
                else -> lit
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - d) / 2, (size.height - d) / 2),
                size = Size(d, d),
                cornerRadius = CornerRadius(d / 3, d / 3),
            )
        }
        marker?.let { m ->
            val x = size.width * m.coerceIn(0f, 1f)
            drawRect(markerColor, topLeft = Offset(x - 1.5f, 0f), size = Size(3f, size.height))
        }
    }
}
