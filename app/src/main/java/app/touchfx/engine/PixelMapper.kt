package app.touchfx.engine

import app.touchfx.model.Mapping
import app.touchfx.model.PanelLayout
import app.touchfx.model.VirtualConfig

/** Turns a virtual's pixels into device pixels: grouping, span/copy mapping and segment invert. */
object PixelMapper {

    /** Physical pixels a virtual drives: the sum of its segments (SPAN) or the longest one (COPY). */
    fun pixelCount(v: VirtualConfig): Int = when (v.mapping) {
        Mapping.SPAN -> v.segments.sumOf { it.length }
        Mapping.COPY -> v.segments.maxOfOrNull { it.length } ?: 0
    }

    /** Pixels the effect renders: one per group of [VirtualConfig.grouping] physical pixels. */
    fun effectPixelCount(v: VirtualConfig): Int {
        val n = pixelCount(v)
        val g = v.grouping.coerceAtLeast(1)
        return (n + g - 1) / g
    }

    /** Repeats each pixel of [src] [group] times into [out], trimming the last group to fit. */
    fun expandGrouping(src: IntArray, group: Int, out: IntArray) {
        if (group <= 1) {
            src.copyInto(out, 0, 0, minOf(src.size, out.size))
            return
        }
        for (i in out.indices) out[i] = src[(i / group).coerceAtMost(src.size - 1)]
    }

    /**
     * Matrix wiring: copies [src] (row-major, [rows] rows of `size / rows` pixels) to [dst],
     * reversing every second row when [serpentine] is set. Pixels after the last full row are
     * copied as they are.
     */
    fun wire(src: IntArray, rows: Int, serpentine: Boolean, dst: IntArray) {
        src.copyInto(dst, 0, 0, minOf(src.size, dst.size))
        if (!serpentine || rows <= 1) return
        val w = src.size / rows
        if (w <= 1) return
        for (r in 1 until rows step 2) {
            val base = r * w
            for (k in 0 until w) dst[base + k] = src[base + w - 1 - k]
        }
    }

    /**
     * For each effect pixel of [v], the index of the segment it lands on, so effects can treat
     * segments as parts (e.g. chase device 1, 2, 3). Strips: by the running segment lengths,
     * allowing for grouping. Side-by-side panels: by the column. COPY (every segment shows the
     * whole effect) and single-segment lights: all 0.
     */
    fun segmentIndex(v: VirtualConfig): IntArray {
        val n = effectPixelCount(v)
        val out = IntArray(n)
        if (v.segments.size <= 1 || v.mapping == Mapping.COPY || n == 0) return out
        if (usesPanels(v)) {
            val width = n / v.rows
            if (width == 0) return out
            val panelOfColumn = IntArray(width) { v.segments.size - 1 }
            var x0 = 0
            v.segments.forEachIndexed { k, seg ->
                val pw = seg.length / v.rows
                for (x in x0 until minOf(width, x0 + pw)) panelOfColumn[x] = k
                x0 += pw
            }
            for (i in 0 until n) out[i] = panelOfColumn[i % width]
            return out
        }
        val g = v.grouping.coerceAtLeast(1)
        var seg = 0
        var segEnd = v.segments[0].length
        for (i in 0 until n) {
            val physical = i * g
            while (physical >= segEnd && seg < v.segments.size - 1) {
                seg++
                segEnd += v.segments[seg].length
            }
            out[i] = seg
        }
        return out
    }

    /** True if [v] is a matrix whose segments are panels placed side by side. */
    fun usesPanels(v: VirtualConfig) =
        v.rows > 1 && v.mapping == Mapping.SPAN && v.panelLayout == PanelLayout.SIDE_BY_SIDE

    /**
     * Side-by-side panels: [image] is the matrix in row-major order (`pixels / rows` wide). Each
     * segment is a panel `rows` tall and `length / rows` wide, placed left to right in segment
     * order. Within a panel, pixels are row-major; [VirtualConfig.serpentine] reverses every
     * second row of each panel, and segment invert turns the panel by 180°. Spare pixels at the
     * end of a segment (length not divisible by rows) stay black.
     */
    fun mapPanels(v: VirtualConfig, image: IntArray, buffers: Map<String, IntArray>) {
        val rows = v.rows
        val width = image.size / rows
        if (width == 0) return
        var x0 = 0
        for (seg in v.segments) {
            val pw = seg.length / rows
            val buf = buffers[seg.deviceId]
            if (buf != null && pw > 0) {
                for (y in 0 until rows) for (x in 0 until pw) {
                    val ix = x0 + x
                    if (ix >= width) break
                    val local = y * pw + if (v.serpentine && y % 2 == 1) pw - 1 - x else x
                    val idx = if (seg.invert) pw * rows - 1 - local else local
                    val dst = seg.start + idx
                    if (dst in buf.indices) buf[dst] = image[y * width + ix]
                }
            }
            x0 += pw
        }
    }

    /**
     * Writes [pixels] (length [pixelCount]) into the device buffers. Pixels outside a device's
     * buffer are skipped, so a stale segment can't crash the render loop.
     * In COPY mode each segment shows the whole effect, scaled to its own length.
     */
    fun map(v: VirtualConfig, pixels: IntArray, buffers: Map<String, IntArray>) {
        if (pixels.isEmpty()) return
        var offset = 0
        for (seg in v.segments) {
            val buf = buffers[seg.deviceId]
            val len = seg.length
            if (buf != null && len > 0) {
                for (k in 0 until len) {
                    val src = when (v.mapping) {
                        Mapping.SPAN -> offset + k
                        Mapping.COPY -> if (len == 1) 0 else ((k.toLong() * (pixels.size - 1) + (len - 1) / 2) / (len - 1)).toInt()
                    }
                    if (src >= pixels.size) break
                    val dst = if (seg.invert) seg.end - k else seg.start + k
                    if (dst in buf.indices) buf[dst] = pixels[src]
                }
            }
            if (v.mapping == Mapping.SPAN) offset += len
        }
    }
}
