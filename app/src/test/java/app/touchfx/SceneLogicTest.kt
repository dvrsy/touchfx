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

    /** A group over the given segments. */
    private fun group(id: String, vararg segments: Segment, enabled: Boolean = true, settings: EffectSettings = EffectSettings()) =
        VirtualConfig(id = id, name = id, segments = segments.toList(), enabled = enabled, settings = settings)

    @Test
    fun groupMadeAfterTheSceneIsSwitchedOffWhereItConflicts() {
        // Scene saved for devices 1 and 2; later a group over part of device 1 is made and turned on,
        // which switched device 1 off (issue #1).
        val scene = SceneLogic.capture("s", "Party", listOf(device("1", partyLook), device("2", partyLook)), setOf("1", "2"))
        val now = listOf(device("1", enabled = false), device("2"), device("3"), group("g", Segment("1", 0, 9)))
        val after = SceneLogic.apply(scene, now).associateBy { it.id }
        assertTrue(after["1"]!!.enabled)
        assertTrue(after["2"]!!.enabled)
        assertFalse("the newer group would draw over device 1", after["g"]!!.enabled)
        assertTrue("lights not in the scene and not in the way stay on", after["3"]!!.enabled)
        assertTrue(SceneLogic.matches(scene, SceneLogic.apply(scene, now)))
    }

    @Test
    fun sceneWithAGroupSwitchesOffItsDevicesAndOverlappingGroups() {
        val g = group("g", Segment("1", 0, 9), Segment("2", 0, 9), settings = partyLook)
        val scene = SceneLogic.capture("s", "Group", listOf(g), setOf("g"))
        // A device light and an overlapping group were turned on since.
        val now = listOf(device("1"), device("2", enabled = false), device("3"), g.copy(enabled = false), group("h", Segment("2", 5, 20)))
        val after = SceneLogic.apply(scene, now).associateBy { it.id }
        assertTrue(after["g"]!!.enabled)
        assertFalse(after["1"]!!.enabled)
        assertFalse(after["h"]!!.enabled)
        assertTrue(after["3"]!!.enabled)
    }

    @Test
    fun whenTwoLightsOfTheSceneNowConflictTheLaterOneWins() {
        // Saved while the group covered only device 2; its segments were changed to include device 1.
        val scene = SceneLogic.capture(
            "s", "Both", listOf(device("1", partyLook), group("g", Segment("2", 0, 9), settings = partyLook)), setOf("1", "g"),
        )
        val now = listOf(device("1"), group("g", Segment("1", 0, 9), Segment("2", 0, 9)))
        val after = SceneLogic.apply(scene, now)
        assertFalse(after[0].enabled)
        assertTrue(after[1].enabled)
        assertTrue(SceneLogic.matches(scene, after))
    }

    @Test
    fun sceneIsNotHighlightedWhileANewerGroupCoversIt() {
        val scene = SceneLogic.capture("s", "Party", listOf(device("1", partyLook)), setOf("1"))
        val covered = listOf(device("1", partyLook), group("g", Segment("1", 0, 9)))
        assertFalse(SceneLogic.matches(scene, covered))
    }

    @Test
    fun releasingAHeldSceneBringsBackLightsItSwitchedOff() {
        val scene = SceneLogic.capture("s", "Strobe", listOf(device("1", partyLook)), setOf("1"))
        val before = listOf(device("1", enabled = false), group("g", Segment("1", 0, 9)), device("2"))
        val snap = SceneLogic.snapshot(scene, before)
        assertEquals(setOf("1", "g"), snap.keys)
        val held = SceneLogic.apply(scene, before)
        assertFalse(held[1].enabled)
        assertEquals(before, SceneLogic.restore(snap, held))
    }

    private fun entry(action: SceneAction) = SceneEntry(action)
}
