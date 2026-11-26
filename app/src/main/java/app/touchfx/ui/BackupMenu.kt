package app.touchfx.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import app.touchfx.engine.AppState
import app.touchfx.engine.Backup
import app.touchfx.engine.LightEngine
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Top-bar menu to back up everything (devices, lights, groups, scenes, own presets, settings)
 * to a JSON file and to restore it. Uses the system file picker, so no storage permission is
 * needed and the file can go to Downloads, a cloud drive, etc. A restore is checked and shown
 * for confirmation before anything is replaced.
 */
@Composable
fun BackupMenu() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<AppState, String>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = LightEngine.exportBackup(appVersion(context), LocalDateTime.now().withNano(0).toString())
            val ok = withContext(Dispatchers.IO) { runCatching { write(context, uri, text) }.isSuccess }
            toast(context, if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = read(context, uri)
                    val state = Backup.decode(text)
                    val date = runCatching { JSONObject(text).optString("exported_at") }.getOrDefault("")
                    state to date
                }
            }
            result.fold(
                onSuccess = { pending = it },
                onFailure = { toast(context, (it as? IllegalArgumentException)?.message ?: "Couldn't read the file") },
            )
        }
    }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Back up to file…") },
                onClick = { open = false; exportLauncher.launch("touchfx-backup-${LocalDate.now()}.json") },
            )
            DropdownMenuItem(
                text = { Text("Restore from file…") },
                // Some file managers label .json files as text or as plain binary.
                onClick = { open = false; importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            )
        }
    }

    pending?.let { (state, date) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "Backup" + (if (date.isNotBlank()) " from ${date.replace('T', ' ')}" else "") +
                        ": ${Backup.summary(state)}, plus audio and other settings.\n\n" +
                        "This replaces all current devices, lights, groups, scenes, your presets and settings. " +
                        "It can't be undone; back up the current setup first if you want to keep it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LightEngine.restore(state)
                    pending = null
                    toast(context, "Backup restored")
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun write(context: Context, uri: Uri, text: String) {
    // "wt": truncate, so overwriting an existing file leaves no old bytes at the end.
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

private fun read(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)!!.use { input ->
        // Backups are small; refuse anything absurdly large rather than filling memory.
        val bytes = input.readBytes()
        require(bytes.size < 20_000_000) { "This file is too large to be a backup." }
        String(bytes, Charsets.UTF_8)
    }

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
