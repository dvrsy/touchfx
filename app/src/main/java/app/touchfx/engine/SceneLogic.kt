package app.touchfx.engine

import app.touchfx.model.VirtualConfig
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import kotlin.math.abs

/** Scene rules, kept free of Android so they can be unit tested. */
object SceneLogic {

    /**
     * Captures the current look of [lights] (virtuals) as a scene. Lights in [included] are saved as
     * ACTIVATE (or STOP if they are switched off); every other light is saved as IGNORE.
     */
    fun capture(id: String, name: String, lights: List<VirtualConfig>, included: Set<String>): Scene {
        val entries = LinkedHashMap<String, SceneEntry>()
        for (d in lights) {
            entries[d.id] = when {
                d.id !in included -> SceneEntry(SceneAction.IGNORE)
                !d.enabled -> SceneEntry(SceneAction.STOP)
                else -> {
                    val s = d.settings
                    SceneEntry(
                        action = SceneAction.ACTIVATE,
                        effectId = s.effectId,
                        paletteId = s.paletteId,
                        customGradient = s.customGradient,
                        brightness = s.brightness,
                        config = s.config(),
                    )
                }
            }
        }
        return Scene(id, name, entries)
    }

    /**
     * Returns [lights] with [scene] applied. Entries for lights that no longer exist are skipped.
     *
     * Lights the scene turns on switch off whatever conflicts with them, as turning a light on by
     * hand does ([VirtualLogic.enableExclusive]). That includes lights the scene doesn't know,
     * such as groups made after it was saved, which would otherwise draw over its lights. If two
     * of the scene's own lights now conflict (e.g. a group's segments were changed), they are
     * turned on in render order, so the later one (usually the group) wins.
     */
    fun apply(scene: Scene, lights: List<VirtualConfig>): List<VirtualConfig> {
        val looked = lights.map { d ->
            val e = scene.entries[d.id] ?: return@map d
            when (e.action) {
                SceneAction.IGNORE -> d
                SceneAction.STOP -> d.copy(enabled = false)
                SceneAction.ACTIVATE -> {
                    val s = d.settings
                    val effect = e.effectId ?: s.effectId
                    d.copy(
                        enabled = true,
                        settings = s.copy(
                            effectId = effect,
                            paletteId = e.paletteId ?: s.paletteId,
                            customGradient = e.customGradient,
                            brightness = e.brightness ?: s.brightness,
                            // Replace only this effect's settings; other effects keep theirs.
                            configs = if (e.config != null) s.configs + (effect to e.config) else s.configs,
                        ),
                    )
                }
            }
        }
        return lights.filter { scene.entries[it.id]?.action == SceneAction.ACTIVATE }
            .fold(looked) { acc, v -> VirtualLogic.enableExclusive(acc, v.id) }
    }

    /**
     * The lights [scene] would change, as they are now, so they can be put back after a held
     * scene is released: its own lights (IGNORE entries left out) and the lights it switches off
     * because they conflict with them. Missing lights are left out.
     */
    fun snapshot(scene: Scene, lights: List<VirtualConfig>): Map<String, VirtualConfig> {
        val after = apply(scene, lights)
        return lights.filterIndexed { i, v ->
            scene.entries[v.id]?.let { it.action != SceneAction.IGNORE } == true || after[i].enabled != v.enabled
        }.associateBy { it.id }
    }

    /**
     * Puts back the on/off state and look saved by [snapshot]. Anything else changed meanwhile
     * (e.g. segments edited, lights removed) is kept.
     */
    fun restore(snapshot: Map<String, VirtualConfig>, lights: List<VirtualConfig>): List<VirtualConfig> =
        lights.map { v -> snapshot[v.id]?.let { old -> v.copy(enabled = old.enabled, settings = old.settings) } ?: v }

    /**
     * True if the lights currently look the way [scene] would set them (to highlight it). Also
     * false while a light that the scene would switch off is on, e.g. a newer group covering it.
     */
    fun matches(scene: Scene, lights: List<VirtualConfig>): Boolean {
        val after = apply(scene, lights)
        var checked = 0
        for ((i, d) in lights.withIndex()) {
            if (d.enabled != after[i].enabled) return false
            val e = scene.entries[d.id] ?: continue
            when (e.action) {
                SceneAction.IGNORE -> continue
                SceneAction.STOP -> {}
                SceneAction.ACTIVATE -> if (d.enabled) {
                    // Switched off by a later light of the same scene: nothing more to compare.
                    val s = d.settings
                    if (e.effectId != null && e.effectId != s.effectId) return false
                    if (e.paletteId != null && e.paletteId != s.paletteId) return false
                    if (e.customGradient != s.customGradient) return false
                    if (e.brightness != null && abs(e.brightness - s.brightness) > 0.005f) return false
                    if (e.config != null && !sameConfig(e.config, s.config(s.effectId))) return false
                }
            }
            checked++
        }
        return checked > 0
    }

    /** Compares settings maps; numbers compare by value, since JSON turns Floats into Doubles. */
    private fun sameConfig(a: Map<String, Any>, b: Map<String, Any>): Boolean {
        if (a.keys != b.keys) return false
        return a.all { (k, va) ->
            val vb = b[k]
            if (va is Number && vb is Number) abs(va.toDouble() - vb.toDouble()) < 1e-4 else va == vb
        }
    }
}
