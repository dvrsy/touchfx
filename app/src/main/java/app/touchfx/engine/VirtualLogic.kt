package app.touchfx.engine

import org.json.JSONArray
import app.touchfx.model.DeviceConfig
import app.touchfx.model.EffectSettings
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

/** Rules for devices and virtuals, kept free of Android so they can be unit tested. */
object VirtualLogic {

    /** The auto-virtual for [device]: same id, one segment covering the whole device. */
    fun autoVirtual(device: DeviceConfig, settings: EffectSettings = EffectSettings(), enabled: Boolean = true) =
        VirtualConfig(
            id = device.id,
            name = device.name,
            segments = listOf(Segment(device.id, 0, device.ledCount - 1)),
            isDevice = device.id,
            enabled = enabled,
            settings = settings,
        )

    /**
     * Brings [virtuals] in line with [devices]:
     * - every device has an auto-virtual that covers it and carries its name;
     * - auto-virtuals of removed devices are dropped;
     * - group segments on removed devices are dropped, and segments are clipped to the LED count.
     * New auto-virtuals go after the existing auto-virtuals and before the groups, so groups
     * (rendered later) win where they overlap a device's own virtual.
     */
    fun sync(devices: List<DeviceConfig>, virtuals: List<VirtualConfig>): List<VirtualConfig> {
        val byId = devices.associateBy { it.id }
        val kept = virtuals.mapNotNull { v ->
            val owner = v.isDevice
            if (owner != null) {
                val d = byId[owner] ?: return@mapNotNull null
                v.copy(id = d.id, name = d.name, segments = listOf(Segment(d.id, 0, d.ledCount - 1)))
            } else {
                v.copy(segments = v.segments.mapNotNull { s -> clip(s, byId[s.deviceId]) })
            }
        }
        val have = kept.mapNotNullTo(HashSet()) { it.isDevice }
        val missing = devices.filter { it.id !in have }.map { autoVirtual(it) }
        if (missing.isEmpty()) return kept
        val firstGroup = kept.indexOfFirst { it.isGroup }.let { if (it < 0) kept.size else it }
        return kept.subList(0, firstGroup) + missing + kept.subList(firstGroup, kept.size)
    }

    private fun clip(s: Segment, device: DeviceConfig?): Segment? {
        if (device == null) return null
        val last = device.ledCount - 1
        if (s.start > last) return null
        return s.copy(start = s.start.coerceAtLeast(0), end = s.end.coerceIn(s.start.coerceAtLeast(0), last))
    }

    /**
     * Virtuals that can't be on at the same time as [v], following LedFx:
     * - a device's own virtual conflicts with every group that uses that device;
     * - a group conflicts with the own virtuals of its devices, and with groups whose pixels
     *   overlap its own.
     */
    fun conflicts(v: VirtualConfig, all: List<VirtualConfig>): List<VirtualConfig> = all.filter { o ->
        if (o.id == v.id) return@filter false
        val own = v.isDevice
        when {
            own != null -> o.isGroup && o.segments.any { it.deviceId == own }
            o.isDevice != null -> v.segments.any { it.deviceId == o.isDevice }
            else -> v.segments.any { a -> o.segments.any { b -> overlap(a, b) } }
        }
    }

    /** Turns [id] on and switches off everything that conflicts with it. */
    fun enableExclusive(virtuals: List<VirtualConfig>, id: String): List<VirtualConfig> {
        val v = virtuals.firstOrNull { it.id == id } ?: return virtuals
        val off = conflicts(v, virtuals).mapTo(HashSet()) { it.id }
        return virtuals.map {
            when (it.id) {
                id -> it.copy(enabled = true)
                in off -> it.copy(enabled = false)
                else -> it
            }
        }
    }

    /** True if two segments drive at least one common pixel. */
    fun overlap(a: Segment, b: Segment) = a.deviceId == b.deviceId && a.start <= b.end && b.start <= a.end

    /** Pairs of segment indices inside one virtual that drive the same pixels. */
    fun selfOverlaps(v: VirtualConfig): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (i in v.segments.indices) for (j in i + 1 until v.segments.size) {
            if (overlap(v.segments[i], v.segments[j])) out += i to j
        }
        return out
    }
}
