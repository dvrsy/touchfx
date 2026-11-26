package app.touchfx.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.touchfx.engine.ViewFilters

/** A filter selection that saves itself whenever it changes. */
class FilterSelection<E>(private val state: MutableState<Set<E>>, private val save: (Set<E>) -> Unit) {
    val value: Set<E> get() = state.value
    fun set(v: Set<E>) { state.value = v; save(v) }
}

/**
 * A filter selection of enum values, remembered across app restarts (UI preference, not part of
 * backups). Unknown saved names are ignored; an empty result falls back to everything.
 */
@Composable
fun <E : Enum<E>> rememberFilter(key: String, values: List<E>): FilterSelection<E> {
    val prefs = LocalContext.current.getSharedPreferences("ui", Context.MODE_PRIVATE)
    return remember(key) {
        val saved = prefs.getStringSet(key, null)?.mapNotNull { name -> values.firstOrNull { it.name == name } }?.toSet()
        FilterSelection(mutableStateOf(saved?.takeIf { it.isNotEmpty() } ?: values.toSet())) { v ->
            prefs.edit().putStringSet(key, v.mapTo(HashSet()) { it.name }).apply()
        }
    }
}

/** A row of chips that switch kinds on and off; each shows how many items it covers. */
@Composable
fun <E> FilterRow(options: List<Pair<E, String>>, counts: Map<E, Int>, selected: Set<E>, onChange: (Set<E>) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value in selected
            FilterChip(
                selected = on,
                onClick = { onChange(ViewFilters.toggle(selected, value)) },
                label = { Text("$label (${counts[value] ?: 0})") },
                leadingIcon = if (on) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
            )
        }
    }
}
