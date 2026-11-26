package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effects
import app.touchfx.effects.LedFxPresets
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.effects.Rgb
import app.touchfx.effects.parseColor
import app.touchfx.model.EffectSettings
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.math.sin

class EngineLogicTest {
    private val rate = AudioAnalyzer.SAMPLE_RATE
    private val hop = rate / 60

    /** Clock per analyzer, so consecutive run() calls continue in time. */
    private val clocks = HashMap<AudioAnalyzer, Long>()

    private fun run(analyzer: AudioAnalyzer, seconds: Double, signal: (Int) -> Float): List<AudioFrame> {
        val frames = ArrayList<AudioFrame>()
        val buf = FloatArray(hop)
        var t = 0
        var nanos = clocks[analyzer] ?: 0L
        while (t < seconds * rate) {
            for (i in 0 until hop) buf[i] = signal(t + i)
            analyzer.push(buf, hop)
            t += hop
            nanos += 1_000_000_000L / 60
            frames += analyzer.analyze(nanos)
        }
        clocks[analyzer] = nanos
        return frames
    }

    private fun kick120(t: Int): Float {
        val beatEvery = rate / 2
        val inBeat = t % beatEvery
        val kick = if (inBeat < rate / 10)
            (sin(2 * PI * 60 * inBeat / rate) * (1.0 - inBeat / (rate / 10.0))).toFloat() * 0.8f else 0f
        return kick + 0.02f * sin(2 * PI * 3000 * t / rate).toFloat()
    }

    @Test
    fun bassToneLightsLowBandsNotHigh() {
        val f = run(AudioAnalyzer(), 1.0) { 0.5f * sin(2 * PI * 80 * it / rate).toFloat() }.last()
        assertTrue("lows ${f.lows}", f.lows > 0.3f)
        assertTrue("lows ${f.lows} > highs ${f.highs}", f.lows > f.highs * 3)
        assertTrue(!f.silent)
    }

    @Test
    fun silenceIsDark() {
        val f = run(AudioAnalyzer(), 1.0) { 0f }.last()
        assertTrue(f.silent)
        assertEquals(0f, f.volume, 0.01f)
    }

    // ---------------------------------------------------------------- silence threshold

    /** Sine at [dbfs] RMS level. */
    private fun sine(dbfs: Double, hz: Double = 440.0): (Int) -> Float {
        val amp = (10.0.pow(dbfs / 20) * sqrt(2.0)).toFloat()
        return { t -> amp * sin(2 * PI * hz * t / rate).toFloat() }
    }

    @Test
    fun levelUsesLedFxScale() {
        assertEquals(0.6f, AudioAnalyzer.levelOf(0.01f), 0.001f)      // -40 dBFS
        assertEquals(-66f, AudioAnalyzer.levelToDb(0.34f), 0.01f)
        assertEquals(0f, AudioAnalyzer.levelOf(0f), 0f)
    }

    @Test
    fun quietMusicAboveThresholdPasses() {
        val f = run(AudioAnalyzer(), 1.0, sine(-40.0)).last()
        assertTrue(!f.silent)
        assertEquals(0.6f, f.level, 0.02f)
    }

    @Test
    fun noiseBelowThresholdIsGated() {
        val rnd = Random(1)
        val a = (10.0.pow(-75.0 / 20) * sqrt(3.0)).toFloat() // uniform noise at -75 dBFS RMS
        val frames = run(AudioAnalyzer(), 2.0) { (rnd.nextFloat() * 2 - 1) * a }
        assertTrue(frames.all { it.silent })
        assertTrue(frames.none { it.beat || it.onset })
        assertTrue(frames.all { f -> f.bands.all { it == 0f } })
    }

    @Test
    fun shortDipKeepsGateOpenLongDipClosesIt() {
        val loud = sine(-40.0)
        fun withDip(dipSec: Double): List<AudioFrame> {
            val start = rate; val end = rate + (dipSec * rate).toInt()
            return run(AudioAnalyzer(), 2.0) { t -> if (t in start until end) loud(t) * 0.0001f else loud(t) }
        }
        val afterOpen = (0.2 * 60).toInt()
        assertTrue("100 ms dip must not close the gate", withDip(0.1).drop(afterOpen).none { it.silent })
        assertTrue("400 ms dip must close the gate", withDip(0.4).drop(afterOpen).any { it.silent })
    }

    @Test
    fun zeroThresholdLetsQuietSignalsThrough() {
        val analyzer = AudioAnalyzer().apply { minVolume = 0f }
        assertTrue(!run(analyzer, 1.0, sine(-85.0)).last().silent)
        assertTrue(run(AudioAnalyzer(), 1.0, sine(-85.0)).last().silent)
    }

    @Test
    fun tempoGridPausesWhileSilentAndResumesWithTheMusic() {
        val analyzer = AudioAnalyzer()
        val music = run(analyzer, 4.0, ::kick120)
        assertTrue("tempo beats while music plays", music.drop(60).count { it.tempoBeat } >= 4)
        val silence = run(analyzer, 3.0) { 0f }
        // After the 300 ms hold the gate is closed: no tempo beats, beat count and phase frozen.
        val quiet = silence.drop(30)
        assertTrue(quiet.all { it.silent })
        assertTrue("no tempo beats in silence", quiet.none { it.tempoBeat })
        assertEquals(1, quiet.map { it.beatCount }.distinct().size)
        assertEquals(1, quiet.map { it.beatPhase }.distinct().size)
        val back = run(analyzer, 3.0, ::kick120)
        assertTrue("tempo beats again when the music returns", back.count { it.tempoBeat } >= 4)
    }

    @Test
    fun kickDrumAt120BpmIsDetectedAsBeats() {
        val beats = run(AudioAnalyzer(), 6.0, ::kick120).drop(120).count { it.beat }
        assertTrue("beats=$beats", beats in 7..12)
    }

    @Test
    fun tempoTrackerLocksNear120Bpm() {
        val frames = run(AudioAnalyzer(), 10.0, ::kick120)
        val bpm = frames.last().bpm
        assertTrue("bpm=$bpm", bpm in 110f..130f)
        val tempoBeats = frames.drop(360).count { it.tempoBeat } // last 4 s
        assertTrue("tempoBeats=$tempoBeats", tempoBeats in 7..9)
    }

    @Test
    fun pitchOfA440IsMidi69() {
        val f = run(AudioAnalyzer(), 1.0) { 0.4f * sin(2 * PI * 440 * it / rate).toFloat() }.last()
        assertEquals(69f, f.pitchMidi, 0.6f)
    }

    private fun renderAll(settingsFor: (String) -> Map<String, Any>) {
        val ctx = RenderContext()
        val analyzer = AudioAnalyzer()
        val frames = run(analyzer, 1.0, ::kick120)
        for (info in Effects.all) {
            for (n in listOf(1, 2, 7, 60, 300)) {
                val effect = info.create()
                val out = IntArray(n)
                ctx.p = ParamValues(info.params, settingsFor(info.id))
                ctx.palette = Palettes.get("rainbow")
                frames.forEachIndexed { k, f ->
                    ctx.audio = f
                    ctx.time = k / 60.0
                    ctx.gradientOffset = (k % 10) / 10f
                    effect.render(ctx, out)
                    assertTrue("${info.id} n=$n produced invalid colour", out.all { it in 0..0xFFFFFF })
                }
            }
        }
    }

    @Test
    fun everyEffectRendersAtAnyLength() = renderAll { emptyMap() }

    @Test
    fun everyLedFxPresetTargetsAnEffectAndRenders() {
        assertTrue(LedFxPresets.all.size > 80)
        for (p in LedFxPresets.all) assertTrue("unknown effect ${p.effectId}", Effects.exists(p.effectId))
        val byEffect = LedFxPresets.all.associateBy { it.effectId }
        renderAll { id -> byEffect[id]?.values ?: emptyMap() }
    }

    @Test
    fun presetApplySwitchesEffectAndKeepsOtherConfigs() {
        val preset = LedFxPresets.all.first { it.effectId == "scroll" }
        val before = EffectSettings(effectId = "energy").withValue("sensitivity", 0.9f)
        val after = preset.applyTo(before)
        assertEquals("scroll", after.effectId)
        assertEquals(0.9f, after.config("energy")["sensitivity"])
        assertEquals(preset.values, after.config("scroll"))
    }

    @Test
    fun colourHelpers() {
        assertEquals(0xFFFFFF, Rgb.add(0xFF8080, 0x80FFFF))
        assertEquals(0x7F7F7F, Rgb.scale(0xFFFFFF, 0.5f))
        assertEquals(0xFF0000, Rgb.hsv(0f, 1f, 1f))
        assertEquals(0x00C78C, parseColor("rgb(0, 199, 140)"))
        assertEquals(0xFFFFFF, parseColor("white"))
        assertEquals(0x4AA3FF, parseColor("#4AA3FF"))
    }
}
