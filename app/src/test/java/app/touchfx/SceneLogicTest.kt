package app.touchfx

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.engine.SceneLogic
import app.touchfx.model.EffectSettings
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class SceneLogicTest {
    /** A device's own virtual (scenes target virtuals). */
    private fun device(id: String, settings: EffectSettings = EffectSettings(), enabled: Boolean = true) =
        VirtualConfig(id = id, name = id, segments = listOf(Segment(id, 0, 29)), isDevice = id, enabled = enabled, settings = settings)

    private val partyLook = EffectSettings(
        effectId = "energy", paletteId = "plasma", brightness = 0.7f,
        configs = mapOf("energy" to mapOf("sensitivity" to 0.8f, "mirror" to true), "fire" to mapOf("blur" to 3f)),
    )

    @Test
    fun captureSavesIncludedLightsAndIgnoresTheRest() {
        val devices = listOf(device("1", partyLook), device("2"), device("3", enabled = false))
        val scene = SceneLogic.capture("s", "Party", devices, setOf("1", "3"))
        assertEquals(SceneAction.ACTIVATE, scene.entries["1"]!!.action)
        assertEquals("energy", scene.entries["1"]!!.effectId)
        assertEquals(mapOf("sensitivity" to 0.8f, "mirror" to true), scene.entries["1"]!!.config)
        assertEquals(SceneAction.IGNORE, scene.entries["2"]!!.action)
        assertEquals(SceneAction.STOP, scene.entries["3"]!!.action)
    }

    @Test
    fun activateMergesWithoutLosingOtherEffectsSettings() {
        val scene = SceneLogic.capture("s", "Party", listOf(device("1", partyLook)), setOf("1"))
        val current = EffectSettings(
            effectId = "fire", paletteId = "ocean", brightness = 0.2f,
            configs = mapOf("fire" to mapOf("blur" to 9f), "rain" to mapOf("blur" to 1f)),
        )
        val applied = SceneLogic.apply(scene, listOf(device("1", current, enabled = false))).single()
        assertTrue(applied.enabled)
        assertEquals("energy", applied.settings.effectId)
        assertEquals("plasma", applied.settings.paletteId)
        assertEquals(0.7f, applied.settings.brightness, 0f)
        assertEquals(mapOf("sensitivity" to 0.8f, "mirror" to true), applied.settings.configs["energy"])
        // The light's own remembered settings for other effects survive.
        assertEquals(mapOf("blur" to 9f), applied.settings.configs["fire"])
        assertEquals(mapOf("blur" to 1f), applied.settings.configs["rain"])
    }

    @Test
    fun ignoreLeavesLightAloneAndStopTurnsItOff() {
        val scene = Scene(
            "s", "Mixed",
            mapOf("1" to entry(SceneAction.IGNORE), "2" to entry(SceneAction.STOP)),
        )
        val before = listOf(device("1", partyLook), device("2", partyLook))
        val after = SceneLogic.apply(scene, before)
        assertEquals(before[0], after[0])
        assertFalse(after[1].enabled)
        assertEquals(partyLook, after[1].settings)
    }

    @Test
    fun entriesForRemovedLightsAreSkippedAndNewLightsUntouched() {
        val scene = SceneLogic.capture("s", "Old", listOf(device("gone", partyLook)), setOf("gone"))
        val now = listOf(device("new"))
        assertEquals(now, SceneLogic.apply(scene, now))
        assertFalse("nothing to compare means not active", SceneLogic.matches(scene, now))
    }

    @Test
    fun sceneMatchesRightAfterApplyAndNotAfterAChange() {
        val scene = SceneLogic.capture("s", "Party", listOf(device("1", partyLook), device("2")), setOf("1", "2"))
        val applied = SceneLogic.apply(scene, listOf(device("1"), device("2", partyLook)))
        assertTrue(SceneLogic.matches(scene, applied))
        val changed = applied.map { if (it.id == "1") it.copy(settings = it.settings.withValue("sensitivity", 0.3f)) else it }
        assertFalse(SceneLogic.matches(scene, changed))
    }

    @Test
    fun jsonRoundTripKeepsEverythingAndStillMatches() {
        val devices = listOf(device("1", partyLook.copy(customGradient = listOf(0xFF0000, 0x0000FF))), device("2"), device("3", enabled = false))
        val scene = SceneLogic.capture("s", "Party", devices, setOf("1", "3"))
        val loaded = Scene.fromJson(JSONObject(scene.toJson().toString()))
        assertEquals(scene.id, loaded.id)
        assertEquals(scene.name, loaded.name)
        assertEquals(scene.entries.keys, loaded.entries.keys)
        assertEquals(scene.entries.mapValues { it.value.action }, loaded.entries.mapValues { it.value.action })
        assertEquals(listOf(0xFF0000, 0x0000FF), loaded.entries["1"]!!.customGradient)
        // Floats come back from JSON as Doubles; matching must still work.
        assertTrue(SceneLogic.matches(loaded, devices))
    }

    private fun entry(action: SceneAction) = SceneEntry(action)
}
