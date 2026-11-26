package app.touchfx.ui

import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.touchfx.engine.PlaylistLogic
import app.touchfx.engine.ViewFilters
import app.touchfx.engine.PlaylistState
import app.touchfx.model.SceneKind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Switch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.touchfx.effects.Palettes
import app.touchfx.engine.LightEngine
import app.touchfx.engine.SceneLogic
import app.touchfx.model.VirtualConfig
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction

/** Scenes tab: tap a scene to recall the saved look of several lights at once. */
@Composable
fun ScenesScreen(padding: PaddingValues) {
    val scenes by LightEngine.scenes.collectAsStateWithLifecycle()
    val lights by LightEngine.virtuals.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Scene?>(null) }
    var updating by remember { mutableStateOf<Scene?>(null) }
    var deleting by remember { mutableStateOf<Scene?>(null) }
    // Playlist editor: null = closed; a playlist scene to edit, or NEW_PLAYLIST.
    var editingPlaylist by remember { mutableStateOf<Scene?>(null) }
    var showPad by remember { mutableStateOf(false) }
    val running by LightEngine.playlist.collectAsStateWithLifecycle()
    val filter = rememberFilter("filter.scenes", SceneKind.entries)
    val shown = ViewFilters.scenes(scenes, filter.value)
    // Never leave a held scene on if this screen goes away mid-press.
    DisposableEffect(Unit) { onDispose { LightEngine.releaseHeldScene() } }

    if (scenes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No scenes yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Set up your lights the way you like, then tap Save scene. " +
                        "A scene recalls the effect, colours and brightness of several lights in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showPad = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Rounded.OpenWith, null, Modifier.size(18.dp))
                    Text(" Open the XY pad")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tap to switch or play · hold-to-play scenes run while pressed · ⋮ for more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPad = true }) {
                        Icon(Icons.Rounded.OpenWith, null, Modifier.size(18.dp))
                        Text(" XY pad")
                    }
                    TextButton(
                        enabled = scenes.any { it.kind == SceneKind.NORMAL },
                        onClick = { editingPlaylist = NEW_PLAYLIST },
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text(" Playlist")
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterRow(
                    options = listOf(SceneKind.NORMAL to "Normal", SceneKind.HOLD to "Hold to play", SceneKind.PLAYLIST to "Playlists"),
                    counts = scenes.groupingBy { it.kind }.eachCount(),
                    selected = filter.value,
                    onChange = { filter.set(it) },
                )
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Nothing to show with this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.id }) { scene ->
                if (scene.kind == SceneKind.PLAYLIST) {
                    PlaylistCard(
                        scene = scene,
                        scenes = scenes,
                        lights = lights,
                        state = running?.takeIf { it.playlistId == scene.id },
                        onEdit = { editingPlaylist = scene },
                        onRename = { renaming = scene },
                        onDelete = { deleting = scene },
                    )
                    return@items
                }
                SceneCard(
                    scene = scene,
                    lights = lights,
                    active = SceneLogic.matches(scene, lights),
                    onActivate = { LightEngine.activateScene(scene.id) },
                    onHold = { LightEngine.holdScene(scene.id) },
                    onRelease = { LightEngine.releaseHeldScene() },
                    onToggleMomentary = { LightEngine.setSceneMomentary(scene.id, !scene.momentary) },
                    onRename = { renaming = scene },
                    onUpdate = { updating = scene },
                    onDelete = { deleting = scene },
                )
            }
        }
    }

    renaming?.let { scene ->
        SceneDialog(
            title = "Rename scene",
            confirmLabel = "Rename",
            initialName = scene.name,
            lights = null,
            initialSelected = emptySet(),
            initialMomentary = null,
            onDismiss = { renaming = null },
            onConfirm = { name, _, _ -> LightEngine.renameScene(scene.id, name); renaming = null },
        )
    }
    updating?.let { scene ->
        SceneDialog(
            title = "Update from current lights",
            confirmLabel = "Update",
            initialName = scene.name,
            lights = lights,
            initialSelected = scene.entries.filterValues { it.action != SceneAction.IGNORE }.keys,
            initialMomentary = scene.momentary,
            onDismiss = { updating = null },
            onConfirm = { name, ids, hold -> LightEngine.updateSceneFromCurrent(scene.id, name, ids, hold); updating = null },
        )
    }
    if (showPad) XyPadScreen(onDismiss = { showPad = false })
    editingPlaylist?.let { pl ->
        PlaylistEditor(existing = pl.takeIf { it !== NEW_PLAYLIST }, scenes = scenes, onDismiss = { editingPlaylist = null })
    }
    deleting?.let { scene ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${scene.name}?") },
            text = { Text("The lights keep their current look.") },
            confirmButton = {
                TextButton(onClick = { LightEngine.deleteScene(scene.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** "Save scene" dialog shown from the Scenes tab's button. */
@Composable
fun SaveSceneDialog(onDismiss: () -> Unit) {
    val lights by LightEngine.virtuals.collectAsStateWithLifecycle()
    val count = LightEngine.scenes.value.size
    SceneDialog(
        title = "Save scene",
        confirmLabel = "Save",
        initialName = "Scene ${count + 1}",
        lights = lights,
        initialSelected = lights.map { it.id }.toSet(),
        initialMomentary = false,
        onDismiss = onDismiss,
        onConfirm = { name, ids, hold -> LightEngine.saveScene(name, ids, hold); onDismiss() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneCard(
    scene: Scene,
    lights: List<VirtualConfig>,
    active: Boolean,
    onActivate: () -> Unit,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    onToggleMomentary: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val currentHold by rememberUpdatedState(onHold)
    val currentRelease by rememberUpdatedState(onRelease)
    val existing = lights.mapTo(HashSet()) { it.id }
    val entries = scene.entries.filterKeys { it in existing }
    val on = entries.values.filter { it.action == SceneAction.ACTIVATE }
    val off = entries.values.count { it.action == SceneAction.STOP }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (scene.momentary) {
                    // Hold to play: on while pressed, previous look back on release.
                    Modifier.pointerInput(scene.id) {
                        detectTapGestures(onPress = {
                            pressed = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentHold()
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                                currentRelease()
                            }
                        })
                    }
                } else {
                    Modifier.combinedClickable(onClick = onActivate, onLongClick = { menu = true })
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                pressed -> MaterialTheme.colorScheme.tertiaryContainer
                active && !scene.momentary -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scene.momentary) {
                    Icon(
                        Icons.Rounded.TouchApp, "Hold to play",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                } else if (active) {
                    Icon(
                        Icons.Rounded.CheckCircle, "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(end = 2.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    scene.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (scene.momentary) "Make it tap to switch" else "Make it hold to play") },
                            onClick = { menu = false; onToggleMomentary() },
                        )
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Update from current lights") }, onClick = { menu = false; onUpdate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // One colour bar per light the scene turns on.
                on.take(4).forEach { e ->
                    val colors = Palettes.resolve(e.paletteId ?: "rainbow", e.customGradient).previewColors.map { rgbColor(it) }
                    val brush = Brush.horizontalGradient(if (colors.size == 1) listOf(colors[0], colors[0]) else colors)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
                if (on.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    if (scene.momentary) append("Hold to play · ")
                    append(if (on.size == 1) "1 light" else "${on.size} lights")
                    if (off > 0) append(" · $off off")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Marker for "create a new playlist" in the editor state. */
private val NEW_PLAYLIST = Scene("new", "", emptyMap())

/**
 * A playlist: tap to play or stop. While playing it shows the current scene and the time left
 * (or "paused" while a hold-to-play scene is pressed), with Skip and Stop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    scene: Scene,
    scenes: List<Scene>,
    lights: List<VirtualConfig>,
    state: PlaylistState?,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val list = scene.playlist ?: return
    val byId = scenes.associateBy { it.id }
    val playable = PlaylistLogic.playable(scene, scenes)
    // Re-read the clock a few times a second for the countdown.
    var now by remember { mutableStateOf(LightEngine.clockMs()) }
    if (state != null) {
        LaunchedEffect(state) { while (true) { now = LightEngine.clockMs(); delay(250) } }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (state != null) LightEngine.stopPlaylist() else LightEngine.startPlaylist(scene.id) },
                onLongClick = { menu = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (state != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic, "Playlist",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(scene.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (state != null) {
                val current = byId[list.items[state.itemIndex].sceneId]
                Text(
                    "▶ ${current?.name ?: "?"}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.paused) "paused (hold)" else formatSeconds((PlaylistLogic.remaining(state, now) + 999) / 1000) + " left",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { LightEngine.skipInPlaylist() }) { Icon(Icons.Rounded.SkipNext, "Next scene") }
                    IconButton(onClick = { LightEngine.stopPlaylist() }) { Icon(Icons.Rounded.Stop, "Stop playlist") }
                }
            } else {
                Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // A colour bar per scene (first light it turns on), up to 4.
                    playable.take(4).forEach { i ->
                        val e = byId[list.items[i].sceneId]?.entries?.values?.firstOrNull { it.action == SceneAction.ACTIVATE }
                        val colors = Palettes.resolve(e?.paletteId ?: "rainbow", e?.customGradient).previewColors.map { rgbColor(it) }
                        val brush = Brush.horizontalGradient(if (colors.size == 1) listOf(colors[0], colors[0]) else colors)
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val total = playable.sumOf { list.items[it].seconds.toLong() }
                Text(
                    (if (playable.size == 1) "1 scene" else "${playable.size} scenes") +
                        " · ${formatSeconds(total)} · " + (if (list.shuffle) "random" else "in order"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Name field plus, when [lights] is given, a checkbox per light. Unticked lights are saved as
 * "ignore", so the scene leaves them alone.
 */
@Composable
private fun SceneDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    lights: List<VirtualConfig>?,
    initialSelected: Set<String>,
    /** null hides the hold-to-play switch (rename). */
    initialMomentary: Boolean?,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selected by remember { mutableStateOf(initialSelected) }
    var momentary by remember { mutableStateOf(initialMomentary ?: false) }
    val canSave = name.isNotBlank() && (lights == null || selected.any { id -> lights.any { it.id == id } })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    label = { Text("Name") },
                )
                if (initialMomentary != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Hold to play", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Plays only while you press it; letting go brings back the previous look. Good for strobes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = momentary, onCheckedChange = { momentary = it })
                    }
                }
                if (lights != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Lights in this scene (their current look is saved):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lights.forEach { d ->
                        val checked = d.id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - d.id else selected + d.id },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(d.name + if (!d.enabled) " (off)" else "")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onConfirm(name.trim(), selected, momentary) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
