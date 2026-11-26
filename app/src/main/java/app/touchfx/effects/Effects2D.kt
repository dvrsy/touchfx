package app.touchfx.effects

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Settings every 2D effect has (LedFx `Twod`): rotation and flips. */
fun twodParams() = listOf(
    IntParam("rotate", "Rotate (× 90°)", 0, 3, 0),
    BoolParam("flip_horizontal", "Flip horizontally", false),
    BoolParam("flip_vertical", "Flip vertically", false),
)

/**
 * Base for 2D (matrix) effects, following LedFx's `Twod`. Subclasses draw a [w]×[h] image in
 * row-major order; this class applies the flips and rotation and writes the matrix pixels.
 *
 * The matrix is `rows` tall and `pixels / rows` wide; left-over pixels stay black. With a 90° or
 * 270° rotation the image is drawn at its native rotated size (h×w), so nothing is rescaled.
 * Rotation is the effect's `rotate` plus the light's own matrix rotation, as in LedFx.
 */
abstract class Effect2D : Effect() {
    private var image = IntArray(0)

    /** Draws the effect into [img] (`w*h`, row-major, 0xRRGGBB, cleared to black). */
    abstract fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int)

    final override fun render(ctx: RenderContext, out: IntArray) {
        out.fill(0)
        val th = ctx.rows.coerceIn(1, maxOf(1, out.size))
        val tw = maxOf(1, out.size / th)
        val rot = Math.floorMod(ctx.p.i("rotate") + ctx.matrixRotate, 4)
        // PIL-style: flips first, then the transpose; on 90/270 the flip axes swap (LedFx do_once).
        var flipV = ctx.p.b("flip_vertical")
        var flipH = ctx.p.b("flip_horizontal")
        if (rot == 1 || rot == 3) { val t = flipV; flipV = flipH; flipH = t }
        val rw = if (rot == 1 || rot == 3) th else tw
        val rh = if (rot == 1 || rot == 3) tw else th

        if (image.size != rw * rh) image = IntArray(rw * rh) else image.fill(0)
        draw(ctx, image, rw, rh)

        for (y in 0 until th) for (x in 0 until tw) {
            val i = y * tw + x
            if (i >= out.size) return
            // Source pixel in the flipped image (inverse of PIL's ROTATE_90/180/270).
            var fx: Int; var fy: Int
            when (rot) {
                1 -> { fx = rw - 1 - y; fy = x }
                2 -> { fx = rw - 1 - x; fy = rh - 1 - y }
                3 -> { fx = y; fy = rh - 1 - x }
                else -> { fx = x; fy = y }
            }
            if (flipH) fx = rw - 1 - fx
            if (flipV) fy = rh - 1 - fy
            out[i] = image[fy * rw + fx]
        }
    }
}

/** Draws a line from (x0,y0) to (x1,y1) (Bresenham), clipped to the image. */
internal fun drawLine(img: IntArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
    var x = x0; var y = y0
    val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        if (x in 0 until w && y in 0 until h) img[y * w + x] = c
        if (x == x1 && y == y1) return
        val e2 = 2 * err
        if (e2 >= dy) { err += dy; x += sx }
        if (e2 <= dx) { err += dx; y += sy }
    }
}

/**
 * Orientation test pattern, the same drawing as LedFx's `Twod.draw_test`: a white frame, and lines
 * from each corner to the centre — red from top-left, blue from top-right, green from
 * bottom-left, white from bottom-right. Use it to check rows, serpentine and rotation.
 */
class MatrixTest : Effect2D() {
    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        for (x in 0 until w) { img[x] = 0xFFFFFF; img[(h - 1) * w + x] = 0xFFFFFF }
        for (y in 0 until h) { img[y * w] = 0xFFFFFF; img[y * w + w - 1] = 0xFFFFFF }
        val mw = w / 2; val mh = h / 2
        drawLine(img, w, h, 0, 0, mw, mh, 0xFF0000)
        drawLine(img, w, h, w - 1, 0, mw - 1, mh, 0x0000FF)
        drawLine(img, w, h, 0, h - 1, mw - 1, mh, 0x00FF00)
        drawLine(img, w, h, w - 1, h - 1, mw, mh, 0xFFFFFF)
    }
}

/** LedFx Plasma2d: three layered sine fields, zoomed by the music, coloured with the gradient. */
class Plasma2d : Effect2D() {
    private var field = FloatArray(0)

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val power = ctx.audio.power(p.s("frequency_range"))
        val scale = p.f("lower") + power * p.f("density")
        val dv = p.f("density_vertical")
        val twist = p.f("twist")
        val radius = p.f("radius")
        val t = ctx.time.toFloat()
        // numpy ogrid 0..min(n, n*scale) with n points, endpoints included.
        val xSpan = minOf(w.toFloat(), w * scale)
        val ySpan = minOf(h.toFloat(), h * scale)
        if (field.size != w * h) field = FloatArray(w * h)
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (j in 0 until h) {
            val y = if (h > 1) ySpan * j / (h - 1) else 0f
            for (i in 0 until w) {
                val x = if (w > 1) xSpan * i / (w - 1) else 0f
                val v = sin(x * 0.1f + t) * cos(y * 0.1f - t) +
                    sin((x * dv + y * twist + t) * 2.5f) +
                    sin(sqrt(x * x + y * y) * radius - t)
                field[j * w + i] = v
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
        }
        val range = hi - lo
        for (k in 0 until w * h) {
            img[k] = ctx.grad(if (range > 0f) (field[k] - lo) / range else 0f)
        }
    }
}

/**
 * LedFx Texter, "Side Scroll" mode: the text enters on the right and scrolls left at
 * `speed × half the width` per second (LedFx screen units), starting again once it has fully
 * left. Speed 0 shows it centred. Drawn with [PixelFont], scaled in whole pixels so it stays crisp.
 * Options as in LedFx: scroll faster on the bass, pulse brightness with the bass, colour each word
 * from the gradient.
 */
class Texter2d : Effect2D() {
    private var text = ""
    /** One entry per font column: its lit rows, and which word it belongs to (-1 = space). */
    private var colMask = IntArray(0)
    private var colWord = IntArray(0)
    private var words = 0
    private var x = Float.NaN
    private val bass = ExpFilter(0.1f, 0.99f)

    private fun layout(t: String) {
        text = t
        val masks = ArrayList<Int>()
        val owner = ArrayList<Int>()
        var word = -1
        var inWord = false
        t.forEachIndexed { i, c ->
            if (i > 0) repeat(PixelFont.GAP) { masks += 0; owner += -1 }
            val g = PixelFont.glyph(c)
            if (g == null) {
                inWord = false
                repeat(PixelFont.SPACE_WIDTH) { masks += 0; owner += -1 }
            } else {
                if (!inWord) { word++; inWord = true }
                g.columns.forEach { masks += it; owner += word }
            }
        }
        colMask = masks.toIntArray()
        colWord = owner.toIntArray()
        words = word + 1
    }

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val t = p.s("text")
        if (t != text) layout(t)

        bass.alphaDecay = p.f("impulse_decay")
        val lows = bass.update(ctx.audio.lowsPower(filtered = false))
        val mult = p.f("multiplier")

        val scale = maxOf(1, h * p.i("height_percent") / 100 / PixelFont.HEIGHT)
        val textW = colMask.size * scale
        val base = p.f("speed_option_1")
        if (base <= 0f) {
            x = (w - textW) / 2f
        } else {
            if (x.isNaN() || x > w) x = w.toFloat()
            val boost = if (p.b("option_1")) 1f + lows * mult else 1f
            x -= base * boost * (w / 2f) * ctx.dt
            if (x + textW < 0f) x = w.toFloat()
        }

        val alpha = if (p.b("alpha")) minOf(1f, 0.1f + lows * mult) else 1f
        val gradient = p.b("use_gradient")
        val solid = p.color("text_color")
        val y0 = (h - PixelFont.CAP_HEIGHT * scale) / 2
        val left = kotlin.math.floor(x).toInt()
        for (c in colMask.indices) {
            val mask = colMask[c]
            if (mask == 0) continue
            val sx = left + c * scale
            if (sx >= w || sx + scale <= 0) continue
            val wordPos = if (words > 1) colWord[c].toFloat() / (words - 1) else 0f
            val color = Rgb.scale(if (gradient) ctx.grad(wordPos) else solid, alpha)
            for (r in 0 until PixelFont.HEIGHT) {
                if (mask and (1 shl r) == 0) continue
                for (dy in 0 until scale) {
                    val py = y0 + r * scale + dy
                    if (py !in 0 until h) continue
                    for (dx in 0 until scale) {
                        val px = sx + dx
                        if (px in 0 until w) img[py * w + px] = color
                    }
                }
            }
        }
    }
}

/**
 * LedFx Concentric: rings of the gradient flowing out of (or into) the centre, sped up by the
 * chosen frequency range. The distance field is precomputed per size and settings.
 */
class Concentric2d : Effect2D() {
    private var dist = FloatArray(0)
    private var key = ""
    private var offset = 0f

    private fun buildDist(w: Int, h: Int, gscale: Float, stretch: Float, smoothing: Float) {
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val d = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / gscale
            val dy = (y - cy) / gscale / stretch
            d[y * w + x] = sqrt(dx * dx + dy * dy)
        }
        // Soften the centre (LedFx: Gaussian blur with radius = center_smoothing).
        if (smoothing > 0f) blur2d(d, w, h, smoothing)
        val maxR = kotlin.math.hypot(cx, cy / stretch)
        for (i in d.indices) {
            val v = if (maxR > 0f) (d[i] / maxR).coerceIn(0f, 1f) else 0f
            d[i] = Math.pow(v.toDouble(), 0.9).toFloat()
        }
        dist = d
    }

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val gscale = p.f("gradient_scale")
        val stretch = p.f("stretch_height")
        val smoothing = p.f("center_smoothing")
        val k = "$w:$h:$gscale:$stretch:$smoothing"
        if (k != key) { key = k; buildDist(w, h, gscale, stretch, smoothing) }

        val power = ctx.audio.power(p.s("frequency_range")) * p.f("power_multiplier") * 12f
        offset = frac(offset + (power + p.f("idle_speed")) * ctx.dt)
        val shift = if (p.b("invert")) offset else -offset
        for (i in 0 until w * h) img[i] = ctx.palette.at(dist[i] + shift + ctx.gradientOffset)
    }
}

/** Separable Gaussian blur of a w×h float field in place (sigma in pixels). */
internal fun blur2d(a: FloatArray, w: Int, h: Int, sigma: Float) {
    val row = FloatArray(w); val rowTmp = FloatArray(w)
    for (y in 0 until h) {
        for (x in 0 until w) row[x] = a[y * w + x]
        blurChannel(row, rowTmp, sigma)
        for (x in 0 until w) a[y * w + x] = row[x]
    }
    val col = FloatArray(h); val colTmp = FloatArray(h)
    for (x in 0 until w) {
        for (y in 0 until h) col[y] = a[y * w + x]
        blurChannel(col, colTmp, sigma)
        for (y in 0 until h) a[y * w + x] = col[y]
    }
}

/**
 * LedFx PlasmaWled2d: WLED's 8-bit plasma (sin8/cos8 lookup tables), with the music changing its
 * size and speed. Index arithmetic wraps at 256 like numpy's uint8 casts in LedFx.
 */
class PlasmaWled2d : Effect2D() {
    private var timeModifier = 0.0

    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
        val p = ctx.p
        val power = ctx.audio.power(p.s("frequency_range")) * 2f
        val sizeB = power * p.f("size_multiplication")
        val speedMul = p.f("speed_multiplication")
        val timeVal = if (speedMul > 0f) {
            timeModifier += power * speedMul
            (timeModifier * 1000).toLong()
        } else (ctx.time * 1000).toLong()
        val a = timeVal.toDouble() / (p.i("speed") + 1)
        val hs = p.i("stretch_horizontal").toDouble()
        val vs = p.i("stretch_vertical").toDouble()
        // LedFx: rows use the "horizontal" stretch, columns the "vertical" one.
        val hStretch = maxOf(0.01, hs - sizeB * hs / 3)
        val vStretch = maxOf(0.01, vs - sizeB * vs / 3)
        for (y in 0 until h) {
            val cosIdx = (COS8[u8(y * hStretch / 16 + a / 3)] + a) % 256
            for (x in 0 until w) {
                val sinIdx = (SIN8[u8(x * vStretch / 16 + a / 4)] + a) % 256
                img[y * w + x] = ctx.grad(SIN8[u8(cosIdx + sinIdx)] / 255f)
            }
        }
    }

    private companion object {
        val SIN8 = IntArray(256) { (sin(it * 2.0 * Math.PI / 255.0) * 127.5 + 127.5).toInt().coerceIn(0, 255) }
        val COS8 = IntArray(256) { (cos(it * 2.0 * Math.PI / 255.0) * 127.5 + 127.5).toInt().coerceIn(0, 255) }
        /** numpy `np.uint8(x)` for non-negative x: truncate, keep the low 8 bits. */
        fun u8(x: Double) = (x.toLong() and 0xFF).toInt()
    }
}
