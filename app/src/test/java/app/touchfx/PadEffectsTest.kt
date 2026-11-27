package app.touchfx

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effect
import app.touchfx.effects.EffectInfo
import app.touchfx.effects.Effects
import app.touchfx.effects.FxRandom
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.engine.EffectClock
import app.touchfx.engine.PadTarget
import app.touchfx.engine.Timing
import app.touchfx.engine.XyPad
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pad's Overall speed, Palette shift and Colours, checked against what every effect really
 * does: each effect is rendered with the same music, with and without the change.
 */
class PadEffectsTest {
    @After
    fun unseed() {
        FxRandom.source = Random.Default
    }

    // ---------------------------------------------------------------- EffectClock on its own

    /** Counts its renders and remembers the time and beat it was given. */
    private class Probe : Effect() {
        var renders = 0
        val times = ArrayList<Double>()
        var audio: AudioFrame? = null
        override fun render(ctx: RenderContext, out: IntArray) {
            renders++; times += ctx.time; audio = ctx.audio
            out.fill(renders)
        }
    }

    private fun frame(ctx: RenderContext, k: Int) { ctx.time = k / 60.0; ctx.dt = 1 / 60f }

    @Test
    fun stepEffectsRenderLessOrMoreOftenAndKeepThePictureBetween() {
        val fx = Probe(); val clock = EffectClock(); val ctx = RenderContext(); val out = IntArray(4)
        val pictures = (0 until 8).map { k -> frame(ctx, k); clock.render(fx, Timing.STEPS, 0.5f, ctx, out); out[0] }
        // The first frame always renders (there is no picture yet); then every second frame,
        // and a skipped frame shows the last picture.
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5), pictures)
        val fast = Probe(); val c2 = EffectClock()
        repeat(8) { k -> frame(ctx, k); c2.render(fast, Timing.STEPS, 2f, ctx, out) }
        assertEquals(16, fast.renders)
        // Its clock runs at the same rate as its steps, so time-based parts stay in step.
        assertEquals(15 / 60.0, fast.times.last(), 1e-6)
        val capped = Probe(); val c3 = EffectClock()
        frame(ctx, 0); c3.render(capped, Timing.STEPS, 100f, ctx, out)
        assertEquals(EffectClock.MAX_STEPS, capped.renders)
    }

    @Test
    fun clockEffectsRunTheirClockFasterAndStayAheadWhenReleased() {
        val fx = Probe(); val clock = EffectClock(); val ctx = RenderContext(); val out = IntArray(4)
        for (k in 0 until 60) { frame(ctx, k); clock.render(fx, Timing.CLOCK, 2f, ctx, out) }
        assertEquals(2 * 59 / 60.0 + 1 / 60.0, fx.times.last(), 1e-6)
        frame(ctx, 60); clock.render(fx, Timing.CLOCK, 1f, ctx, out)
        assertTrue("no jump back when the pad is let go", fx.times.last() > fx.times[58])
    }

    private fun beat(phase: Float, count: Int, tempoBeat: Boolean = false) = AudioFrame(
        FloatArray(1), FloatArray(1), 0f, FloatArray(4), FloatArray(4), false, false, 0f,
        phase, (count % 4) + phase, count, tempoBeat, 120f, 0f, false,
    )

    @Test
    fun beatEffectsGetHalfAndDoubleTimeThatStaysOnTheBeat() {
        val clock = EffectClock()
        // Half time: one cycle spans two beats.
        assertEquals(0.25f, clock.beatTime(beat(0.5f, 4), -1).beatPhase, 1e-6f)
        assertEquals(0.75f, clock.beatTime(beat(0.5f, 5), -1).beatPhase, 1e-6f)
        assertEquals(60f, clock.beatTime(beat(0.5f, 5), -1).bpm, 1e-6f)
        // Double time: two cycles per beat, each starting a new beat.
        val c2 = EffectClock()
        val a = c2.beatTime(beat(0.25f, 8), 1)
        val b = c2.beatTime(beat(0.75f, 8), 1)
        assertEquals(0.5f, a.beatPhase, 1e-6f)
        assertEquals(0.5f, b.beatPhase, 1e-6f)
        assertTrue("the second half of the beat starts a new one", b.tempoBeat)
        assertFalse(c2.beatTime(beat(0.8f, 8), 1).tempoBeat)
        assertEquals(240f, b.bpm, 1e-6f)
        // Steps from the Overall speed: 0.25x .. 4x.
        assertEquals(listOf(-2, -1, 0, 1, 2), listOf(0.25f, 0.5f, 1f, 2f, 4f).map { EffectClock.beatSteps(it) })
        assertEquals(0, EffectClock.beatSteps(1.3f))
    }

    @Test
    fun aBeatEffectDoesNotPassItsBeatOnToTheNextLight() {
        val ctx = RenderContext().apply { audio = beat(0.5f, 4) }
        val real = ctx.audio
        val fx = Probe()
        EffectClock().render(fx, Timing.BEAT, 0.5f, ctx, IntArray(4))
        assertEquals(0.25f, fx.audio!!.beatPhase, 1e-6f)
        assertSame(real, ctx.audio)
    }

    // ---------------------------------------------------------------- every effect, measured

    private val hop = AudioAnalyzer.SAMPLE_RATE / 60

    /** Settings that show the effect moving: gradient on, modulation on, a moving effect inside. */
    private fun settings(info: EffectInfo): Map<String, Any> {
        val m = HashMap<String, Any>()
        info.gradientToggle?.let { m[it] = true }
        when (info.id) {
            "gradient", "singleColor" -> m["modulate"] = true
            "filter" -> m["roll_speed"] = 0.5f
            "radial" -> m["source"] = "fade"
            "blend" -> { m["background"] = "fade"; m["foreground"] = "fade" }
        }
        return m
    }

    /** 3 s of music-like input (kicks twice a second) through [info] via the pad's clock. */
    private fun render(
        info: EffectInfo, timing: Timing = Timing.CLOCK, scale: Float = 1f, offset: Float = 0f, palette: String = "rainbow",
        recolour: Boolean = false,
    ): List<Int> {
        FxRandom.source = Random(7)
        val a = AudioAnalyzer(); val fx = info.create(); val clock = EffectClock()
        val pal = Palettes.get(palette)
        var config = settings(info)
        if (recolour) config = XyPad.applyColours(info, config) { pal.clamped(it) }
        val ctx = RenderContext().apply { p = ParamValues(info.params, config); this.palette = pal; rows = if (info.is2d) 8 else 1 }
        val out = IntArray(64); val buf = FloatArray(hop); val all = ArrayList<Int>()
        for (k in 0 until 180) {
            for (i in 0 until hop) {
                val t = k * hop + i
                buf[i] = (0.4 * sin(2 * PI * 80 * t / 44100) * (if (t % 22050 < 2000) 1.0 else 0.2) +
                    0.05 * sin(2 * PI * 3000 * t / 44100)).toFloat()
            }
            a.push(buf, hop)
            ctx.audio = a.analyze(k * 16_666_667L); ctx.time = k / 60.0; ctx.dt = 1 / 60f; ctx.gradientOffset = offset
            clock.render(fx, timing, scale, ctx, out); all.addAll(out.toList())
        }
        return all
    }

    @Test
    fun rendersRepeatWithASeededRandomSource() {
        val wrong = Effects.all.filter { render(it) != render(it) }.map { it.id }
        assertTrue("effects not using FxRandom: $wrong", wrong.isEmpty())
    }

    @Test
    fun overallSpeedMarksMatchWhatEachEffectDoes() {
        val wrong = Effects.all.mapNotNull { info ->
            val base = render(info)
            val byClock = base != render(info, Timing.CLOCK, 2f)
            val problem = when (XyPad.timing(info.id)) {
                Timing.CLOCK -> if (!byClock) "ignores its clock" else null
                Timing.STEPS -> when {
                    byClock -> "follows its clock (smoother), should be CLOCK"
                    base == render(info, Timing.STEPS, 2f) -> "doesn't change with more steps"
                    else -> null
                }
                Timing.BEAT -> if (base == render(info, Timing.BEAT, 2f)) "doesn't follow the beat" else null
                Timing.NONE -> when {
                    byClock -> "follows its clock, should be CLOCK"
                    base != render(info, Timing.BEAT, 2f) -> "follows the beat, should be BEAT"
                    else -> null
                }
            }
            problem?.let { "${info.id}: $it" }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun paletteShiftIsOfferedExactlyForEffectsItChanges() {
        val wrong = Effects.all.filter { info ->
            XyPad.affects(PadTarget.PALETTE, info.id) != (render(info) != render(info, offset = 0.25f))
        }.map { it.id }
        assertTrue("Palette shift marked wrongly for: $wrong", wrong.isEmpty())
    }

    @Test
    fun coloursIsOfferedExactlyForEffectsItChanges() {
        val wrong = Effects.all.filter { info ->
            val changes = render(info, palette = "red", recolour = true) != render(info, palette = "blue", recolour = true)
            XyPad.affects(PadTarget.COLOURS, info.id) != changes
        }.map { it.id }
        assertTrue("Colours marked wrongly for: $wrong", wrong.isEmpty())
    }
}
