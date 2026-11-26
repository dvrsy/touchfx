package app.touchfx

import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.effects.EffectInfo
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import kotlin.math.PI
import kotlin.math.sin

/** Every effect that shows the Colours section must react to the palette picked there. */
class ColoursTest {
    private val hop = AudioAnalyzer.SAMPLE_RATE / 60

    /** All pixels of 2 s of music-like input with [palette]; same audio for every call. */
    private fun render(info: EffectInfo, palette: String, settings: Map<String, Any>): List<Int> {
        val a = AudioAnalyzer()
        val fx = info.create()
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, settings); this.palette = Palettes.get(palette); rows = if (info.is2d) 8 else 1
        }
        val out = IntArray(64)
        val buf = FloatArray(hop)
        val all = ArrayList<Int>()
        for (k in 0 until 120) {
            for (i in 0 until hop) {
                val t = k * hop + i
                buf[i] = (0.4 * sin(2 * PI * 80 * t / 44100) * (if (t % 22050 < 2000) 1.0 else 0.2) +
                    0.05 * sin(2 * PI * 3000 * t / 44100)).toFloat()
            }
            a.push(buf, hop)
            ctx.audio = a.analyze(k * 16_666_667L); ctx.time = k / 60.0
            fx.render(ctx, out); all.addAll(out.toList())
        }
        return all
    }

    @Test
    fun everyEffectWithColoursReactsToThePalette() {
        val ignoring = Effects.all.filter { it.usesGradient }.filter { info ->
            // What the app does when a palette is picked: the effect's gradient switch goes on.
            val settings = info.gradientToggle?.let { mapOf<String, Any>(it to true) } ?: emptyMap()
            render(info, "red", settings) == render(info, "blue", settings)
        }.map { it.id }
        assertTrue("effects ignoring the picked palette: $ignoring", ignoring.isEmpty())
    }

    @Test
    fun effectsWithAGradientSwitchUseTheirOwnColourWhenItIsOff() {
        val withSwitch = Effects.all.filter { it.usesGradient && it.gradientToggle != null }
        assertTrue(withSwitch.map { it.id }.containsAll(listOf("scan", "scan_and_flare", "scan_multi", "filter", "texter2d")))
        for (info in withSwitch.filter { info -> info.params.first { it.key == info.gradientToggle }.default == false }) {
            val off = mapOf<String, Any>(info.gradientToggle!! to false)
            assertTrue("${info.id} should ignore the palette while its switch is off",
                render(info, "red", off) == render(info, "blue", off))
        }
    }
}
