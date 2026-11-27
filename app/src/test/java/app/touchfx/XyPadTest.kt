package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.effects.ChoiceParam
import app.touchfx.effects.Effects
import app.touchfx.effects.FloatParam
import app.touchfx.effects.IntParam
import app.touchfx.effects.Palette
import app.touchfx.effects.Palettes
import app.touchfx.effects.Rgb
import app.touchfx.engine.PadState
import app.touchfx.engine.PadTarget
import app.touchfx.engine.Timing
import app.touchfx.engine.XyPad
import kotlin.math.roundToInt

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
        // Between 10 and the saved 100, by the pad's curve.
        assertEquals(XyPad.around(100f, 10f, 100f, XyPad.shape(0.25f)).roundToInt(), out["height_percent"])
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
        assertEquals(0.5f, XyPad.paletteShift(pad(1f, PadTarget.PALETTE)), 1e-6f)
        assertEquals(XyPad.shape(0.75f) - 0.5f, XyPad.paletteShift(pad(0.75f, PadTarget.PALETTE)), 1e-6f)
        // An effect setting target doesn't touch colours.
        assertEquals(1f, XyPad.brightness(pad(0f, PadTarget.SPEED)), 1e-6f)
    }

    // ---------------------------------------------------------------- Colours, Overall speed, one light

    private fun solid(id: String, c: Int) = Palette(id, id, listOf(c))
    private val palettes = listOf(solid("a", 0x100000), solid("b", 0x200000), solid("c", 0x300000), solid("d", 0x400000), solid("e", 0x500000))

    @Test
    fun coloursGlidesThroughThePalettesFromTheLightsOwn() {
        fun at(x: Float) = XyPad.pickPalette(pad(x, PadTarget.COLOURS), "c", palettes)
        assertEquals(null, at(0.5f))
        assertEquals("near the centre: own colours", null, at(0.52f))
        assertEquals("a", at(0f)!!.id)
        assertEquals("e", at(1f)!!.id)
        assertEquals(null, XyPad.pickPalette(pad(1f, PadTarget.HUE), "c", palettes))
        // Between two palettes it blends them instead of jumping.
        assertEquals(0x38, Rgb.r(XyPad.blend(palettes[2], palettes[3], 0.5f).clamped(0.5f)))
        val between = (50..100).mapNotNull { k -> at(k / 100f)?.let { Rgb.r(it.clamped(0.5f)) } }.filter { it % 0x10 != 0 }
        assertTrue("passes colours between c and d", between.any { it in 0x31..0x3F })
        // Moving right never goes back to an earlier colour.
        val reds = (50..100).map { k -> at(k / 100f)?.let { Rgb.r(it.clamped(0.5f)) } ?: 0x30 }
        assertEquals(reds.sorted(), reds)
    }

    @Test
    fun padPalettesAreSortedByColourAndKeepThemAll() {
        val ordered = XyPad.PAD_PALETTES
        assertEquals(Palettes.all.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        assertEquals(Palettes.all.size, ordered.size)
        // Single-colour palettes come in rising hue order (red, yellow, green, blue, ...).
        val solids = listOf(solid("blue", 0x0000FF), solid("red", 0xFF0000), solid("green", 0x00FF00), solid("yellow", 0xFFFF00))
        assertEquals(listOf("red", "yellow", "green", "blue"), XyPad.paletteOrder(solids).map { it.id })
        // Palettes without one main colour go last.
        val rainbow = Palette("rb", "rb", listOf(0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFF0000))
        assertEquals("rb", XyPad.paletteOrder(listOf(rainbow) + solids).last().id)
    }

    @Test
    fun theCentreZoneDoesNothingAndSmallMovesChangeLittle() {
        assertEquals(0.5f, XyPad.shape(0.5f), 0f)
        assertEquals(0.5f, XyPad.shape(0.5f + XyPad.DEAD_ZONE), 0f)
        assertEquals(0f, XyPad.shape(0f), 1e-6f)
        assertEquals(1f, XyPad.shape(1f), 1e-6f)
        val values = (0..100).map { XyPad.shape(it / 100f) }
        assertEquals("moves the same way as the finger", values.sorted(), values)
        // Halfway to the edge changes less than half as much (finer near the centre).
        assertTrue(XyPad.shape(0.75f) - 0.5f < 0.125f)
    }

    @Test
    fun onlyWhatWorksOnARunningLightIsOffered() {
        // Energy has no speed setting and follows only the sound.
        val energy = XyPad.available(listOf("energy"))
        assertFalse(PadTarget.SPEED in energy)
        assertFalse(PadTarget.TIME in energy)
        assertTrue(PadTarget.NONE in energy && PadTarget.HUE in energy && PadTarget.BRIGHTNESS in energy)
        // With Fire next to it, Speed works on one of the two lights.
        val both = XyPad.available(listOf("energy", "fire"))
        assertEquals(1, both[PadTarget.SPEED])
        assertEquals(2, both[PadTarget.HUE])
        // Bar: no Speed, but Overall speed (beat steps) works.
        val bar = XyPad.available(listOf("bar"))
        assertFalse(PadTarget.SPEED in bar)
        assertTrue(PadTarget.TIME in bar)
        // A saved choice that doesn't work now counts as Nothing.
        assertEquals(PadTarget.NONE, XyPad.effective(PadTarget.SPEED, energy))
        assertEquals(PadTarget.SPEED, XyPad.effective(PadTarget.SPEED, both))
        // No lights on: only Nothing.
        assertEquals(setOf(PadTarget.NONE), XyPad.available(emptyList()).keys)
    }

    @Test
    fun everyTimingMarkNamesAKnownEffect() {
        for (id in XyPad.TIMING.keys + XyPad.NO_PALETTE_SHIFT) assertTrue("unknown effect $id", Effects.exists(id))
        assertEquals(Timing.BEAT, XyPad.timing("bar"))
        assertEquals(Timing.CLOCK, XyPad.timing("fire"))
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
        assertTrue(XyPad.affects(PadTarget.TIME, "fire") && XyPad.affects(PadTarget.COLOURS, "glitch"))
        assertFalse("follows only the sound", XyPad.affects(PadTarget.TIME, "wavelength"))
    }

    @Test
    fun saturationGoesFromGreyToFullColour() {
        assertEquals(0f, XyPad.saturation(pad(0.5f, PadTarget.SATURATION)), 0f)
        assertEquals(-1f, XyPad.saturation(pad(0f, PadTarget.SATURATION)), 1e-6f)
        assertEquals(1f, XyPad.saturation(pad(1f, PadTarget.SATURATION)), 1e-6f)
        // Left edge: no colour left, brightness kept.
        val grey = XyPad.recolor(0xFF0000, 0f, 1f, -1f)
        assertTrue("got ${Integer.toHexString(grey)}", Rgb.r(grey) == Rgb.g(grey) && Rgb.g(grey) == Rgb.b(grey) && Rgb.r(grey) > 250)
        // Right edge: a pale red becomes full red.
        assertEquals(0xFF0000, XyPad.recolor(0xFF8080, 0f, 1f, 1f))
        // Grey stays grey (it has no colour to strengthen).
        assertEquals(0x808080, XyPad.recolor(0x808080, 0f, 1f, 1f))
        assertEquals(0x123456, XyPad.recolor(0x123456, 0f, 1f, 0f))
        assertTrue(XyPad.affects(PadTarget.SATURATION, "wavelength") && XyPad.affects(PadTarget.STROBE, "energy"))
    }

    @Test
    fun strobeFlashesSteadilyOnTheLeftAndOnTheBeatOnTheRight() {
        // Centre (and no Strobe axis): always on.
        assertTrue((0..100).all { XyPad.strobeOpen(pad(0.5f, PadTarget.STROBE), it / 100.0, it / 100f) })
        assertTrue(XyPad.strobeOpen(pad(1f, PadTarget.HUE), 0.5, 0.5f))
        // Left edge: 20 flashes per second, each on for 30 % of its period.
        val left = pad(0f, PadTarget.STROBE)
        val on = (0 until 1000).count { XyPad.strobeOpen(left, it / 1000.0, 0f) }
        assertTrue("on $on of 1000 ms", on in 275..325)
        val flashes = (0 until 1000).map { XyPad.strobeOpen(left, it / 1000.0, 0f) }.zipWithNext().count { (a, b) -> !a && b }
        assertTrue("$flashes flashes", flashes in 19..21)
        // Just right of the centre: once per beat, on at the start of the beat.
        val right = pad(0.6f, PadTarget.STROBE)
        assertTrue(XyPad.strobeOpen(right, 0.0, 0.1f))
        assertFalse(XyPad.strobeOpen(right, 0.0, 0.5f))
        // Right edge: 8 per beat.
        val edge = pad(1f, PadTarget.STROBE)
        assertEquals(8, (0 until 800).map { XyPad.strobeOpen(edge, 0.0, it / 800f) }.zipWithNext().count { (a, b) -> !a && b } + 1)
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
