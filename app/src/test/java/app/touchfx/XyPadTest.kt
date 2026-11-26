package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.effects.ChoiceParam
import app.touchfx.effects.Effects
import app.touchfx.effects.FloatParam
import app.touchfx.effects.IntParam
import app.touchfx.effects.Rgb
import app.touchfx.engine.PadState
import app.touchfx.engine.PadTarget
import app.touchfx.engine.XyPad

class XyPadTest {
    private fun pad(x: Float, xt: PadTarget, y: Float = 0.5f, yt: PadTarget = PadTarget.NONE) = PadState(x, y, xt, yt)

    @Test
    fun everyMappedSettingExistsAndCanBeMoved() {
        for ((target, effects) in XyPad.MACROS) for ((id, macros) in effects) {
            assertTrue("$target: unknown effect $id", Effects.exists(id))
            val info = Effects.info(id)
            for (m in macros) {
                val p = info.params.firstOrNull { it.key == m.key }
                assertTrue("$target: $id has no setting ${m.key}", p is FloatParam || p is IntParam || p is ChoiceParam)
            }
        }
    }

    @Test
    fun centreKeepsTheSavedValueAndTheEdgesReachTheRange() {
        assertEquals(4f, XyPad.around(4f, 1f, 10f, 0.5f), 1e-6f)
        assertEquals(1f, XyPad.around(4f, 1f, 10f, 0f), 1e-6f)
        assertEquals(10f, XyPad.around(4f, 1f, 10f, 1f), 1e-6f)
        assertEquals(7f, XyPad.around(4f, 1f, 10f, 0.75f), 1e-6f)
        val scroll = Effects.info("scroll")
        val saved = mapOf<String, Any>("speed" to 4)
        assertEquals(saved, XyPad.applyToConfig(scroll, saved, pad(0.5f, PadTarget.SPEED)).filterKeys { it == "speed" }.mapValues { (it.value as Number).toInt() })
        assertEquals(10f, (XyPad.applyToConfig(scroll, saved, pad(1f, PadTarget.SPEED))["speed"] as Number).toFloat(), 1e-6f)
    }

    @Test
    fun invertedSettingsAndChoicesGoTheRightWay() {
        // PlasmaWled2d's "speed" is a divider: pad right = faster = lower value.
        val plasma = Effects.info("plasmawled")
        assertEquals(0, XyPad.applyToConfig(plasma, emptyMap(), pad(1f, PadTarget.SPEED))["speed"])
        // Segment Chase speed is a choice listed fast -> slow: pad right = "4 per beat".
        val chase = Effects.info("segment_chase")
        assertEquals("4 per beat", XyPad.applyToConfig(chase, emptyMap(), pad(1f, PadTarget.SPEED))["speed"])
        assertEquals("Every bar (4 beats)", XyPad.applyToConfig(chase, emptyMap(), pad(0f, PadTarget.SPEED))["speed"])
        assertEquals("1 per beat", XyPad.applyToConfig(chase, emptyMap(), pad(0.5f, PadTarget.SPEED))["speed"])
    }

    @Test
    fun bothAxesWorkTogetherAndIntSettingsStayWhole() {
        val texter = Effects.info("texter2d")
        val out = XyPad.applyToConfig(texter, emptyMap(), PadState(1f, 0.25f, PadTarget.SPEED, PadTarget.SIZE))
        assertEquals(3f, (out["speed_option_1"] as Number).toFloat(), 1e-6f)
        assertTrue(out["height_percent"] is Int)
        assertEquals(55, out["height_percent"]) // halfway between 10 and the saved 100
    }

    @Test
    fun colourTargetsWorkOnEveryEffect() {
        assertTrue(XyPad.affects(PadTarget.HUE, "wavelength"))
        assertFalse(XyPad.affects(PadTarget.SPEED, "wavelength"))
        assertEquals(0f, XyPad.hueShift(pad(0.5f, PadTarget.HUE)), 1e-6f)
        assertEquals(180f, XyPad.hueShift(pad(1f, PadTarget.HUE)), 1e-4f)
        // Red turned by 120 degrees is green.
        val g = XyPad.recolor(0xFF0000, 120f, 1f)
        assertTrue("got ${Integer.toHexString(g)}", Rgb.g(g) > 250 && Rgb.r(g) < 5 && Rgb.b(g) < 5)
        assertEquals(0f, XyPad.brightness(pad(0f, PadTarget.BRIGHTNESS)), 1e-6f)
        assertEquals(1f, XyPad.brightness(pad(0.5f, PadTarget.BRIGHTNESS)), 1e-6f)
        assertEquals(0x000000, XyPad.recolor(0x808080, 0f, 0f))
        assertEquals(0xFFFFFF, XyPad.recolor(0xC0C0C0, 0f, 2f))
        assertEquals(0.25f, XyPad.paletteShift(pad(0.75f, PadTarget.PALETTE)), 1e-6f)
        // An effect setting target doesn't touch colours.
        assertEquals(1f, XyPad.brightness(pad(0f, PadTarget.SPEED)), 1e-6f)
    }

    // ---------------------------------------------------------------- Colours, Overall speed, one light

    private val palettes = listOf("a", "b", "c", "d", "e")

    @Test
    fun coloursStepsThroughThePalettesFromTheLightsOwn() {
        assertEquals(null, XyPad.pickPalette(pad(0.5f, PadTarget.COLOURS), palettes, 2))
        assertEquals("near the centre: own colours", null, XyPad.pickPalette(pad(0.52f, PadTarget.COLOURS), palettes, 2))
        assertEquals("a", XyPad.pickPalette(pad(0f, PadTarget.COLOURS), palettes, 2))
        assertEquals("e", XyPad.pickPalette(pad(1f, PadTarget.COLOURS), palettes, 2))
        assertEquals("d", XyPad.pickPalette(pad(0.75f, PadTarget.COLOURS), palettes, 2))
        assertEquals(null, XyPad.pickPalette(pad(1f, PadTarget.HUE), palettes, 2))
    }

    @Test
    fun effectsWithTheirOwnColoursTakeThemFromThePalette() {
        val colour = { t: Float -> if (t < 0.25f) 0x111111 else if (t < 0.75f) 0x222222 else 0x333333 }
        assertEquals(0x222222, XyPad.applyColours(Effects.info("singleColor"), emptyMap(), colour)["color"])
        val energy = XyPad.applyColours(Effects.info("energy"), emptyMap(), colour)
        assertEquals(listOf(0x111111, 0x222222, 0x333333), listOf(energy["color_lows"], energy["color_mids"], energy["color_high"]))
        // Peak markers keep their colour.
        val eq = XyPad.applyColours(Effects.info("equalizer2d"), mapOf("peak_color" to 0xFFFFFF), colour)
        assertEquals(0xFFFFFF, eq["peak_color"])
        // An effect without colour settings is unchanged.
        assertEquals(emptyMap<String, Any>(), XyPad.applyColours(Effects.info("wavelength"), emptyMap(), colour))
    }

    @Test
    fun overallSpeedRunsTimeQuarterToFourTimes() {
        assertEquals(0.25f, XyPad.timeScale(pad(0f, PadTarget.TIME)), 1e-4f)
        assertEquals(1f, XyPad.timeScale(pad(0.5f, PadTarget.TIME)), 1e-6f)
        assertEquals(4f, XyPad.timeScale(pad(1f, PadTarget.TIME)), 1e-4f)
        assertEquals(1f, XyPad.timeScale(pad(1f, PadTarget.SPEED)), 1e-6f)
        assertTrue(XyPad.affects(PadTarget.TIME, "wavelength") && XyPad.affects(PadTarget.COLOURS, "glitch"))
    }

    @Test
    fun aPadOpenedOnALightOnlyChangesThatLight() {
        val all = PadState(1f, 1f, PadTarget.HUE, PadTarget.TIME)
        assertTrue(all.appliesTo("f1") && all.appliesTo("g"))
        val one = all.copy(lightId = "g")
        assertTrue(one.appliesTo("g"))
        assertFalse(one.appliesTo("f1"))
    }
}
