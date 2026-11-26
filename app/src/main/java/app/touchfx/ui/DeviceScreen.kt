package app.touchfx.ui

import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Tune
import app.touchfx.effects.EffectListParam
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import app.touchfx.model.VirtualConfig
import app.touchfx.model.Mapping
import app.touchfx.model.PanelLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.touchfx.effects.EffectCategory
import app.touchfx.effects.EffectInfo
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.Preset
import app.touchfx.engine.LightEngine
import app.touchfx.engine.PixelMapper
import app.touchfx.model.DeviceKind
import app.touchfx.net.WledApi
import kotlin.math.roundToInt

private val LAYOUT_KEYS = setOf("blur", "mirror", "flip", "rotate", "flip_horizontal", "flip_vertical")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
/**
 * Settings for one light (a virtual): effect, presets, colours, layout. For a device's own virtual
 * it also shows the device: LED count, WLED page and removal.
 */
fun LightScreen(virtualId: String, onBack: () -> Unit, onOpenWled: (String) -> Unit, onEditGroup: (String) -> Unit) {
    val devices by LightEngine.devices.collectAsStateWithLifecycle()
    val virtuals by LightEngine.virtuals.collectAsStateWithLifecycle()
    val previews by LightEngine.previews.collectAsStateWithLifecycle()
    val userPresets by LightEngine.userPresets.collectAsStateWithLifecycle()
    val virtual = virtuals.firstOrNull { it.id == virtualId }
    LaunchedEffect(virtual == null) { if (virtual == null) onBack() }
    if (virtual == null) return
    val device = virtual.isDevice?.let { id -> devices.firstOrNull { it.id == id } }

    val s = virtual.settings
    val info = Effects.info(s.effectId)
    // A new holder whenever the saved values change: Compose's strong skipping compares by
    // instance, so mutating one holder in place left controls showing stale values.
    val config = s.config(info.id)
    val values = remember(info.id, config) { ParamValues(info.params, config) }

    var confirmDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<String?>(null) }
    var showPad by remember { mutableStateOf(false) }
    if (showPad) XyPadScreen(onDismiss = { showPad = false }, lightId = virtualId)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(virtual.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (device?.kind == DeviceKind.WLED) {
                        IconButton(onClick = { onOpenWled(device.id) }) { Icon(Icons.Rounded.Language, "Open WLED") }
                    }
                    IconButton(onClick = { showPad = true }) { Icon(Icons.Rounded.OpenWith, "XY pad for this light") }
                    if (virtual.isGroup) {
                        IconButton(onClick = { onEditGroup(virtual.id) }) { Icon(Icons.Rounded.Edit, "Edit group") }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Remove") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp)
        ) {
            // ---- preview, on/off, brightness
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    if (virtual.rows > 1) {
                        MatrixPreview(
                            pixels = if (virtual.enabled) previews[virtual.id] else null,
                            ledCount = PixelMapper.pixelCount(virtual),
                            rows = virtual.rows,
                        )
                    } else {
                        LedStrip(
                            pixels = if (virtual.enabled) previews[virtual.id] else null,
                            ledCount = PixelMapper.pixelCount(virtual),
                            height = 34.dp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (device?.kind == DeviceKind.PREVIEW) "Show preview" else "Send to lights",
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = virtual.enabled,
                            onCheckedChange = { on -> LightEngine.setEnabled(virtualId, on) },
                        )
                    }
                    SettingSlider("Brightness", "${(s.brightness * 100).roundToInt()}%", s.brightness, 0f..1f) { v ->
                        LightEngine.updateSettings(virtualId) { it.copy(brightness = v) }
                    }
                }
            }

            // ---- effect
            SectionTitle("Effect")
            Card(
                onClick = { showPicker = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${info.category.title} · ${info.description}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.Rounded.SwapHoriz, "Change effect")
                }
            }

            // ---- presets
            val builtIn = remember(info.id) { Preset.builtInFor(info.id) }
            val mine = userPresets.filter { it.effectId == info.id }
            SectionTitle("Presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(builtIn, key = { it.id }) { p ->
                    AssistChip(
                        onClick = { LightEngine.updateSettings(virtualId) { p.applyTo(it) } },
                        label = { Text(p.name.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = p.gradient?.let { g -> { GradientDot(g) } },
                    )
                }
                items(mine, key = { it.id }) { p ->
                    InputChip(
                        selected = false,
                        onClick = { LightEngine.updateSettings(virtualId) { p.applyTo(it) } },
                        label = { Text(p.name) },
                        leadingIcon = p.gradient?.let { g -> { GradientDot(g) } },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close, "Delete preset",
                                Modifier.size(18.dp).clickable { LightEngine.deletePreset(p.id) },
                            )
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showSave = true },
                        label = { Text("Save current") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }
            if (builtIn.isEmpty() && mine.isEmpty()) {
                Text(
                    "No presets for this effect yet. Tune it below and tap \"Save current\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- colours
            val toggle = info.gradientToggle
            if (info.usesGradient) {
                SectionTitle("Colours")
                // Effects like Scan or Filter use their own colour unless "Use gradient" is on (as in
                // LedFx); keep that switch here, next to the palettes it enables.
                toggle?.let { key ->
                    info.params.first { it.key == key }.let { param ->
                        ParamControl(param, values) { v -> LightEngine.updateSettings(virtualId) { it.withValue(key, v) } }
                    }
                    if (!values.b(key)) {
                        Text(
                            "Off: the effect uses its own colour (in its settings below). Picking a palette turns this on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LazyRow {
                    s.customGradient?.let { g ->
                        item(key = "custom") { CustomGradientSwatch(g) }
                    }
                    items(Palettes.all, key = { it.id }) { p ->
                        PaletteSwatch(p, selected = s.customGradient == null && p.id == s.paletteId) {
                            LightEngine.updateSettings(virtualId) {
                                val picked = it.copy(paletteId = p.id, customGradient = null)
                                // Picking colours means "use them": switch the effect's gradient on.
                                if (toggle != null) picked.withValue(toggle, true) else picked
                            }
                        }
                    }
                }
                info.params.firstOrNull { it.key == "gradient_roll" }?.let { roll ->
                    ParamControl(roll, values) { v -> LightEngine.updateSettings(virtualId) { it.withValue(roll.key, v) } }
                }
            }

            // ---- effect settings
            val specific = info.params.filter { it.key !in LAYOUT_KEYS && it.key != "gradient_roll" && (it.key != toggle || !info.usesGradient) }
            if (specific.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("${info.name} settings", Modifier.weight(1f))
                    TextButton(onClick = {
                        LightEngine.updateSettings(virtualId) { it.copy(configs = it.configs - info.id) }
                    }) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                        Text(" Reset")
                    }
                }
                specific.forEach { param ->
                    ParamControl(param, values) { v -> LightEngine.updateSettings(virtualId) { it.withValue(param.key, v) } }
                }
            }

            // ---- settings of the effects an Effect Chase / Blend runs
            info.params.filterIsInstance<EffectListParam>().takeIf { it.isNotEmpty() }?.let { lists ->
                val chosen = lists.flatMap { EffectListParam.parse(values.s(it.key)) }.distinct().filter { Effects.exists(it) }
                if (chosen.isNotEmpty()) {
                    SectionTitle("Chosen effects")
                    Text(
                        "Each runs with this light's own settings for it and the colours above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    chosen.forEach { id ->
                        OutlinedButton(shape = TouchShapes.control, onClick = { editingChild = id }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("${Effects.info(id).name} settings", modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Tune, null)
                        }
                    }
                }
            }

            SectionTitle("Layout")
            info.params.filter { it.key in LAYOUT_KEYS }.forEach { param ->
                ParamControl(param, values) { v -> LightEngine.updateSettings(virtualId) { it.withValue(param.key, v) } }
            }

            MatrixSection(virtual)

            // ---- strip (only for a device's own virtual)
            if (device != null) {
                SectionTitle("Strip")
                var countText by remember(device.ledCount) { mutableStateOf(device.ledCount.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { t ->
                            countText = t.filter { it.isDigit() }.take(4)
                            countText.toIntOrNull()?.takeIf { it in 1..4096 }?.let { n ->
                                LightEngine.updateDevice(device.id) { it.copy(ledCount = n) }
                            }
                        },
                        label = { Text("Number of LEDs") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (device.kind == DeviceKind.WLED) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(shape = TouchShapes.control, onClick = {
                            scope.launch {
                                WledApi.fetchInfo(device.host)?.let { info ->
                                    LightEngine.updateDevice(device.id) { it.copy(name = info.name, ledCount = info.ledCount) }
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Refresh, null)
                            Text(" From WLED")
                        }
                    }
                }
                if (device.kind == DeviceKind.WLED) {
                    Text(
                        "Address: ${device.host} · streamed with DDP at 60 fps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(shape = TouchShapes.control, onClick = { onOpenWled(device.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Language, null)
                        Text("  Open WLED settings")
                    }
                }
            }
        }
    }

    editingChild?.let { childId ->
        ChildEffectSheet(virtualId, childId, s.configs[childId] ?: emptyMap(), onDismiss = { editingChild = null })
    }

    if (showPicker) {
        EffectPickerSheet(current = info.id, matrix = virtual.rows > 1, onDismiss = { showPicker = false }) { picked ->
            LightEngine.updateSettings(virtualId) { it.copy(effectId = picked.id) }
            showPicker = false
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save preset") },
            text = {
                Column {
                    Text("Saves the current ${info.name} settings and colours.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true, label = { Text("Name") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    LightEngine.savePreset(name.trim(), virtual.settings); showSave = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${virtual.name}?") },
            text = {
                Text(
                    if (device != null) "Its effect settings will be lost, and it is taken out of any groups. You can add it again later."
                    else "The group is removed. Its devices keep their own settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (device != null) LightEngine.removeDevice(device.id) else LightEngine.removeVirtual(virtualId)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EffectPickerSheet(current: String, matrix: Boolean, onDismiss: () -> Unit, onPick: (EffectInfo) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Choose an effect", style = MaterialTheme.typography.titleLarge)
            val offered = Effects.all.filter { matrix || !it.is2d }
            Text(
                "${offered.size} effects · most come from LedFx",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EffectCategory.entries.forEach { cat ->
                val list = offered.filter { it.category == cat }
                if (list.isEmpty()) return@forEach
                SectionTitle(cat.title)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { e ->
                        FilterChip(
                            selected = e.id == current,
                            onClick = { onPick(e) },
                            label = { Text(e.name) },
                            leadingIcon = if (e.audioReactive) {
                                { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))
            Text(
                if (matrix) "More 2D effects from LedFx (Flame, Digital Rain, Noise…) are still to come."
                else "2D matrix effects appear here once this light has more than one row (Matrix, below the effect settings).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GradientDot(colors: List<Int>) {
    val c = colors.map { rgbColor(it) }
    Box(
        Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
            .background(if (c.size > 1) Brush.horizontalGradient(c) else Brush.horizontalGradient(listOf(c[0], c[0])))
    )
}

@Composable
private fun CustomGradientSwatch(colors: List<Int>) {
    val c = colors.map { rgbColor(it) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).padding(4.dp),
    ) {
        Box(
            Modifier.size(68.dp, 36.dp).clip(RoundedCornerShape(10.dp))
                .background(if (c.size > 1) Brush.horizontalGradient(c) else Brush.horizontalGradient(listOf(c[0], c[0])))
                .border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text("Preset", style = MaterialTheme.typography.labelSmall, color = Color.Unspecified)
    }
}

/**
 * Matrix layout of a light: rows, zig-zag wiring and rotation. With more than one row the light
 * is a matrix, and the 2D (Matrix) effects become available.
 */
@Composable
private fun MatrixSection(virtual: VirtualConfig) {
    val pixels = PixelMapper.effectPixelCount(virtual)
    SectionTitle("Matrix")
    var rowsText by remember(virtual.rows) { mutableStateOf(virtual.rows.toString()) }
    val rowsValue = rowsText.toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = rowsText,
            onValueChange = { t ->
                rowsText = t.filter { it.isDigit() }.take(4)
                rowsText.toIntOrNull()?.takeIf { it in 1..maxOf(1, pixels) }?.let { r ->
                    LightEngine.updateVirtual(virtual.id) { it.copy(rows = r) }
                }
            },
            label = { Text("Rows") },
            singleLine = true,
            isError = rowsValue == null || rowsValue !in 1..maxOf(1, pixels),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        Spacer(Modifier.width(12.dp))
        val w = pixels / virtual.rows.coerceAtLeast(1)
        val spare = pixels - w * virtual.rows
        Text(
            if (virtual.rows <= 1) "1 row = a strip"
            else "$w × ${virtual.rows} matrix" + if (spare > 0) " · $spare LEDs unused" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (spare > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (virtual.rows > 1) {
        if (virtual.isGroup && virtual.segments.size > 1 && virtual.mapping == Mapping.SPAN) {
            Text("Panels", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf(PanelLayout.SIDE_BY_SIDE to "Side by side", PanelLayout.STACKED to "Stacked").forEachIndexed { i, (layout, label) ->
                    SegmentedButton(
                        selected = virtual.panelLayout == layout,
                        onClick = { LightEngine.updateVirtual(virtual.id) { it.copy(panelLayout = layout) } },
                        shape = segmentShape(i, 2),
                    ) { Text(label) }
                }
            }
            Text(
                if (virtual.panelLayout == PanelLayout.SIDE_BY_SIDE)
                    "Each segment is a panel ${virtual.rows} rows tall, placed left to right in segment order. " +
                        "A reversed segment is a panel turned by 180°."
                else "The segments run on from one to the next, so the panels sit one above the other.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (PixelMapper.usesPanels(virtual) && virtual.segments.size > 1) "Zig-zag wiring in each panel (serpentine)"
                    else "Zig-zag wiring (serpentine)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Every second row runs backwards. If WLED itself is set up as a 2D matrix it may " +
                        "already handle this; leave it off then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = virtual.serpentine,
                onCheckedChange = { on -> LightEngine.updateVirtual(virtual.id) { it.copy(serpentine = on) } },
            )
        }
        Text("Rotate the matrix", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("0°", "90°", "180°", "270°").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = virtual.rotate == i,
                    onClick = { LightEngine.updateVirtual(virtual.id) { it.copy(rotate = i) } },
                    shape = segmentShape(i, 4),
                ) { Text(label) }
            }
        }
        Text(
            "Tip: pick the Matrix test effect. Red should start top-left and blue top-right.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Settings of an effect that another effect runs (Effect Chase, Blend). They are the light's own
 * saved settings for that effect, so they also apply when it is picked directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildEffectSheet(virtualId: String, childId: String, config: Map<String, Any>, onDismiss: () -> Unit) {
    val info = Effects.info(childId)
    // Fresh holder per change (strong skipping compares by instance).
    val values = remember(childId, config) { ParamValues(info.params, config) }
    fun set(key: String, v: Any) = LightEngine.updateSettings(virtualId) {
        it.copy(configs = it.configs + (childId to ((it.configs[childId] ?: emptyMap()) + (key to v))))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${info.name} settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    LightEngine.updateSettings(virtualId) { it.copy(configs = it.configs - childId) }
                }) { Text("Reset") }
            }
            // Layout settings (blur, mirror, flip, roll) only apply to the light's main effect.
            info.params.filter { it.key !in LAYOUT_KEYS && it.key != "gradient_roll" }.forEach { param ->
                ParamControl(param, values) { v -> set(param.key, v) }
            }
        }
    }
}
