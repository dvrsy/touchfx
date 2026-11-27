package app.touchfx.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.touchfx.engine.LightEngine
import app.touchfx.engine.PadState
import app.touchfx.engine.PadTarget
import app.touchfx.engine.Timing
import app.touchfx.engine.XyPad

/**
 * X/Y pad: drag to change the running effects live, e.g. speed left/right and hue up/down.
 * The centre leaves everything as saved; nothing is saved. Lifting the finger returns to normal
 * unless "Hold position" is on; closing the pad always does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XyPadScreen(onDismiss: () -> Unit, lightId: String? = null) {
    val prefs = LocalContext.current.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun saved(key: String, default: PadTarget) =
        runCatching { PadTarget.valueOf(prefs.getString(key, default.name)!!) }.getOrDefault(default)
    var xTarget by remember { mutableStateOf(saved("pad.x", PadTarget.SPEED)) }
    var yTarget by remember { mutableStateOf(saved("pad.y", PadTarget.HUE)) }
    var hold by remember { mutableStateOf(false) }
    /** Finger position, 0..1 both ways (y = 1 at the top); null = released. */
    var pos by remember { mutableStateOf<Offset?>(null) }
    val lights by LightEngine.virtuals.collectAsStateWithLifecycle()
    // Opened from a light's screen: only that light; otherwise every light that is on.
    val single = lightId?.let { id -> lights.firstOrNull { it.id == id } }
    val running = if (single != null) listOf(single) else lights.filter { it.enabled }
    val effects = running.map { it.settings.effectId }
    // Only what works on at least one running light is offered. A chosen target that doesn't
    // work right now counts as Nothing, but stays saved for when it works again.
    val available = XyPad.available(effects)
    val xUsed by rememberUpdatedState(XyPad.effective(xTarget, available))
    val yUsed by rememberUpdatedState(XyPad.effective(yTarget, available))

    fun push() = LightEngine.setPad(pos?.let { PadState(it.x, it.y, xUsed, yUsed, lightId) })
    DisposableEffect(Unit) { onDispose { LightEngine.setPad(null) } }
    // The running lights changed (e.g. a playlist moved on) while the pad is held.
    LaunchedEffect(xUsed, yUsed) { push() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (single != null) "XY pad · ${single.name}" else "XY pad · all lights") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close the pad") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AxisPicker("X ↔", xUsed, available, running.size, Modifier.weight(1f)) { xTarget = it; prefs.edit().putString("pad.x", it.name).apply() }
                    AxisPicker("Y ↕", yUsed, available, running.size, Modifier.weight(1f)) { yTarget = it; prefs.edit().putString("pad.y", it.name).apply() }
                }
                Text(
                    when {
                        running.isEmpty() -> "No lights are on."
                        else -> listOf(xUsed, yUsed).filter { it != PadTarget.NONE }.distinct().joinToString(" · ") { t ->
                            val n = available[t] ?: 0
                            val beat = t == PadTarget.TIME && effects.any { XyPad.timing(it) == Timing.BEAT }
                            "${t.label}: " + when {
                                single != null -> "works on this light"
                                n == running.size -> "all ${running.size} running lights"
                                else -> "$n of ${running.size} running lights"
                            } + (if (beat) " (beat effects: ¼, ½, 2× or 4× time)" else "") +
                                (if (t == PadTarget.STROBE) " (left: steady, right: on the beat)" else "")
                        }.ifEmpty { "Choose what the axes change." }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val grid = MaterialTheme.colorScheme.outlineVariant
                val cross = MaterialTheme.colorScheme.outline
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TouchColors.well)
                        .pointerInput(hold) {
                            fun place(o: Offset) {
                                pos = Offset((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                                push()
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                place(down.position)
                                down.consume()
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    place(change.position)
                                    change.consume()
                                }
                                if (!hold) { pos = null; push() }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (k in 1..3) {
                            val f = k / 4f
                            drawLine(if (k == 2) cross else grid, Offset(size.width * f, 0f), Offset(size.width * f, size.height), strokeWidth = if (k == 2) 2f else 1f)
                            drawLine(if (k == 2) cross else grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = if (k == 2) 2f else 1f)
                        }
                        pos?.let { p ->
                            val c = Offset(p.x * size.width, (1f - p.y) * size.height)
                            drawCircle(TouchColors.orange.copy(alpha = 0.25f), radius = 56f, center = c)
                            drawCircle(TouchColors.orange, radius = 22f, center = c)
                            drawCircle(TouchColors.blue, radius = 40f, center = c, style = Stroke(width = 4f))
                        }
                    }
                    Text(
                        "${xTarget.label} →", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    )
                    Text(
                        "↑ ${yTarget.label}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hold position", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = hold, onCheckedChange = { on -> hold = on; if (!on) { pos = null; push() } })
                    TextButton(onClick = { pos = null; push() }) { Text("Reset") }
                }
                Text(
                    "The centre leaves everything as saved; small moves change a little, and towards the edges " +
                        "a setting goes to its minimum or maximum. The menus only offer what the running lights " +
                        "can use. Speed, Size and Intensity move each effect's own setting. Colours glides " +
                        "through the palettes, sorted by colour. Overall speed runs effects 0.25× to 4×; effects " +
                        "that follow the beat switch to quarter, half, double or quadruple time, and effects that " +
                        "only follow the sound can't be sped up. Hue, Brightness and Saturation (grey to full " +
                        "colour) work on every light, Palette shift on effects that use the palette. Strobe " +
                        "flashes the lights: left of the centre at a steady rate (slow to fast), right of it on " +
                        "the beat (1, 2, 4 or 8 flashes per beat). Nothing is saved, and lifting your finger " +
                        "returns to normal unless Hold position is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AxisPicker(
    label: String, value: PadTarget, available: Map<PadTarget, Int>, total: Int, modifier: Modifier,
    onPick: (PadTarget) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(shape = TouchShapes.control, onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label  ${value.label}", modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            available.forEach { (t, n) ->
                // Works on only some of the running lights: say on how many.
                val part = if (t != PadTarget.NONE && n < total) "  ($n of $total)" else ""
                DropdownMenuItem(text = { Text(t.label + part) }, onClick = { open = false; onPick(t) })
            }
        }
    }
}
