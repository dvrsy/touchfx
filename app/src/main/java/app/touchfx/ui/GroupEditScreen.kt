package app.touchfx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.touchfx.engine.LightEngine
import app.touchfx.engine.PixelMapper
import app.touchfx.engine.VirtualLogic
import app.touchfx.model.DeviceConfig
import app.touchfx.model.Mapping
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

private const val MAX_GROUPING = 32

/**
 * Creates or edits a group: a light made of segments of one or more devices.
 * [virtualId] null = new group. [onSaved] gets the group's id.
 * LED numbers are shown 1-based and inclusive ("LED 1 to 60"); stored 0-based like LedFx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(virtualId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val devices by LightEngine.devices.collectAsStateWithLifecycle()
    val virtuals by LightEngine.virtuals.collectAsStateWithLifecycle()
    val running by LightEngine.running.collectAsStateWithLifecycle()
    val existing = virtualId?.let { id -> virtuals.firstOrNull { it.id == id && it.isGroup } }

    var name by remember { mutableStateOf(existing?.name ?: "Group ${virtuals.count { it.isGroup } + 1}") }
    var mapping by remember { mutableStateOf(existing?.mapping ?: Mapping.SPAN) }
    var grouping by remember { mutableIntStateOf(existing?.grouping ?: 1) }
    var segments by remember {
        mutableStateOf(existing?.segments ?: devices.take(1).map { Segment(it.id, 0, it.ledCount - 1) })
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Light up the selected segment on the real LEDs while editing.
    LaunchedEffect(selected, segments) {
        val seg = selected?.let { segments.getOrNull(it) }
        LightEngine.setHighlight(seg?.let { LightEngine.Highlight(it.deviceId, it.start, it.end) })
    }
    DisposableEffect(Unit) { onDispose { LightEngine.setHighlight(null) } }
    BackHandler(onBack = onBack)

    val draft = VirtualConfig(
        id = existing?.id ?: "draft",
        name = name, segments = segments, mapping = mapping, grouping = grouping,
    )
    val byId = devices.associateBy { it.id }
    val segmentsValid = segments.isNotEmpty() && segments.all { s ->
        val d = byId[s.deviceId]
        d != null && s.start in 0..s.end && s.end < d.ledCount
    }
    val canSave = name.isNotBlank() && segmentsValid
    val switchedOff = VirtualLogic.conflicts(draft, virtuals.filter { it.id != draft.id && it.enabled })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New group" else "Edit group") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back without saving") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete group") }
                    }
                    TextButton(enabled = canSave, onClick = {
                        if (existing == null) {
                            val v = VirtualConfig(name = name.trim(), segments = segments, mapping = mapping, grouping = grouping)
                            LightEngine.addVirtual(v)
                            onSaved(v.id)
                        } else {
                            LightEngine.updateVirtual(existing.id) {
                                it.copy(name = name.trim(), segments = segments, mapping = mapping, grouping = grouping)
                            }
                            // New segments may now clash with other lights.
                            if (existing.enabled) LightEngine.setEnabled(existing.id, true)
                            onSaved(existing.id)
                        }
                    }) { Text("Save") }
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
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(32) },
                singleLine = true, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("How the effect is laid out")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mapping == Mapping.SPAN, onClick = { mapping = Mapping.SPAN },
                    shape = segmentShape(0, 2),
                ) { Text("Span") }
                SegmentedButton(
                    selected = mapping == Mapping.COPY, onClick = { mapping = Mapping.COPY },
                    shape = segmentShape(1, 2),
                ) { Text("Copy") }
            }
            Hint(
                if (mapping == Mapping.SPAN) "Span: one effect runs across all segments, in the order below."
                else "Copy: every segment shows the whole effect."
            )

            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LEDs per effect pixel", style = MaterialTheme.typography.bodyMedium)
                    Hint("Drive several LEDs as one pixel, for chunkier effects on dense strips.")
                }
                IconButton(enabled = grouping > 1, onClick = { grouping-- }) { Icon(Icons.Rounded.Remove, "Fewer") }
                Text("$grouping", style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = grouping < MAX_GROUPING, onClick = { grouping++ }) { Icon(Icons.Rounded.Add, "More") }
            }

            val total = PixelMapper.pixelCount(draft)
            val effectPixels = PixelMapper.effectPixelCount(draft)
            Text(
                "$total LEDs" + if (grouping > 1) " · $effectPixels effect pixels" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle("Segments")
            Hint(
                if (running) "Tap a segment to light it up white on your LEDs."
                else "Start the lights to see the selected segment light up on your LEDs."
            )
            if (devices.isEmpty()) {
                Text(
                    "Add a device on the Devices tab first.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            segments.forEachIndexed { i, seg ->
                SegmentCard(
                    index = i,
                    count = segments.size,
                    segment = seg,
                    devices = devices,
                    selected = selected == i,
                    onSelect = { selected = if (selected == i) null else i },
                    onChange = { new -> segments = segments.toMutableList().also { it[i] = new } },
                    onMove = { dir ->
                        val j = i + dir
                        segments = segments.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
                        if (selected == i) selected = j
                    },
                    onDelete = {
                        segments = segments.toMutableList().also { it.removeAt(i) }
                        selected = null
                    },
                )
            }

            val overlaps = VirtualLogic.selfOverlaps(draft)
            if (overlaps.isNotEmpty()) {
                Text(
                    "Segments " + overlaps.joinToString { (a, b) -> "${a + 1} and ${b + 1}" } +
                        " use the same LEDs; the later one wins there.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            FilledTonalButton(shape = TouchShapes.control, 
                enabled = devices.isNotEmpty(),
                onClick = {
                    // Default: the next device not used yet, else the first one.
                    val used = segments.mapTo(HashSet()) { it.deviceId }
                    val d = devices.firstOrNull { it.id !in used } ?: devices.first()
                    segments = segments + Segment(d.id, 0, d.ledCount - 1)
                    selected = segments.size - 1
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Text("  Add segment")
            }

            if (switchedOff.isNotEmpty()) {
                Hint(
                    (if (existing?.enabled == false) "Turning this group on" else "Saving") +
                        " switches off: " + switchedOff.joinToString { it.name } +
                        ". A device shows one light at a time, as in LedFx."
                )
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("The group is removed. Its devices keep their own settings.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; LightEngine.removeVirtual(existing.id); onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentCard(
    index: Int,
    count: Int,
    segment: Segment,
    devices: List<DeviceConfig>,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Segment) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val device = devices.firstOrNull { it.id == segment.deviceId }
    val leds = device?.ledCount ?: 1
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(shape = TouchShapes.control, onClick = { menu = true }) {
                        Text(device?.name ?: "Choose device", maxLines = 1)
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        devices.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.name} · ${d.ledCount} LEDs") },
                                onClick = {
                                    menu = false
                                    if (d.id != segment.deviceId) onChange(Segment(d.id, 0, d.ledCount - 1, segment.invert))
                                },
                            )
                        }
                    }
                }
                IconButton(enabled = index > 0, onClick = { onMove(-1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton(enabled = index < count - 1, onClick = { onMove(1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove segment") }
            }

            if (device != null) {
                Column(Modifier.padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LedNumberField("From LED", segment.start + 1, 1..(segment.end + 1), Modifier.weight(1f)) { v ->
                            onChange(segment.copy(start = v - 1))
                        }
                        Spacer(Modifier.width(8.dp))
                        LedNumberField("To LED", segment.end + 1, (segment.start + 1)..leds, Modifier.weight(1f)) { v ->
                            onChange(segment.copy(end = v - 1))
                        }
                    }
                    if (leds > 1) {
                        RangeSlider(
                            value = (segment.start + 1).toFloat()..(segment.end + 1).toFloat(),
                            onValueChange = { r ->
                                val a = r.start.toInt().coerceIn(1, leds)
                                val b = r.endInclusive.toInt().coerceIn(a, leds)
                                if (a - 1 != segment.start || b - 1 != segment.end) onChange(segment.copy(start = a - 1, end = b - 1))
                            },
                            valueRange = 1f..leds.toFloat(),
                        )
                    }
                    SegmentBar(segment, leds)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${segment.length} LEDs" + if (segment.invert) " · reversed" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Reverse", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = segment.invert, onCheckedChange = { onChange(segment.copy(invert = it)) })
                    }
                }
            } else {
                Text("This device was removed.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Number field that only reports values inside [range]; out-of-range input is shown as an error. */
@Composable
private fun LedNumberField(label: String, value: Int, range: IntRange, modifier: Modifier, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        isError = n == null || n !in range,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** The whole device as a bar, with the segment's range coloured. */
@Composable
private fun SegmentBar(segment: Segment, leds: Int) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val x0 = size.width * segment.start / leds
        val x1 = size.width * (segment.end + 1) / leds
        drawRoundRect(fill, topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(2f), size.height), cornerRadius = r)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
