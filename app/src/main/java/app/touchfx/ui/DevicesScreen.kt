package app.touchfx.ui

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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.touchfx.engine.LightEngine
import app.touchfx.model.DeviceConfig
import app.touchfx.model.DeviceKind
import app.touchfx.model.MAX_LEDS
import app.touchfx.model.VirtualConfig
import app.touchfx.net.WledApi
import app.touchfx.net.WledDiscovery

/** Devices tab: the physical outputs (WLED controllers and preview strips). */
@Composable
fun DevicesScreen(padding: PaddingValues, onOpenWled: (String) -> Unit) {
    val devices by LightEngine.devices.collectAsStateWithLifecycle()
    val virtuals by LightEngine.virtuals.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DeviceConfig?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "The LED outputs. Pick effects on the Lights tab; combine devices there with \"New group\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    "No devices yet. Tap \"Add device\" to find WLED lights on your Wi-Fi, " +
                        "or add a preview strip to try effects on the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
        items(devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                virtuals = virtuals,
                onEdit = { editing = device },
                onOpenWled = { onOpenWled(device.id) },
            )
        }
    }

    editing?.let { d -> DeviceEditDialog(d, onDismiss = { editing = null }) }
}

@Composable
private fun DeviceCard(device: DeviceConfig, virtuals: List<VirtualConfig>, onEdit: () -> Unit, onOpenWled: () -> Unit) {
    val users = virtuals.filter { v -> v.segments.any { it.deviceId == device.id } }
    val active = users.filter { it.enabled }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (device.kind == DeviceKind.WLED) Icons.Rounded.Router else Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (device.kind == DeviceKind.WLED) "WLED · ${device.host} · ${device.ledCount} LEDs"
                    else "Preview strip · ${device.ledCount} LEDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        active.isEmpty() -> "Idle"
                        active.size == 1 && active[0].isDevice == device.id -> "Showing its own light"
                        else -> "Driven by " + active.joinToString { it.name }
                    } + if (users.any { it.isGroup }) " · in ${users.count { it.isGroup }} group(s)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            if (device.kind == DeviceKind.WLED) {
                IconButton(onClick = onOpenWled) { Icon(Icons.Rounded.Language, "Open WLED") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit device") }
        }
    }
}

/** Name, LED count and removal of a device. */
@Composable
private fun DeviceEditDialog(device: DeviceConfig, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(device.name) }
    var count by remember { mutableStateOf(device.ledCount.toString()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val n = count.toIntOrNull()
    val valid = name.isNotBlank() && n != null && n in 1..MAX_LEDS

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${device.name}?") },
            text = { Text("Its own light and effect settings are lost, and it is taken out of any groups.") },
            confirmButton = {
                TextButton(onClick = { LightEngine.removeDevice(device.id); onDismiss() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit device") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(32) },
                    singleLine = true, label = { Text("Name") },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { t -> count = t.filter { it.isDigit() }.take(4) },
                        label = { Text("Number of LEDs") },
                        singleLine = true,
                        isError = n == null || n !in 1..MAX_LEDS,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (device.kind == DeviceKind.WLED) {
                        IconButton(onClick = {
                            scope.launch {
                                WledApi.fetchInfo(device.host)?.let { info ->
                                    name = info.name; count = info.ledCount.toString()
                                }
                            }
                        }) { Icon(Icons.Rounded.Refresh, "Read from WLED") }
                    }
                }
                if (device.kind == DeviceKind.WLED) {
                    Text(
                        "Address: ${device.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Remove device", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                LightEngine.updateDevice(device.id) { it.copy(name = name.trim(), ledCount = n!!) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val discovery = remember { WledDiscovery(context) }
    val found by discovery.found.collectAsStateWithLifecycle()
    val scanning by discovery.scanning.collectAsStateWithLifecycle()
    val devices by LightEngine.devices.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var manualHost by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(true)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Add a device", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("WLED on your Wi-Fi", style = MaterialTheme.typography.titleSmall)
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            if (found.isEmpty()) {
                Text(
                    if (scanning) "Searching…" else "None found yet. Make sure the phone and the lights are on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            found.forEach { info ->
                val added = devices.any { it.host == info.host }
                ListItem(
                    headlineContent = { Text(info.name) },
                    supportingContent = { Text("${info.host} · ${info.ledCount} LEDs") },
                    leadingContent = { Icon(Icons.Rounded.Router, null) },
                    trailingContent = {
                        TextButton(enabled = !added, onClick = {
                            LightEngine.addDevice(
                                DeviceConfig(name = info.name, kind = DeviceKind.WLED, host = info.host, ledCount = info.ledCount)
                            )
                            onDismiss()
                        }) { Text(if (added) "Added" else "Add") }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Add by IP address", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it.trim(); manualError = null },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    isError = manualError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(shape = TouchShapes.control, 
                    enabled = manualHost.isNotBlank() && !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            val info = WledApi.fetchInfo(manualHost)
                            checking = false
                            if (info == null) {
                                manualError = "No WLED device answered at that address"
                            } else {
                                LightEngine.addDevice(
                                    DeviceConfig(name = info.name, kind = DeviceKind.WLED, host = info.host, ledCount = info.ledCount)
                                )
                                onDismiss()
                            }
                        }
                    },
                ) { Text(if (checking) "…" else "Connect") }
            }
            manualError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedButton(shape = TouchShapes.control, 
                onClick = {
                    LightEngine.addDevice(DeviceConfig(name = "Preview strip", kind = DeviceKind.PREVIEW, ledCount = 60))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PhoneAndroid, null)
                Text("  Add a preview strip (shown on the phone)")
            }
        }
    }
}
