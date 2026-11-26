package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioFrame
import kotlin.math.PI
import kotlin.math.sin

class TapTempoTest {
    private val frameNanos = 1_000_000_000L / 60

    /** Runs [frames] frames in tap mode; [tapAt] holds the frame numbers where the user taps. */
    private fun runTapped(analyzer: AudioAnalyzer, frames: Int, tapAt: Set<Int>, start: Int = 0): List<AudioFrame> =
        (start until start + frames).map { f ->
            val now = f * frameNanos
            if (f in tapAt) analyzer.tap(now)
            analyzer.analyze(now)
        }

    @Test
    fun tapsSetTheTempoAndItKeepsGoingOnItsOwn() {
        val a = AudioAnalyzer().apply { tapMode = true; tapSimulate = false }
        // 100 BPM = a tap every 0.6 s = every 36 frames.
        val taps = (0 until 5).map { 30 + it * 36 }.toSet()
        val frames = runTapped(a, 600, taps)
        assertEquals(100f, a.tapBpm, 0.5f)
        // After the last tap (frame 174) beats keep coming every ~36 frames with no taps.
        val beatsAfter = frames.withIndex().filter { it.index > 180 && it.value.tempoBeat }.map { it.index }
        assertTrue("beats after tapping stopped: $beatsAfter", beatsAfter.size >= 10)
        beatsAfter.zipWithNext { x, y -> assertTrue("gap ${y - x}", y - x in 35..37) }
        // Each tapped beat also counts as a kick; there is no sound.
        assertTrue(frames.all { it.beat == it.tempoBeat && !it.silent && it.bands.all { b -> b == 0f } })
    }

    @Test
    fun aSingleTapAfterAPauseResyncsTheBarWithoutChangingTheTempo() {
        val a = AudioAnalyzer().apply { tapMode = true }
        runTapped(a, 400, (0 until 4).map { 10 + it * 30 }.toSet())   // 120 BPM
        assertEquals(120f, a.tapBpm, 0.5f)
        // Much later (> 2 s gap), one tap somewhere off the grid.
        val after = runTapped(a, 60, setOf(400 + 7), start = 400)
        assertEquals("tempo unchanged", 120f, a.tapBpm, 0.5f)
        val tapped = after[7]
        assertTrue(tapped.tempoBeat)
        assertEquals("first beat of a bar", 0, tapped.beatCount % 4)
        assertEquals(0f, tapped.barPhase, 0.01f)
    }

    @Test
    fun tapModeIgnoresSoundAndOtherSourcesIgnoreTaps() {
        val a = AudioAnalyzer().apply { tapMode = true; tapSimulate = false }
        val loud = FloatArray(735) { (0.5 * sin(2 * PI * 80 * it / 44100)).toFloat() }
        repeat(30) { a.push(loud, loud.size); a.analyze(it * frameNanos) }
        val f = a.analyze(31 * frameNanos)
        assertTrue(f.bands.all { it == 0f } && f.volume == 0f && f.level == 0f)
        assertEquals(0f, f.lows, 0f)
    }

    @Test
    fun tapTempoStaysWithinLimits() {
        val a = AudioAnalyzer()
        a.tapBpm = 1000f
        assertEquals(AudioAnalyzer.TAP_MAX_BPM, a.tapBpm, 0.01f)
        a.tapBpm = 1f
        assertEquals(AudioAnalyzer.TAP_MIN_BPM, a.tapBpm, 0.01f)
    }

    // ---------------------------------------------------------------- simulated sound

    @Test
    fun simulatedSoundFollowsTheTappedBeatLikeADrumPattern() {
        val a = AudioAnalyzer().apply { tapMode = true; tapSimulate = true; tapBpm = 120f }
        // 120 BPM = a beat every 30 frames; tap once to anchor bar and beat.
        val frames = runTapped(a, 240, setOf(0))
        val beats = frames.withIndex().filter { it.value.tempoBeat }.map { it.index }
        assertTrue("beats every 30 frames: $beats", beats.zipWithNext().all { (x, y) -> y - x in 29..31 })
        val b0 = beats[1]
        // Kick: bass is strong right after a beat and has dropped by the middle of it.
        assertTrue(frames[b0 + 2].lows > 0.5f)
        assertTrue(frames[b0 + 2].lows > frames[b0 + 14].lows * 1.5f)
        // Snare on beats 2 and 4: mids are stronger after odd beats than after even ones.
        val oddBeat = beats.first { frames[it].beatCount % 2 == 1 }
        val evenBeat = beats.first { it > 0 && frames[it].beatCount % 2 == 0 }
        assertTrue(frames[oddBeat + 2].mids > frames[evenBeat + 2].mids + 0.2f)
        // Hi-hat every half beat: highs peak again half-way through the beat.
        assertTrue(frames[b0 + 15 + 1].highs > frames[b0 + 12].highs)
        assertTrue("onsets on hi-hats", frames.count { it.onset } >= 12)
        assertTrue(frames.all { !it.silent && it.volume > 0.2f && it.bands.all { v -> v in 0f..1f } })
    }

    @Test
    fun withoutSimulationTheBandsStayDark() {
        val a = AudioAnalyzer().apply { tapMode = true; tapSimulate = false }
        val frames = runTapped(a, 120, setOf(0, 30, 60))
        assertTrue(frames.all { f -> f.bands.all { it == 0f } && f.volume == 0f && !f.onset })
    }
}
