package app.touchfx.engine

import app.touchfx.model.Scene
import app.touchfx.model.SceneKind
import app.touchfx.model.VirtualConfig

/** Which lights the Lights tab shows: a device's own light, groups, or both. */
enum class LightKind { LIGHT, GROUP }

/** Filters for the Lights and Scenes tabs; free of Android so they can be unit tested. */
object ViewFilters {
    fun kindOf(v: VirtualConfig) = if (v.isGroup) LightKind.GROUP else LightKind.LIGHT

    fun lights(all: List<VirtualConfig>, show: Set<LightKind>): List<VirtualConfig> = all.filter { kindOf(it) in show }

    fun scenes(all: List<Scene>, show: Set<SceneKind>): List<Scene> = all.filter { it.kind in show }

    /**
     * Toggles [item] in [selected]. The last selected item can't be switched off, so a filter
     * never hides everything by accident.
     */
    fun <T> toggle(selected: Set<T>, item: T): Set<T> = when {
        item !in selected -> selected + item
        selected.size > 1 -> selected - item
        else -> selected
    }
}
