package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effect
import app.touchfx.effects.EffectChase
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.engine.PixelMapper
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class EffectChaseTest {
    private val green = 0x00FF00
    private val red = 0xFF0000
    /** Three WLED strips of 10 LEDs as one group. */
    private val segs = PixelMapper.segmentIndex(VirtualConfig(id = "g", name = "G",
        segments = listOf(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9))))

    private fun beat(count: Int) = AudioFrame(FloatArray(32), FloatArray(32), 0f, FloatArray(4), FloatArray(4),
        false, false, 0f, 0.1f, 0f, count, false, 120f, 0f, false)

    /**
     * Effect Chase with Single Color (set to green in the light's saved settings) and Gradient
     * (red palette): what each of the three devices shows after each beat.
     */
    private fun run(settings: Map<String, Any>, beats: Int): List<List<Int>> {
        val info = Effects.info("effect_chase")
        val fx: Effect = info.create()
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, mapOf("effects" to "singleColor,gradient", "trail" to 0f) + settings)
            palette = Palettes.get("red")
            segmentOf = segs; segmentCount = 3
            effectConfigs = mapOf("singleColor" to mapOf("color" to green))
        }
        val out = IntArray(30)
        return List(beats) { b ->
            ctx.audio = beat(b)
            repeat(30) { fx.render(ctx, out); ctx.time += 1.0 / 60 }
            listOf(out[0], out[10], out[20])
        }
    }

    @Test
    fun eachStepBringsTheNextEffectToTheLitDevice() {
        assertEquals(
            listOf(listOf(green, 0, 0), listOf(0, red, 0), listOf(0, 0, green), listOf(red, 0, 0)),
            run(emptyMap(), 4),
        )
    }

    @Test
    fun perPartEveryDeviceKeepsItsEffect() {
        assertEquals(
            listOf(listOf(green, 0, 0), listOf(0, red, 0), listOf(0, 0, green), listOf(green, 0, 0)),
            run(mapOf("effect_per" to "Per part"), 4),
        )
    }

    @Test
    fun effectShiftLightsEverythingAndMovesTheEffectsOn() {
        val frames = run(mapOf("mode" to "Effect shift"), 3)
        assertTrue(frames.all { f -> f.all { it != 0 } })
        assertEquals(listOf(red, green, red), frames[0])
        assertEquals(listOf(green, red, green), frames[1])
    }

    @Test
    fun chosenEffectsUseTheLightsOwnSettings() {
        val info = Effects.info("effect_chase")
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, mapOf("effects" to "singleColor", "mode" to "Effect shift", "parts" to 2))
            effectConfigs = emptyMap() // nothing saved: the effect's default (red)
        }
        val out = IntArray(8)
        val fx = info.create()
        fx.render(ctx, out)
        assertTrue(out.all { it == red })
        ctx.effectConfigs = mapOf("singleColor" to mapOf("color" to 0x0000FF))
        fx.render(ctx, out)
        assertTrue(out.all { it == 0x0000FF })
    }

    @Test
    fun noNestingAndMatrixEffectsOnlyOnMatrices() {
        assertEquals(listOf("fire"), EffectChase.usableEffects("effect_chase, plasma2d,fire,unknown,fire", matrix = false))
        assertEquals(listOf("plasma2d", "fire"), EffectChase.usableEffects("effect_chase,plasma2d,fire", matrix = true))
    }
}
