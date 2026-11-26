package app.touchfx

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.engine.PixelMapper
import app.touchfx.engine.SceneLogic
import app.touchfx.engine.VirtualLogic
import app.touchfx.model.DeviceConfig
import app.touchfx.model.DeviceKind
import app.touchfx.model.Mapping
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class VirtualsTest {
    private fun group(vararg segs: Segment, mapping: Mapping = Mapping.SPAN, grouping: Int = 1) =
        VirtualConfig(id = "g", name = "Group", segments = segs.toList(), mapping = mapping, grouping = grouping)

    private fun buffers(vararg sizes: Pair<String, Int>) = sizes.associate { (id, n) -> id to IntArray(n) }

    /** Pixels 1..n, so every output value says which effect pixel it came from. */
    private fun ramp(n: Int) = IntArray(n) { it + 1 }

    // ---------------------------------------------------------------- PixelMapper

    @Test
    fun spanRunsAcrossDevicesInSegmentOrder() {
        val v = group(Segment("a", 0, 2), Segment("b", 0, 1))
        assertEquals(5, PixelMapper.pixelCount(v))
        val bufs = buffers("a" to 3, "b" to 2)
        PixelMapper.map(v, ramp(5), bufs)
        assertArrayEquals(intArrayOf(1, 2, 3), bufs["a"])
        assertArrayEquals(intArrayOf(4, 5), bufs["b"])
    }

    @Test
    fun invertReversesOnlyItsSegment() {
        val v = group(Segment("a", 0, 2), Segment("b", 0, 2, invert = true))
        val bufs = buffers("a" to 3, "b" to 3)
        PixelMapper.map(v, ramp(6), bufs)
        assertArrayEquals(intArrayOf(1, 2, 3), bufs["a"])
        assertArrayEquals(intArrayOf(6, 5, 4), bufs["b"])
    }

    @Test
    fun partialRangesLeaveTheRestOfTheDeviceAlone() {
        val v = group(Segment("a", 2, 4))
        val bufs = buffers("a" to 7)
        PixelMapper.map(v, ramp(3), bufs)
        assertArrayEquals(intArrayOf(0, 0, 1, 2, 3, 0, 0), bufs["a"])
    }

    @Test
    fun copyShowsTheWholeEffectOnEverySegment() {
        val v = group(Segment("a", 0, 4), Segment("b", 0, 2), mapping = Mapping.COPY)
        assertEquals(5, PixelMapper.pixelCount(v))
        val bufs = buffers("a" to 5, "b" to 3)
        PixelMapper.map(v, ramp(5), bufs)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5), bufs["a"])
        assertArrayEquals("first, middle, last", intArrayOf(1, 3, 5), bufs["b"])
    }

    @Test
    fun groupingRepeatsPixelsAndTrimsTheLastGroup() {
        val v = group(Segment("a", 0, 6), grouping = 3)
        assertEquals(7, PixelMapper.pixelCount(v))
        assertEquals(3, PixelMapper.effectPixelCount(v))
        val out = IntArray(7)
        PixelMapper.expandGrouping(intArrayOf(1, 2, 3), 3, out)
        assertArrayEquals(intArrayOf(1, 1, 1, 2, 2, 2, 3), out)
    }

    @Test
    fun laterVirtualWinsWhereTheyOverlap() {
        val bufs = buffers("a" to 4)
        PixelMapper.map(group(Segment("a", 0, 3)), intArrayOf(1, 1, 1, 1), bufs)
        PixelMapper.map(group(Segment("a", 1, 2)), intArrayOf(9, 9), bufs)
        assertArrayEquals(intArrayOf(1, 9, 9, 1), bufs["a"])
    }

    @Test
    fun segmentsBeyondTheDeviceOrOnMissingDevicesAreSkipped() {
        val bufs = buffers("a" to 3)
        PixelMapper.map(group(Segment("a", 1, 5), Segment("gone", 0, 3)), ramp(9), bufs)
        assertArrayEquals(intArrayOf(0, 1, 2), bufs["a"])
    }

    // ---------------------------------------------------------------- VirtualLogic

    private val strip = DeviceConfig(id = "s", name = "Strip", kind = DeviceKind.WLED, host = "h", ledCount = 10)
    private val desk = DeviceConfig(id = "d", name = "Desk", kind = DeviceKind.WLED, host = "h2", ledCount = 4)

    @Test
    fun syncCreatesAutoVirtualsWithTheDeviceId() {
        val v = VirtualLogic.sync(listOf(strip, desk), emptyList())
        assertEquals(listOf("s", "d"), v.map { it.id })
        assertEquals(listOf(Segment("s", 0, 9)), v[0].segments)
        assertEquals("s", v[0].isDevice)
    }

    @Test
    fun syncFollowsLedCountAndNameChanges() {
        val v = VirtualLogic.sync(listOf(strip), emptyList())
        val after = VirtualLogic.sync(listOf(strip.copy(name = "Shelf", ledCount = 20)), v)
        assertEquals("Shelf", after.single().name)
        assertEquals(Segment("s", 0, 19), after.single().segments.single())
    }

    @Test
    fun syncDropsRemovedDevicesAndClipsGroups() {
        val g = group(Segment("s", 5, 9), Segment("d", 0, 3), Segment("s", 20, 25))
        val v = VirtualLogic.sync(listOf(strip.copy(ledCount = 8)), VirtualLogic.sync(listOf(strip, desk), listOf(g)))
        assertEquals(listOf("s", "g"), v.map { it.id })
        assertEquals(listOf(Segment("s", 5, 7)), v.last().segments)
    }

    @Test
    fun newAutoVirtualsGoBeforeGroupsSoGroupsWinOverlaps() {
        val v = VirtualLogic.sync(listOf(strip, desk), listOf(VirtualLogic.autoVirtual(strip), group(Segment("s", 0, 3))))
        assertEquals(listOf("s", "d", "g"), v.map { it.id })
    }

    // ---------------------------------------------------------------- exclusivity (LedFx rules)

    private val all = listOf(
        VirtualLogic.autoVirtual(strip),
        VirtualLogic.autoVirtual(desk),
        VirtualConfig(id = "left", name = "Left", segments = listOf(Segment("s", 0, 4))),
        VirtualConfig(id = "right", name = "Right", segments = listOf(Segment("s", 5, 9))),
        VirtualConfig(id = "both", name = "Both", segments = listOf(Segment("s", 3, 6), Segment("d", 0, 3))),
    )
    private fun ids(list: List<VirtualConfig>) = list.map { it.id }.toSet()

    @Test
    fun groupConflictsWithItsDevicesOwnLightsAndOverlappingGroups() {
        val left = all.first { it.id == "left" }
        assertEquals(setOf("s", "both"), ids(VirtualLogic.conflicts(left, all)))
        // Left and Right share a device but no pixels: they can run together.
        assertFalse("right" in ids(VirtualLogic.conflicts(left, all)))
    }

    @Test
    fun deviceOwnLightConflictsWithEveryGroupOnIt() {
        assertEquals(setOf("left", "right", "both"), ids(VirtualLogic.conflicts(all[0], all)))
        assertEquals(setOf("both"), ids(VirtualLogic.conflicts(all[1], all)))
    }

    @Test
    fun enablingSwitchesOffConflictsAndLeavesTheRest() {
        val after = VirtualLogic.enableExclusive(all, "left")
        val on = after.filter { it.enabled }.map { it.id }.toSet()
        assertEquals(setOf("d", "left", "right"), on)
    }

    @Test
    fun selfOverlapsFindSegmentsSharingPixels() {
        val v = group(Segment("s", 0, 4), Segment("d", 0, 3), Segment("s", 4, 6), Segment("s", 7, 9))
        assertEquals(listOf(0 to 2), VirtualLogic.selfOverlaps(v))
    }

    @Test
    fun virtualJsonRoundTrip() {
        val v = group(Segment("s", 2, 5, invert = true), Segment("d", 0, 3), mapping = Mapping.COPY, grouping = 2)
        assertEquals(v, VirtualConfig.fromJson(JSONObject(v.toJson().toString())))
        val auto = VirtualLogic.autoVirtual(strip)
        assertEquals(auto, VirtualConfig.fromJson(JSONObject(auto.toJson().toString())))
    }
}
