package app.touchfx

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.engine.PixelMapper
import app.touchfx.engine.SceneLogic
import app.touchfx.model.EffectSettings
import app.touchfx.model.Mapping
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class HoldAndChaseTest {
    // ---------------------------------------------------------------- hold-to-play scenes

    private fun light(id: String, effect: String, enabled: Boolean = true) = VirtualConfig(
        id = id, name = id, segments = listOf(Segment(id, 0, 9)), isDevice = id, enabled = enabled,
        settings = EffectSettings(effectId = effect, configs = mapOf(effect to mapOf("blur" to 1f))),
    )

    private val strobe = Scene(
        "s", "Strobe",
        mapOf(
            "a" to SceneEntry(SceneAction.ACTIVATE, effectId = "real_strobe", config = emptyMap()),
            "b" to SceneEntry(SceneAction.ACTIVATE, effectId = "real_strobe", config = emptyMap()),
            "c" to SceneEntry(SceneAction.IGNORE),
        ),
        momentary = true,
    )

    @Test
    fun releasingAHeldSceneBringsBackTheLookFromBefore() {
        val before = listOf(light("a", "energy"), light("b", "fire", enabled = false), light("c", "rainbow"))
        val snap = SceneLogic.snapshot(strobe, before)
        assertEquals(setOf("a", "b"), snap.keys)            // IGNORE lights aren't touched
        val during = SceneLogic.apply(strobe, before)
        assertTrue(during.take(2).all { it.enabled && it.settings.effectId == "real_strobe" })
        assertEquals(before, SceneLogic.restore(snap, during))
    }

    @Test
    fun restoreKeepsChangesThatAreNotTheLook() {
        val before = listOf(light("a", "energy"))
        val snap = SceneLogic.snapshot(strobe, before)
        // While held, the light's segments were edited: that edit survives the release.
        val during = SceneLogic.apply(strobe, before).map { it.copy(segments = listOf(Segment("a", 0, 4))) }
        val after = SceneLogic.restore(snap, during).single()
        assertEquals("energy", after.settings.effectId)
        assertEquals(listOf(Segment("a", 0, 4)), after.segments)
    }

    @Test
    fun holdToPlayIsSavedWithTheScene() {
        assertTrue(Scene.fromJson(JSONObject(strobe.toJson().toString())).momentary)
        val old = strobe.toJson().apply { remove("momentary") }
        assertFalse("scenes saved before default to tap", Scene.fromJson(old).momentary)
    }

    // ---------------------------------------------------------------- segment index

    private fun group(vararg segs: Segment, grouping: Int = 1, rows: Int = 1, mapping: Mapping = Mapping.SPAN) =
        VirtualConfig(id = "g", name = "G", segments = segs.toList(), grouping = grouping, rows = rows, mapping = mapping)

    @Test
    fun segmentIndexFollowsSegmentsGroupingAndPanels() {
        assertArrayEquals(intArrayOf(0, 0, 0, 1, 1, 2), PixelMapper.segmentIndex(group(Segment("a", 0, 2), Segment("b", 0, 1), Segment("c", 0, 0))))
        // Grouping 2: effect pixel i drives physical 2i, 2i+1.
        assertArrayEquals(intArrayOf(0, 0, 1, 1), PixelMapper.segmentIndex(group(Segment("a", 0, 3), Segment("b", 0, 3), grouping = 2)))
        // Two 2x2 panels side by side: a 4x2 image, columns 0-1 panel 0, columns 2-3 panel 1.
        assertArrayEquals(intArrayOf(0, 0, 1, 1, 0, 0, 1, 1), PixelMapper.segmentIndex(group(Segment("a", 0, 3), Segment("b", 0, 3), rows = 2)))
        assertTrue(PixelMapper.segmentIndex(group(Segment("a", 0, 3), Segment("b", 0, 3), mapping = Mapping.COPY)).all { it == 0 })
    }

    // ---------------------------------------------------------------- Segment Chase

    private fun beat(count: Int, phase: Float = 0.1f, kick: Boolean = false, tempoBeat: Boolean = false) = AudioFrame(
        FloatArray(32), FloatArray(32), 0f, FloatArray(4), FloatArray(4), kick, false, 0f,
        phase, 0f, count, tempoBeat, 120f, 0f, false,
    )

    private fun chase(settings: Map<String, Any>, segments: IntArray?, parts: Int): Pair<RenderContext, app.touchfx.effects.Effect> {
        val info = Effects.info("segment_chase")
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, settings)
            palette = Palettes.get("rainbow")
            if (segments != null) { segmentOf = segments; segmentCount = parts }
        }
        return ctx to info.create()
    }

    /** Brightest part (by summed brightness) of [out] split by [segmentOf]. */
    private fun brightest(out: IntArray, segmentOf: IntArray, parts: Int): Int {
        val sum = IntArray(parts)
        out.forEachIndexed { i, c -> sum[segmentOf[i]] += (c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF) }
        return sum.indices.maxBy { sum[it] }
    }

    @Test
    fun chaseStepsThroughTheGroupsDevicesOnTheBeat() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        val (ctx, fx) = chase(emptyMap(), segs, 3)
        val out = IntArray(30)
        for (b in 0 until 6) {
            ctx.audio = beat(b)
            repeat(10) { fx.render(ctx, out) }
            assertEquals("beat $b", b % 3, brightest(out, segs, 3))
        }
    }

    @Test
    fun speedTwoPerBeatTakesTwoStepsPerBeat() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        val (ctx, fx) = chase(mapOf("speed" to "2 per beat"), segs, 3)
        val out = IntArray(30)
        ctx.audio = beat(0, phase = 0.1f); fx.render(ctx, out)
        assertEquals(0, brightest(out, segs, 3))
        ctx.audio = beat(0, phase = 0.6f); fx.render(ctx, out)
        assertEquals(1, brightest(out, segs, 3))
    }

    @Test
    fun fillLightsEveryDeviceBeforeStartingOver() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        val (ctx, fx) = chase(mapOf("mode" to "Fill", "trail" to 0f), segs, 3)
        val out = IntArray(30)
        for (b in 0..2) { ctx.audio = beat(b); repeat(5) { fx.render(ctx, out) } }
        assertTrue("all three lit after three beats", (0 until 3).all { k -> out[k * 10] != 0 })
        ctx.audio = beat(3); repeat(30) { fx.render(ctx, out) }
        assertTrue("starts over with only the first", out[0] != 0 && out[10] == 0 && out[20] == 0)
    }

    @Test
    fun singleStripIsSplitIntoParts() {
        val (ctx, fx) = chase(mapOf("parts" to 4, "trail" to 0f, "one_color" to true, "color" to 0xFF0000), null, 1)
        val out = IntArray(40)
        ctx.audio = beat(1)
        repeat(30) { fx.render(ctx, out) }
        assertTrue((10 until 20).all { out[it] == 0xFF0000 })
        assertTrue((0 until 10).all { out[it] == 0 } && (20 until 40).all { out[it] == 0 })
    }

    @Test
    fun kicksModeStepsOnDetectedKicks() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9)))
        val (ctx, fx) = chase(mapOf("step_on" to "Kicks", "trail" to 0f), segs, 2)
        val out = IntArray(20)
        ctx.audio = beat(0, kick = true); fx.render(ctx, out)
        ctx.audio = beat(0); repeat(20) { fx.render(ctx, out) }
        val first = brightest(out, segs, 2)
        ctx.audio = beat(0, kick = true); fx.render(ctx, out)
        ctx.audio = beat(0); repeat(20) { fx.render(ctx, out) }
        assertEquals((first + 1) % 2, brightest(out, segs, 2))
    }

    // ---------------------------------------------------------------- triggered cycles

    /** Runs frames at 60 fps from [ctx]'s current time; returns the brightest part after each. */
    private fun frames(ctx: RenderContext, fx: app.touchfx.effects.Effect, out: IntArray, segs: IntArray, count: Int,
                       first: AudioFrame, rest: AudioFrame): List<Int?> = List(count) { k ->
        ctx.audio = if (k == 0) first else rest
        fx.render(ctx, out)
        ctx.time += 1.0 / 60
        if (out.all { it == 0 }) null else brightest(out, segs, 3)
    }

    @Test
    fun aKickRunsOneFullCycleThroughTheDevicesThenWaits() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        // 120 BPM: a beat is 0.5 s; cycle 0.5 beats = 0.25 s = 15 frames, 5 frames per device.
        val (ctx, fx) = chase(mapOf("step_on" to "Kick runs a cycle", "cycle_length" to 0.5f, "trail" to 0f), segs, 3)
        val out = IntArray(30)
        val seen = frames(ctx, fx, out, segs, 40, beat(0, kick = true), beat(0))
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2), seen.take(15))
        // Even with no trail the last device takes ~0.2 s to fade out.
        assertTrue("dark again before the next kick: $seen", seen.drop(30).all { it == null })
        // The next kick runs the cycle again from the first device.
        val again = frames(ctx, fx, out, segs, 6, beat(0, kick = true), beat(0))
        assertEquals(listOf(0, 0, 0, 0, 0, 1), again)
    }

    @Test
    fun aKickDuringACycleStartsAFreshOne() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        val (ctx, fx) = chase(mapOf("step_on" to "Kick runs a cycle", "cycle_length" to 0.5f, "trail" to 0f), segs, 3)
        val out = IntArray(30)
        frames(ctx, fx, out, segs, 8, beat(0, kick = true), beat(0))      // now on device 2
        val restarted = frames(ctx, fx, out, segs, 3, beat(0, kick = true), beat(0))
        assertEquals(listOf(0, 0, 0), restarted)
    }

    @Test
    fun beatRunsACycleUsesTheTempoBeat() {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        val (ctx, fx) = chase(mapOf("step_on" to "Beat runs a cycle", "cycle_length" to 0.5f, "trail" to 0f), segs, 3)
        val out = IntArray(30)
        // A kick alone does nothing in this mode.
        assertTrue(frames(ctx, fx, out, segs, 5, beat(0, kick = true), beat(0)).all { it == null })
        val seen = frames(ctx, fx, out, segs, 12, beat(1, tempoBeat = true), beat(1))
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2), seen)
    }

    // ---------------------------------------------------------------- Random Flash, fixed rate

    /** Number of flashes (dark -> lit edges) over [seconds] at 60 fps. */
    private fun flashes(settings: Map<String, Any>, seconds: Double): Int {
        val info = Effects.info("random_flash")
        val ctx = RenderContext().apply { p = ParamValues(info.params, settings) }
        val fx = info.create()
        val out = IntArray(20)
        var lit = false
        var edges = 0
        repeat((seconds * 60).toInt()) {
            fx.render(ctx, out)
            val now = out.any { it != 0 }
            if (now && !lit) edges++
            lit = now
            ctx.time += 1.0 / 60
        }
        return edges
    }

    @Test
    fun fixedRateFlashesExactlyThatOftenWithGapsBetween() {
        val full = mapOf("timing" to "Fixed rate", "hit_relative_size" to 100)
        assertEquals(12, flashes(full + mapOf("strobe_rate" to 4f, "hit_duration" to 0.05f), 3.0))
        // A long flash duration is capped at half the period, so it still strobes.
        assertEquals(12, flashes(full + mapOf("strobe_rate" to 4f, "hit_duration" to 2f), 3.0))
        assertEquals(30, flashes(full + mapOf("strobe_rate" to 10f, "hit_duration" to 0.5f), 3.0))
    }

    // ---------------------------------------------------------------- speed in cycle modes

    /** 120 BPM tempo grid at frame [f] (30 frames per beat). */
    private fun grid(f: Int, kick: Boolean = false) = beat(f / 30, phase = (f % 30) / 30f, kick = kick)

    private fun runGrid(settings: Map<String, Any>, frames: Int, kicksAt: Set<Int> = emptySet()): List<Int?> {
        val segs = PixelMapper.segmentIndex(group(Segment("a", 0, 9), Segment("b", 0, 9), Segment("c", 0, 9)))
        // One colour, so "brightest" compares light levels, not how bright each gradient colour is.
        val (ctx, fx) = chase(settings + mapOf("trail" to 0f, "one_color" to true), segs, 3)
        val out = IntArray(30)
        return List(frames) { f ->
            ctx.audio = grid(f, kick = f in kicksAt)
            fx.render(ctx, out)
            ctx.time += 1.0 / 60
            if (out.all { it == 0 }) null else brightest(out, segs, 3)
        }
    }

    @Test
    fun beatCycleAtTwoPerBeatRunsTwoCyclesEachBeat() {
        val seen = runGrid(mapOf("step_on" to "Beat runs a cycle", "speed" to "2 per beat", "cycle_length" to 0.5f), 90)
        // A cycle every half beat (15 frames), 5 frames per device.
        val one = List(5) { 0 } + List(5) { 1 } + List(5) { 2 }
        assertEquals(one + one, seen.subList(30, 60))
    }

    @Test
    fun beatCycleEveryTwoBeatsWaitsInBetween() {
        val seen = runGrid(mapOf("step_on" to "Beat runs a cycle", "speed" to "Every 2 beats", "cycle_length" to 0.5f), 180)
        val starts = seen.indices.filter { it > 0 && seen[it] == 0 && seen[it - 1] != 0 }
        assertEquals("a cycle every 60 frames: $starts", listOf(60, 120), starts)
    }

    @Test
    fun kickAtFourPerBeatRunsABurstOfFourCycles() {
        // Cycle length 1 beat is capped at the quarter-beat between cycles.
        val seen = runGrid(mapOf("step_on" to "Kick runs a cycle", "speed" to "4 per beat", "cycle_length" to 1f), 60, kicksAt = setOf(0))
        val starts = seen.indices.filter { seen[it] == 0 && (it == 0 || seen[it - 1] != 0) }
        assertEquals("four cycles within the beat: $starts", 4, starts.size)
        assertTrue(starts.all { it < 30 })
        assertTrue("then it waits: ${seen.drop(40)}", seen.drop(40).all { it == null })
    }

    @Test
    fun kickEveryTwoBeatsOnlyEverySecondKickStartsACycle() {
        val seen = runGrid(mapOf("step_on" to "Kick runs a cycle", "speed" to "Every 2 beats", "cycle_length" to 0.5f), 120, kicksAt = setOf(0, 30, 60, 90))
        val starts = seen.indices.filter { seen[it] == 0 && (it == 0 || seen[it - 1] != 0) }
        assertEquals(listOf(30, 90), starts)
    }
}
