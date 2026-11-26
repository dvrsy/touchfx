package app.touchfx.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// ====================================================================== small drawing helpers

private class Pt(val x: Float, val y: Float)

private fun lerpPt(a: Pt, b: Pt, t: Float) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** PIL-style rectangle: both corners inclusive, clipped to the image. */
private fun fillRect(img: IntArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
    for (y in max(0, min(y0, y1))..min(h - 1, max(y0, y1))) for (x in max(0, min(x0, x1))..min(w - 1, max(x0, x1))) img[y * w + x] = c
}

/** Even-odd point-in-polygon test. */
private fun inside(pts: List<Pt>, x: Float, y: Float): Boolean {
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        val a = pts[i]; val b = pts[j]
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Filled polygon with its outline, like PIL's polygon(fill=...); [set] receives each pixel. */
private fun fillPolygon(w: Int, h: Int, pts: List<Pt>, set: (Int) -> Unit) {
    val minX = max(0, pts.minOf { it.x }.toInt()); val maxX = min(w - 1, pts.maxOf { it.x }.toInt() + 1)
    val minY = max(0, pts.minOf { it.y }.toInt()); val maxY = min(h - 1, pts.maxOf { it.y }.toInt() + 1)
    for (y in minY..maxY) for (x in minX..maxX) if (inside(pts, x.toFloat(), y.toFloat())) set(y * w + x)
    for (i in pts.indices) line(w, h, pts[i], pts[(i + 1) % pts.size], 1, set)
}

/** Line of [width] pixels between two points (width 0 or 1 = thin). */
private fun line(w: Int, h: Int, a: Pt, b: Pt, width: Int, set: (Int) -> Unit) {
    val steps = max(1, max(abs(b.x - a.x), abs(b.y - a.y)).toInt() * 2)
    val r = (max(1, width) - 1) / 2
    for (s in 0..steps) {
        val p = lerpPt(a, b, s.toFloat() / steps)
        val cx = p.x.toInt(); val cy = p.y.toInt()
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) if (x in 0 until w && y in 0 until h) set(y * w + x)
    }
}

// ====================================================================== Equalizer2d

/**
 * LedFx Equalizer2d: frequency bars across a matrix (from the bottom, or from the middle with
 * `center`), optional peak markers, or a `ring` of bands around an oval that can `spin` with the
 * bass. Bar colours: `power_gradient` Off (by band), Solid (by level), Progressive (along the bar
 * by height) or Stretch (the whole gradient along each bar).
 */
class Equalizer2d : Effect2D() {
    private var mel = FloatArray(0)
    private var peaks = FloatArray(0)
    private val impulseFilter = ExpFilter(0.1f, 0.99f)
    private var spinValue = 0f
    private var ringDist = FloatArray(0)
    private var ringKey = ""

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val n = w * h
        val bands = p.i("bands").coerceIn(1, max(1, n))
        if (mel.size != n) mel = FloatArray(n)
        ctx.audio.melbank(mel, p.b("filtered"))
        for (i in mel.indices) mel[i] = mel[i].coerceIn(0f, 1f)

        // Volume per band (numpy array_split: the first n % bands parts get one extra value).
        val volumes = FloatArray(bands + 1)
        var start = 0
        for (b in 0 until bands) {
            val size = n / bands + if (b < n % bands) 1 else 0
            var sum = 0f; var best = 0f
            for (k in start until start + size) { sum += mel[k]; best = max(best, mel[k]) }
            volumes[b] = if (size == 0) 0f else if (p.b("max_vs_mean")) best else sum / size
            start += size
        }
        volumes[bands] = volumes[0]
        val peakMarks = p.b("peak_marks")
        if (peaks.size != bands + 1) peaks = FloatArray(bands + 1)
        if (peakMarks) {
            val decay = p.f("peak_decay")
            for (b in 0..bands) { val a = if (volumes[b] > peaks[b]) 0.99f else decay; peaks[b] = a * volumes[b] + (1 - a) * peaks[b] }
        }
        impulseFilter.alphaDecay = p.f("spin_decay")
        val impulse = impulseFilter.update(ctx.audio.power(p.s("frequency_range")) * p.f("spin_multiplier"))

        val mode = p.s("power_gradient")
        val barColors = IntArray(bands) { b -> ctx.grad(if (mode == "Solid") volumes[b] else b.toFloat() / bands) }
        val center = p.b("center")
        val peakSize = p.i("peak_percent") * h / 100
        val peakColor = p.color("peak_color")

        if (p.b("ring")) {
            if (p.b("spin")) spinValue = (spinValue + impulse) % 360f
            val rotation = if (p.b("spin")) spinValue else 0f
            val bandsc = List(bands + 1) { i ->
                val angle = 2.0 * PI * i / bands + Math.toRadians(rotation.toDouble())
                Pt((w / 2.0 + (w - 1) / 2.0 * cos(angle)).toFloat(), (h / 2.0 + (h - 1) / 2.0 * sin(angle)).toFloat())
            }
            val mids = List(bands) { lerpPt(bandsc[it], bandsc[it + 1], 0.5f) }.let { it + it[0] }
            val pc = Pt(w / 2f, h / 2f)
            fun poly(i: Int) = if (center) listOf(lerpPt(pc, bandsc[i], volumes[i]), lerpPt(pc, bandsc[i + 1], volumes[i]), pc)
                else listOf(bandsc[i], bandsc[i + 1], lerpPt(mids[i], pc, volumes[i]))

            if (mode == "Progressive" || mode == "Stretch") {
                val key = "$w:$h"
                if (key != ringKey) {
                    ringKey = key
                    val cx = w / 2f; val cy = h / 2f; val rx = max(w / 2f, 1f); val ry = max(h / 2f, 1f)
                    ringDist = FloatArray(n) { k ->
                        val dx = (k % w - cx) / rx; val dy = (k / w - cy) / ry
                        sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                    }
                }
                val stretch = mode == "Stretch"
                for (i in 0 until bands) {
                    val vol = max(volumes[i], 0.01f)
                    fillPolygon(w, h, poly(i)) { k ->
                        val d = ringDist[k]
                        val pos = when {
                            stretch -> ((if (center) d else 1f - d) / vol).coerceIn(0f, 1f)
                            center -> d
                            else -> 1f - d
                        }
                        img[k] = ctx.grad(pos)
                    }
                }
            } else {
                for (i in 0 until bands) fillPolygon(w, h, poly(i)) { k -> img[k] = barColors[i] }
            }
            if (peakMarks) {
                for (i in 0 until bands) {
                    val a = if (center) lerpPt(pc, mids[i], peaks[i]) else lerpPt(mids[i], pc, peaks[i])
                    val b = if (center) lerpPt(pc, mids[i + 1], peaks[i + 1]) else lerpPt(mids[i + 1], pc, peaks[i + 1])
                    line(w, h, a, b, peakSize) { k -> img[k] = peakColor }
                }
            }
            return
        }

        val half = h / 2
        for (i in 0 until bands) {
            val bandStart = (w / bands.toFloat() * i).toInt()
            val bandEnd = max(bandStart, (w / bands.toFloat() * (i + 1)).toInt() - 1)
            val vs = (h * volumes[i]).toInt()
            if (mode == "Progressive" || mode == "Stretch") {
                val bottom: Int; val top: Int
                if (center) { bottom = max(half - vs / 2, 0); top = min(half + vs / 2, h - 1) }
                else { bottom = max(h - 1 - vs, 0); top = h - 1 }
                for (row in bottom..top) {
                    val pos = if (center) abs(row - half).toFloat() / (if (mode == "Stretch") max(vs / 2, 1) else max(half, 1))
                        else (h - 1 - row).toFloat() / (if (mode == "Stretch") max(vs, 1) else max(h - 1, 1))
                    val c = ctx.grad(pos.coerceIn(0f, 1f))
                    for (x in bandStart..min(bandEnd, w - 1)) img[row * w + x] = c
                }
            } else if (center) {
                fillRect(img, w, h, bandStart, half - vs / 2, bandEnd, half + vs / 2, barColors[i])
            } else {
                fillRect(img, w, h, bandStart, h - 1 - vs, bandEnd, h - 1, barColors[i])
            }
            if (peakMarks) {
                if (center) {
                    val scaled = (half * peaks[i]).toInt()
                    val end = scaled + peakSize / 2
                    fillRect(img, w, h, bandStart, half + scaled, bandEnd, half + end, peakColor)
                    fillRect(img, w, h, bandStart, half - end, bandEnd, half - scaled, peakColor)
                } else {
                    val scaled = ((h - 1) * peaks[i]).toInt()
                    fillRect(img, w, h, bandStart, h - 1 - scaled - peakSize, bandEnd, h - 1 - scaled, peakColor)
                }
            }
        }
    }
}

// ====================================================================== Radial

/**
 * LedFx Radial: wraps a strip around a centre point, radius = position along the strip. In
 * LedFx the strip is another light's picture; here it is a chosen strip effect (`source`),
 * rendered as a strip of `source_pixels` LEDs. Shapes: `edges` 0 = circle, 1 = bar, 2 = oval,
 * 3+ = polygon (or lobes with `polygon` off, or a star with `star`); plus `twist`, `rotation` and
 * `spin` driven by the chosen frequency range.
 */
class Radial2d : Effect2D() {
    private val children = ChildEffects()
    private var spinTotal = 0.0

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val sourceId = ChildEffects.usable(p.s("source"), matrix = false).firstOrNull() ?: return
        val count = p.i("source_pixels").coerceIn(2, 1000)

        val s = p.f("spin")
        val spin = sign(s) * s * s / 10.0 // LedFx: nonlinear_log(spin, 2) / 10
        spinTotal = frac(spinTotal + ctx.audio.power(p.s("frequency_range")) * spin)

        // The source runs as a plain strip.
        val rows = ctx.rows; val segOf = ctx.segmentOf; val segCount = ctx.segmentCount
        ctx.rows = 1; ctx.segmentOf = IntArray(0); ctx.segmentCount = 1
        val strip = children.render(ctx, listOf(sourceId), count).getValue(sourceId)
        ctx.rows = rows; ctx.segmentOf = segOf; ctx.segmentCount = segCount

        val cx = w * p.f("x_offset"); val cy = h * p.f("y_offset")
        var maxRadius = 0f
        for (y in 0 until h) for (x in 0 until w) maxRadius = max(maxRadius, sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)))
        maxRadius *= 1f + 1e-6f
        val theta = frac(spinTotal + p.f("rotation")) * 2 * PI
        val edges = p.i("edges")
        val polygon = p.b("polygon")
        val star = p.f("star")
        val twist = p.f("twist")
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx; val dy = y - cy
            var radius = sqrt(dx * dx + dy * dy).toDouble()
            val angle = atan2(dy.toDouble(), dx.toDouble()) - theta
            val angleNorm = (angle + PI) / (2 * PI)
            when {
                edges == 1 -> radius = abs(dx * cos(theta) + dy * sin(theta))
                edges == 2 -> radius *= sqrt(cos(angle) * cos(angle) + 0.25 * sin(angle) * sin(angle))
                edges >= 3 && !polygon -> radius *= abs(cos(edges * angle / 2))
                edges >= 3 -> {
                    val a = 2 * PI / edges
                    val angleMod = floorModD(angle + a / 2, a) - a / 2
                    var polyRadius = cos(PI / edges) / max(cos(angleMod), 1e-6)
                    if (star != 0f) polyRadius *= 1 + star * cos(edges * angle)
                    radius /= polyRadius
                }
            }
            val norm = if (maxRadius > 0f) radius / maxRadius else 0.0
            val index = frac(norm + twist * angleNorm)
            img[y * w + x] = strip[(index * count).toInt().coerceIn(0, count - 1)]
        }
    }

    /** Python's % for floats: the result has the sign of [m]. */
    private fun floorModD(x: Double, m: Double) = x - kotlin.math.floor(x / m) * m
}
