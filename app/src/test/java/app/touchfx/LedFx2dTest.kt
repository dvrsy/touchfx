package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext

class LedFx2dTest {
    /** Loud lows, quiet highs (both filtered and raw bands). */
    private val bassy: AudioFrame = run {
        val bands = FloatArray(32) { if (it < 10) 0.9f else 0.05f }
        val power = floatArrayOf(0.9f, 0.9f, 0.1f, 0.05f)
        AudioFrame(bands, bands.copyOf(), 0.8f, power, power.copyOf(), false, false, 0f, 0f, 0f, 0, false, 120f, 0f, false)
    }

    private fun render(id: String, w: Int, h: Int, settings: Map<String, Any> = emptyMap(), frames: Int = 1, audio: AudioFrame = bassy,
                       palette: String = "rainbow", configs: Map<String, Map<String, Any>> = emptyMap()): IntArray {
        val info = Effects.info(id)
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, settings); rows = h; this.palette = Palettes.get(palette); this.audio = audio
            effectConfigs = configs
        }
        val fx = info.create()
        val out = IntArray(w * h)
        repeat(frames) { fx.render(ctx, out); ctx.time += 1.0 / 60 }
        return out
    }

    /** Lit rows in column [x] of a w x h image. */
    private fun height(img: IntArray, w: Int, h: Int, x: Int) = (0 until h).count { y -> img[y * w + x] != 0 }

    // ---------------------------------------------------------------- Equalizer2d

    @Test
    fun equalizerBarsFollowTheSpectrum() {
        val img = render("equalizer2d", 32, 16, mapOf("bands" to 8, "filtered" to false))
        assertTrue("bass bars tall: ${height(img, 32, 16, 0)}", height(img, 32, 16, 0) >= 12)
        assertTrue("treble bars short: ${height(img, 32, 16, 31)}", height(img, 32, 16, 31) <= 3)
        // Bars grow from the bottom.
        assertTrue(img[15 * 32 + 31] != 0 && img[0 * 32 + 31] == 0)
    }

    @Test
    fun centredBarsAreSymmetric() {
        val img = render("equalizer2d", 32, 16, mapOf("bands" to 8, "filtered" to false, "center" to true))
        for (x in 0 until 32) for (d in 1..6) {
            assertEquals("x=$x d=$d", img[(8 - d) * 32 + x] != 0, img[(8 + d) * 32 + x] != 0)
        }
    }

    @Test
    fun ringWithPeaksStaysInsideTheMatrix() {
        val img = render("equalizer2d", 16, 16, mapOf("ring" to true, "spin" to true, "peak_marks" to true, "peak_percent" to 5,
            "power_gradient" to "Progressive", "filtered" to false), frames = 30)
        assertTrue(img.count { it != 0 } > 20)
        assertTrue("peak markers drawn", img.any { it == 0xFFFFFF })
    }

    // ---------------------------------------------------------------- Radial

    /** Radial of the Gradient effect (a rainbow strip) on a 16x16 matrix. */
    private fun radial(settings: Map<String, Any> = emptyMap(), frames: Int = 1, audio: AudioFrame = bassy) =
        render("radial", 16, 16, mapOf("source" to "gradient") + settings, frames, audio)

    @Test
    fun radialPutsTheStripStartInTheCentreAndItsEndInTheCorners() {
        val strip = render("gradient", 64, 1)
        val img = radial()
        assertEquals(strip[0], img[8 * 16 + 8])
        assertEquals(strip[63], img[0])
        // A circle: pixels at the same distance from the centre share a colour.
        assertEquals(img[8 * 16 + 4], img[8 * 16 + 12])
        assertEquals(img[4 * 16 + 8], img[12 * 16 + 8])
    }

    @Test
    fun radialShapesAndSpin() {
        assertNotEquals(radial().toList(), radial(mapOf("edges" to 4)).toList())
        assertNotEquals(radial(mapOf("edges" to 5)).toList(), radial(mapOf("edges" to 5, "polygon" to false)).toList())
        // Spin follows the music; without spin it stays put.
        assertNotEquals(radial(mapOf("edges" to 3, "spin" to 1f), frames = 1).toList(), radial(mapOf("edges" to 3, "spin" to 1f), frames = 20).toList())
        assertEquals(radial(mapOf("edges" to 3), frames = 1).toList(), radial(mapOf("edges" to 3), frames = 20).toList())
    }

    @Test
    fun radialNeedsAStripEffectAsItsSource() {
        // A matrix effect or Radial itself can't be the source: nothing is drawn.
        assertTrue(render("radial", 16, 16, mapOf("source" to "plasma2d")).all { it == 0 })
        assertTrue(render("radial", 16, 16, mapOf("source" to "radial")).all { it == 0 })
    }
}
