package app.touchfx

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import app.touchfx.audio.AudioSourceType
import app.touchfx.effects.Preset
import app.touchfx.engine.AppState
import app.touchfx.engine.Backup
import app.touchfx.engine.VirtualLogic
import app.touchfx.model.DeviceConfig
import app.touchfx.model.DeviceKind
import app.touchfx.model.EffectSettings
import app.touchfx.model.Mapping
import app.touchfx.model.PanelLayout
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class BackupTest {
    private val f1 = DeviceConfig(id = "f1", name = "WLED-F1", kind = DeviceKind.WLED, host = "192.168.1.110", ledCount = 128)
    private val f2 = DeviceConfig(id = "f2", name = "WLED-F2", kind = DeviceKind.WLED, host = "192.168.1.120", ledCount = 128)
    private val group = VirtualConfig(
        id = "g", name = "Panels", segments = listOf(Segment("f1", 0, 127), Segment("f2", 0, 127, invert = true)),
        mapping = Mapping.SPAN, grouping = 1, rows = 8, serpentine = true, rotate = 2, panelLayout = PanelLayout.SIDE_BY_SIDE,
        settings = EffectSettings(effectId = "texter2d", paletteId = "plasma", brightness = 0.8f,
            configs = mapOf("texter2d" to mapOf("text" to "Hallo Wörld", "speed_option_1" to 1.5f))),
    )
    private val state = AppState(
        devices = listOf(f1, f2),
        virtuals = VirtualLogic.sync(listOf(f1, f2), listOf(group)).map { if (it.id == "f1") it.copy(enabled = false) else it },
        scenes = listOf(Scene("s", "Strobe", mapOf("g" to SceneEntry(SceneAction.ACTIVATE, effectId = "random_flash", config = emptyMap())), momentary = true)),
        presets = listOf(Preset("user:1", "Mine", "energy", mapOf("sensitivity" to 0.7), listOf(0xFF0000, 0x0000FF))),
        audioSource = AudioSourceType.TAP,
        sensitivity = 0.65f,
        masterBrightness = 0.4f,
        minVolume = mapOf(AudioSourceType.MICROPHONE to 0.5f, AudioSourceType.PHONE_AUDIO to 0.2f, AudioSourceType.TAP to 0.34f),
        tapBpm = 128f,
        tapSimulate = false,
    )

    private fun roundTrip(s: AppState) = Backup.decode(Backup.encode(s, Backup.SCHEMA, "0.1.0", "2026-11-25T21:30:00").toString(2))

    @Test
    fun aBackupRestoresEverything() {
        val back = roundTrip(state)
        assertEquals(state.devices, back.devices)
        assertEquals(state.virtuals.map { it.toJson().toString() }, back.virtuals.map { it.toJson().toString() })
        assertEquals(state.scenes.map { it.toJson().toString() }, back.scenes.map { it.toJson().toString() })
        assertTrue(back.scenes.single().momentary)
        assertEquals(state.presets.map { it.toJson().toString() }, back.presets.map { it.toJson().toString() })
        assertEquals(state.copy(devices = emptyList(), virtuals = emptyList(), scenes = emptyList(), presets = emptyList()),
            back.copy(devices = emptyList(), virtuals = emptyList(), scenes = emptyList(), presets = emptyList()))
        assertEquals("Hallo Wörld", back.virtuals.first { it.id == "g" }.settings.config("texter2d")["text"])
        assertEquals("2 devices, 1 group, 1 scene, 1 own preset", Backup.summary(back))
    }

    @Test
    fun unknownEffectsAreReplacedLikeOnStart() {
        val o = Backup.encode(state, Backup.SCHEMA, "0.1.0", "x")
        o.getJSONArray("virtuals").getJSONObject(0).getJSONObject("settings").put("effect", "effect_from_the_future")
        assertTrue(Backup.decode(o.toString()).virtuals.all { it.settings.effectId != "effect_from_the_future" })
    }

    private fun rejects(text: String, expected: String) {
        try {
            Backup.decode(text); fail("accepted: $text")
        } catch (e: IllegalArgumentException) {
            assertTrue("'${e.message}' should mention '$expected'", e.message!!.contains(expected))
        }
    }

    @Test
    fun wrongOrDamagedFilesAreRejectedWithAMessage() {
        rejects("not json", "valid JSON")
        rejects("""{"hello":1}""", "isn't a TouchFx backup")
        rejects("""{"app":"ledfx-mobile","format":1,"schema":2}""", "isn't a TouchFx backup")
        rejects("""{"app":"touchfx","format":99}""", "newer version")
        rejects("""{"app":"touchfx","format":1,"schema":3}""", "newer version")
        rejects("""{"app":"touchfx","format":1,"schema":1}""", "older test version")
        rejects("""{"app":"touchfx","format":1,"schema":2,"devices":[42]}""", "damaged")
        rejects("""{"app":"touchfx","format":1,"schema":2,"devices":[{"name":"no id"}]}""", "damaged")
    }

    @Test
    fun theFileSaysWhatItIs() {
        val o = JSONObject(Backup.encode(state, Backup.SCHEMA, "0.1.0", "2026-11-25T21:30:00").toString())
        assertEquals("touchfx", o.getString("app"))
        assertEquals(Backup.FORMAT, o.getInt("format"))
        assertEquals("0.1.0", o.getString("app_version"))
        assertEquals("2026-11-25T21:30:00", o.getString("exported_at"))
    }
}
