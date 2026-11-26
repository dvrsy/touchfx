package app.touchfx.ui

import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.engine.LightEngine
import app.touchfx.model.DeviceConfig
import app.touchfx.model.DeviceKind
import app.touchfx.model.VirtualConfig
import app.touchfx.engine.LightKind
import app.touchfx.engine.PixelMapper
import app.touchfx.engine.ViewFilters
import app.touchfx.net.WledApi
import app.touchfx.net.WledDiscovery

@Composable
fun LightsScreen(
    padding: PaddingValues,
    running: Boolean,
    onStart: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenWled: (String) -> Unit,
) {
    val devices by LightEngine.devices.collectAsStateWithLifecycle()
    val virtuals by LightEngine.virtuals.collectAsStateWithLifecycle()
    val filter = rememberFilter("filter.lights", LightKind.entries)
    var showPad by remember { mutableStateOf(false) }
    if (showPad) XyPadScreen(onDismiss = { showPad = false })
    val shown = ViewFilters.lights(virtuals, filter.value)
    val previews by LightEngine.previews.collectAsStateWithLifecycle()
    val master by LightEngine.masterBrightness.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!running) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lights are off", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Start the engine to react to music.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(shape = TouchShapes.control, onClick = onStart) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text(" Start")
                        }
                    }
                }
            }
        }
        item(key = "master") { MasterBrightnessCard(master) }
        if (virtuals.isNotEmpty()) {
            item(key = "filter") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterRow(
                        options = listOf(LightKind.LIGHT to "Lights", LightKind.GROUP to "Groups"),
                        counts = virtuals.groupingBy { ViewFilters.kindOf(it) }.eachCount(),
                        selected = filter.value,
                        onChange = { filter.set(it) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPad = true }) {
                        Icon(Icons.Rounded.OpenWith, null, Modifier.size(18.dp))
                        Text(" XY pad")
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        "Nothing to show with this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        if (virtuals.isEmpty()) {
            item {
                Text(
                    "No lights yet. Add WLED controllers or a preview strip on the Devices tab, " +
                        "then group them here with \"New group\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
        items(shown, key = { it.id }) { virtual ->
            val device = virtual.isDevice?.let { id -> devices.firstOrNull { it.id == id } }
            LightCard(
                virtual = virtual,
                device = device,
                pixels = previews[virtual.id],
                onClick = { onOpen(virtual.id) },
                onToggle = { on -> LightEngine.setEnabled(virtual.id, on) },
                onOpenWled = { device?.let { onOpenWled(it.id) } },
            )
        }
    }
}

@Composable
private fun MasterBrightnessCard(value: Float) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BrightnessLow, "All lights dimmer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = local,
                onValueChange = { local = it; LightEngine.setMasterBrightness(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Icon(Icons.Rounded.BrightnessHigh, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "${(local * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp).padding(start = 8.dp),
            )
        }
    }
}

/** A light on the Lights tab: a device's own virtual, or a group. */
@Composable
private fun LightCard(
    virtual: VirtualConfig,
    device: DeviceConfig?,
    pixels: IntArray?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpenWled: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        device == null -> Icons.Rounded.Workspaces
                        device.kind == DeviceKind.WLED -> Icons.Rounded.Router
                        else -> Icons.Rounded.PhoneAndroid
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(virtual.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            device == null -> "Group · ${virtual.segments.map { it.deviceId }.distinct().size} devices · " +
                                "${PixelMapper.pixelCount(virtual)} LEDs"
                            device.kind == DeviceKind.WLED -> "WLED · ${device.host} · ${device.ledCount} LEDs"
                            else -> "On-screen preview · ${device.ledCount} LEDs"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (device?.kind == DeviceKind.WLED) {
                    IconButton(onClick = onOpenWled) { Icon(Icons.Rounded.Language, "Open WLED") }
                }
                Switch(checked = virtual.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(14.dp))
            if (virtual.rows > 1) {
                MatrixPreview(
                    pixels = if (virtual.enabled) pixels else null,
                    ledCount = PixelMapper.pixelCount(virtual),
                    rows = virtual.rows,
                    maxHeight = 120.dp,
                )
            } else {
                LedStrip(pixels = if (virtual.enabled) pixels else null, ledCount = PixelMapper.pixelCount(virtual))
            }
            var bright by remember(virtual.settings.brightness) { mutableFloatStateOf(virtual.settings.brightness) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BrightnessMedium, "Brightness", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = bright,
                    onValueChange = { v -> bright = v; LightEngine.updateSettings(virtual.id) { it.copy(brightness = v) } },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(bright * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(Effects.info(virtual.settings.effectId).name) })
                AssistChip(onClick = onClick, label = {
                    Text(if (virtual.settings.customGradient != null) "Preset colours" else Palettes.get(virtual.settings.paletteId).name)
                })
            }
        }
    }
}
