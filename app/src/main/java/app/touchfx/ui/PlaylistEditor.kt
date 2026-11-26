package app.touchfx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.touchfx.engine.LightEngine
import app.touchfx.model.Playlist
import app.touchfx.model.PlaylistItem
import app.touchfx.model.Scene
import app.touchfx.model.SceneKind

private const val DEFAULT_SECONDS = 30

/** "1:05" for 65 seconds. */
fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Creates ([existing] null) or edits a playlist: a name, in order or random, and a list of
 * normal scenes, each with how many seconds it runs before the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditor(existing: Scene?, scenes: List<Scene>, onDismiss: () -> Unit) {
    val normal = scenes.filter { it.kind == SceneKind.NORMAL }
    val byId = scenes.associateBy { it.id }
    var name by remember { mutableStateOf(existing?.name ?: "Playlist ${scenes.count { it.kind == SceneKind.PLAYLIST } + 1}") }
    var shuffle by remember { mutableStateOf(existing?.playlist?.shuffle ?: false) }
    var items by remember { mutableStateOf(existing?.playlist?.items ?: emptyList()) }
    fun playable(item: PlaylistItem) = byId[item.sceneId]?.kind == SceneKind.NORMAL
    val canSave = name.isNotBlank() && items.any { playable(it) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (existing == null) "New playlist" else "Edit playlist") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close without saving") } },
                    actions = {
                        TextButton(enabled = canSave, onClick = {
                            // Scenes deleted meanwhile are dropped.
                            LightEngine.savePlaylist(existing?.id, name.trim(), Playlist(items.filter { playable(it) }, shuffle))
                            onDismiss()
                        }) { Text("Save") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(32) },
                    singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                )
                SectionTitle("Order")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !shuffle, onClick = { shuffle = false }, shape = segmentShape(0, 2)) { Text("In order") }
                    SegmentedButton(selected = shuffle, onClick = { shuffle = true }, shape = segmentShape(1, 2)) { Text("Random") }
                }
                Text(
                    if (shuffle) "Each round plays every scene once, shuffled; the same scene never plays twice in a row."
                    else "Plays the scenes top to bottom, then starts again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                SectionTitle("Scenes")
                Text(
                    "Each scene runs for its time, then the next one starts. A hold-to-play scene pressed " +
                        "meanwhile pauses the playlist, which carries on where it was when you let go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                items.forEachIndexed { i, item ->
                    PlaylistItemRow(
                        index = i, count = items.size, item = item, scene = byId[item.sceneId], choices = normal,
                        onChange = { new -> items = items.toMutableList().also { it[i] = new } },
                        onMove = { d -> items = items.toMutableList().also { val t = it[i]; it[i] = it[i + d]; it[i + d] = t } },
                        onDelete = { items = items.toMutableList().also { it.removeAt(i) } },
                    )
                }

                var addMenu by remember { mutableStateOf(false) }
                Box(Modifier.padding(top = 12.dp)) {
                    FilledTonalButton(shape = TouchShapes.control, enabled = normal.isNotEmpty(), onClick = { addMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null)
                        Text("  Add scene")
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        normal.forEach { sc ->
                            DropdownMenuItem(text = { Text(sc.name) }, onClick = {
                                addMenu = false
                                items = items + PlaylistItem(sc.id, items.lastOrNull()?.seconds ?: DEFAULT_SECONDS)
                            })
                        }
                    }
                }
                if (normal.isEmpty()) {
                    Text(
                        "Save some scenes first (hold-to-play scenes and playlists can't be added).",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                val total = items.filter { playable(it) }.sumOf { it.seconds.toLong() }
                if (total > 0) {
                    Text(
                        "One round: ${formatSeconds(total)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    index: Int, count: Int, item: PlaylistItem, scene: Scene?, choices: List<Scene>,
    onChange: (PlaylistItem) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val ok = scene?.kind == SceneKind.NORMAL
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                OutlinedButton(shape = TouchShapes.control, onClick = { menu = true }) {
                    Text(
                        if (ok) scene!!.name else "(scene deleted)",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    choices.forEach { sc ->
                        DropdownMenuItem(text = { Text(sc.name) }, onClick = { menu = false; onChange(item.copy(sceneId = sc.id)) })
                    }
                }
            }
            SecondsField(item.seconds) { onChange(item.copy(seconds = it)) }
            Column {
                IconButton(enabled = index > 0, onClick = { onMove(-1) }, modifier = Modifier.padding(0.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton(enabled = index < count - 1, onClick = { onMove(1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove from playlist") }
        }
    }
}

/** Seconds a scene runs (1 to 3600). */
@Composable
private fun SecondsField(seconds: Int, onValue: (Int) -> Unit) {
    var text by remember(seconds) { mutableStateOf(seconds.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.takeIf { it in 1..PlaylistItem.MAX_SECONDS }?.let(onValue)
        },
        label = { Text("Sec.") },
        singleLine = true,
        isError = n == null || n !in 1..PlaylistItem.MAX_SECONDS,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(84.dp),
    )
}
