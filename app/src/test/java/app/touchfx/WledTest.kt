package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.EffectCategory
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.effects.WledMath

class WledTest {
    // ---------------------------------------------------------------- maths, as in WLED

    @Test
    fun trigMatchesWled() {
        assertEquals(128, WledMath.sin8(0))
        assertEquals(255, WledMath.sin8(64))
        assertTrue(WledMath.sin8(192) <= 1)
        assertEquals(WledMath.sin8(64), WledMath.cos8(0))
        assertTrue(WledMath.sin16(0x4000) in 32766..32767)
        assertTrue(WledMath.sin16(0xC000) in -32767..-32766)
        assertEquals(0, WledMath.ease8InOutCubic(0))
        assertTrue(WledMath.ease8InOutCubic(128) in 127..129)
        assertTrue(WledMath.ease8InOutCubic(255) >= 253)
    }

    @Test
    fun beatsinStaysInRangeAndWrapsLikeUint16() {
        for (ms in 0L..20_000L step 37) {
            assertTrue(WledMath.beatsin8(60, ms, 64, 255) in 64..255)
            // beatsin16 with a negative lowest wraps like WLED's uint16: as signed it stays in -360..360.
            val v = WledMath.beatsin16(3, ms, -360, 360).toShort().toInt()
            assertTrue("$v", v in -360..360)
        }
    }

    @Test
    fun perlinNoiseIsSmoothAndUsesTheRange() {
        val values = (0 until 2000).map { WledMath.perlin16(it * 3000, 12345, 777) }
        assertTrue(values.all { it in 0..0xFFFF })
        assertTrue("varies", values.max() - values.min() > 20000)
        // Neighbouring samples are close: noise, not random.
        assertTrue(values.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < 6000 })
    }

    // ---------------------------------------------------------------- effects

    private fun frame(volume: Float = 0f, kick: Boolean = false) = AudioFrame(
        FloatArray(32) { if (volume > 0f && it == 6) volume else 0f }, FloatArray(32), volume, FloatArray(4), FloatArray(4),
        kick, false, 0f, 0f, 0f, 0, false, 120f, 0f, volume == 0f,
    )

    private fun run(id: String, n: Int, seconds: Double, rows: Int = 1, settings: Map<String, Any> = emptyMap(),
                    audio: (Int) -> AudioFrame = { frame() }): List<IntArray> {
        val info = Effects.info(id)
        val fx = info.create()
        val ctx = RenderContext().apply { p = ParamValues(info.params, settings); palette = Palettes.get("rainbow"); this.rows = rows }
        return List((seconds * 60).toInt()) { f ->
            ctx.audio = audio(f); ctx.time = f / 60.0
            IntArray(n).also { fx.render(ctx, it) }
        }
    }

    @Test
    fun everyWledEffectRendersAtAnySize() {
        val wled = Effects.all.filter { it.id.startsWith("wled_") }
        assertEquals(10, wled.size)
        for (info in wled) {
            val sizes = if (info.category == EffectCategory.MATRIX) listOf(1 to 1, 8 to 8, 32 to 16, 16 to 3) else listOf(1 to 1, 2 to 1, 60 to 1, 300 to 1)
            for ((w, h) in sizes) {
                val frames = run(info.id, w * h, 3.0, rows = h) { f -> frame(volume = 0.7f, kick = f % 30 == 0) }
                assertTrue("${info.id} ${w}x$h invalid colour", frames.all { fr -> fr.all { it in 0..0xFFFFFF } })
                if (w * h >= 60) assertTrue("${info.id} ${w}x$h stayed dark", frames.any { fr -> fr.any { it != 0 } })
            }
        }
    }

    @Test
    fun blinkDutyCycleFollowsIntensity() {
        fun litShare(intensity: Int) = run("wled_blink", 20, 20.0, settings = mapOf("speed" to 128, "intensity" to intensity))
            .count { fr -> fr.any { it != 0 } } / (20.0 * 60)
        val half = litShare(128)
        assertTrue("half duty: $half", half in 0.4..0.6)
        assertTrue(litShare(230) > litShare(30) + 0.5)
    }

    @Test
    fun puddlepeakOnlySplashesOnLoudEnoughPeaks() {
        val quiet = run("wled_puddlepeak", 60, 3.0) { frame(volume = 0.7f) }
        assertTrue("no peaks, no puddles", quiet.all { fr -> fr.all { it == 0 } })
        val peaks = run("wled_puddlepeak", 60, 3.0) { f -> frame(volume = 0.7f, kick = f % 30 == 0) }
        assertTrue(peaks.any { fr -> fr.any { it != 0 } })
        val tooQuiet = run("wled_puddlepeak", 60, 3.0, settings = mapOf("custom2" to 255)) { f -> frame(volume = 0.3f, kick = f % 30 == 0) }
        assertTrue("below the minimum volume", tooQuiet.all { fr -> fr.all { it == 0 } })
    }

    @Test
    fun gravfreqBarGrowsWithVolume() {
        fun lit(volume: Float) = run("wled_gravfreq", 100, 2.0, settings = mapOf("intensity" to 255)) { frame(volume = volume) }
            .last().count { it != 0 }
        assertTrue("louder = longer bar: ${lit(0.3f)} vs ${lit(0.9f)}", lit(0.9f) > lit(0.3f) + 10)
    }
}
