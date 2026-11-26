/*
 * Effects ported from WLED (https://github.com/wled/WLED, wled00/FX.cpp and helpers).
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors,
 * licensed under the EUPL v. 1.2 or later; included here under the GPL-3.0 via the EUPL's
 * compatibility clause. The beat and ease functions derive from FastLED (MIT licence).
 * Each effect below names its original authors as WLED credits them.
 */
package app.touchfx.effects

import app.touchfx.audio.AudioAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ====================================================================== WLED / FastLED maths

/** WLED's integer maths with its 8/16-bit wraparound (wled_math.cpp, util.cpp, FastLED). */
internal object WledMath {
    /** 16-bit sine: theta 0..65535 -> -32767..32767 (Bhaskara approximation, as WLED). */
    fun sin16(theta: Int): Int {
        var th = theta and 0xFFFF
        var sign = 1
        if (th > 0x7FFF) { th = 0xFFFF - th; sign = -1 }
        val precal = th.toLong() * (0x7FFF - th)
        val num = precal * (4 * 0x7FFF)
        val den = 1342095361L - precal
        return (num / den).toInt() * sign
    }

    fun sin8(theta: Int): Int = minOf(sin16((theta and 0xFF) * 257) + 0x7FFF + 128, 0xFFFF) shr 8
    fun cos8(theta: Int): Int = sin8(theta + 64)

    fun scale8(i: Int, scale: Int): Int = ((i and 0xFF) * (1 + (scale and 0xFF))) shr 8
    fun scale16(i: Int, scale: Int): Int = (((i and 0xFFFF).toLong() * ((scale and 0xFFFF) + 1)) shr 16).toInt()

    fun beat88(bpm88: Int, nowMs: Long, timebase: Long = 0): Int = ((((nowMs - timebase) * bpm88 * 280) shr 16) and 0xFFFF).toInt()
    fun beat16(bpm: Int, nowMs: Long, timebase: Long = 0): Int = beat88(if (bpm < 256) bpm shl 8 else bpm, nowMs, timebase)
    fun beat8(bpm: Int, nowMs: Long, timebase: Long = 0): Int = beat16(bpm, nowMs, timebase) shr 8

    fun beatsin8(bpm: Int, nowMs: Long, lowest: Int = 0, highest: Int = 255, timebase: Long = 0, phase: Int = 0): Int {
        val s = sin8(beat8(bpm, nowMs, timebase) + phase)
        return (lowest + scale8(s, (highest - lowest) and 0xFF)) and 0xFF
    }

    fun beatsin16(bpm: Int, nowMs: Long, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0, phase: Int = 0): Int {
        val s = (sin16(beat16(bpm, nowMs, timebase) + phase) + 32768) and 0xFFFF
        return (lowest + scale16(s, (highest - lowest) and 0xFFFF)) and 0xFFFF
    }

    fun ease8InOutCubic(i: Int): Int {
        val v = i and 0xFF
        return (v * v * ((3 shl 8) - (v shl 1))) shr 16
    }

    /** WLED's fast atan2 approximation (radians). */
    fun atan2(y: Float, x: Float): Float {
        val ay = abs(y); val ax = abs(x)
        var r = (ax - ay) / (ay + ax + 1e-10f)
        var angle: Float
        if (x < 0) { r = -r; angle = (PI / 2 + PI / 4).toFloat() } else angle = (PI / 2 - PI / 4).toFloat()
        angle += (0.1963f * r * r - 0.9817f) * r
        return if (y < 0f) -angle else angle
    }

    /** FastLED random8(lo, hi): lo until hi (hi exclusive). */
    fun random8(lo: Int, hi: Int): Int = if (hi <= lo) lo else lo + Random.nextInt(hi - lo)

    // ---- WLED's Perlin noise (util.cpp), 3D, 16-bit result. Coordinates are uint32 bit patterns.
    private fun grad(h: Int) = (h and 0x03) - 2

    private fun gradient3D(x0: Int, dx: Int, y0: Int, dy: Int, z0: Int, dz: Int): Int {
        var h = (x0 * 0x27D4EB2D) xor (y0 * 0xB5297A4D.toInt()) xor (z0 * 0x1B56C4E9)
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        return ((grad(h) * dx + grad(h ushr 2) * dy + grad(h ushr 3) * dz) * 85) shr 9
    }

    private fun smoothstep(t: Int): Int {
        val tsq = (t.toLong() * t) shr 16
        val factor = (3L shl 16) - (t.toLong() shl 1)
        return ((tsq * factor) shr 18).toInt()
    }

    private fun lerp(a: Int, b: Int, t: Int): Int = a + (((b - a).toLong() * t) shr 14).toInt()

    private fun perlin3DRaw(x: Int, y: Int, z: Int): Int {
        val x0 = x ushr 16; val y0 = y ushr 16; val z0 = z ushr 16
        val x1 = x0 + 1; val y1 = y0 + 1; val z1 = z0 + 1
        val dx0 = x and 0xFFFF; val dy0 = y and 0xFFFF; val dz0 = z and 0xFFFF
        val dx1 = dx0 - 0x10000; val dy1 = dy0 - 0x10000; val dz1 = dz0 - 0x10000
        val g000 = gradient3D(x0, dx0, y0, dy0, z0, dz0)
        val g001 = gradient3D(x0, dx0, y0, dy0, z1, dz1)
        val g010 = gradient3D(x0, dx0, y1, dy1, z0, dz0)
        val g011 = gradient3D(x0, dx0, y1, dy1, z1, dz1)
        val g100 = gradient3D(x1, dx1, y0, dy0, z0, dz0)
        val g101 = gradient3D(x1, dx1, y0, dy0, z1, dz1)
        val g110 = gradient3D(x1, dx1, y1, dy1, z0, dz0)
        val g111 = gradient3D(x1, dx1, y1, dy1, z1, dz1)
        val tx = smoothstep(dx0); val ty = smoothstep(dy0); val tz = smoothstep(dz0)
        val nx0 = lerp(g000, g100, tx); val nx1 = lerp(g010, g110, tx)
        val nx2 = lerp(g001, g101, tx); val nx3 = lerp(g011, g111, tx)
        return lerp(lerp(nx0, nx1, ty), lerp(nx2, nx3, ty), tz)
    }

    fun perlin16(x: Int, y: Int, z: Int): Int = ((((perlin3DRaw(x, y, z) * 1731) shr 10) + 33147)).coerceIn(0, 0xFFFF)
}

// ====================================================================== segment emulation

/**
 * What a WLED effect sees as its segment: a persistent pixel buffer (WLED effects draw on the
 * previous frame), the sliders, time, the palette and audio. Effects run at WLED's default
 * 42 frames per second, so per-frame fades and movements keep WLED's pace.
 */
internal abstract class WledEffect {
    var w = 0; var h = 0
    var px = IntArray(0)
    /** Milliseconds, like WLED's strip.now. */
    var now = 0L
    /** Frames since the effect started or the size changed (SEGENV.call). */
    var call = 0L
    lateinit var ctx: RenderContext
    private var acc = TICK
    private var kickLatch = false

    // Audio, as WLED's audio-reactive usermod provides it.
    /** Smoothed volume 0..255 (volumeSmth). */
    var volumeSmth = 0f
    /** A peak (kick) since the last WLED frame (samplePeak). */
    var samplePeak = false
    /** Loudest frequency in Hz (FFT_MajorPeak). */
    var majorPeakHz = 1f

    val len get() = w * h
    val speed get() = ctx.p.i("speed")
    val intensity get() = ctx.p.i("intensity")
    val custom1 get() = ctx.p.i("custom1")
    val custom2 get() = ctx.p.i("custom2")
    val custom3 get() = ctx.p.i("custom3")

    /** One WLED frame. */
    abstract fun frame()
    /** Called when the size changes (and at the start). */
    open fun reset() {}

    fun run(c: RenderContext, width: Int, height: Int, out: IntArray) {
        ctx = c
        if (width != w || height != h) {
            w = width; h = height
            px = IntArray(w * h); call = 0; acc = TICK
            reset()
        }
        if (c.audio.beat) kickLatch = true
        acc += c.dt
        var n = 0
        while (acc >= TICK && n < 3) {
            acc -= TICK; n++
            now = (c.time * 1000).toLong()
            volumeSmth = c.audio.volume * 255f
            samplePeak = kickLatch; kickLatch = false
            majorPeakHz = majorPeak(c.audio.bands)
            frame()
            call++
        }
        if (acc > TICK) acc = TICK // don't pile up after a stall
        px.copyInto(out, 0, 0, minOf(px.size, out.size))
    }

    private fun majorPeak(bands: FloatArray): Float {
        var best = 0
        for (b in bands.indices) if (bands[b] > bands[best]) best = b
        return if (bands[best] < 0.05f) 1f else AudioAnalyzer.bandCenterHz(best)
    }

    // ---- drawing (WLED Segment API)
    fun set(i: Int, c: Int) { if (i in px.indices) px[i] = c }
    fun setXY(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = c }
    fun addXY(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = add(px[y * w + x], c) }
    fun fill(c: Int) = px.fill(c)

    fun fadeToBlackBy(f: Int) {
        if (f <= 0) return
        for (i in px.indices) px[i] = scale(px[i], 255 - f)
    }

    /** WLED fade_out towards black: rate 0..255, higher = slower fade. */
    fun fadeOut(rateIn: Int) {
        val rate = (256 - rateIn) shr 1
        val mapped = 256 / (rate + 1)
        for (i in px.indices) {
            val c = px[i]
            if (c == 0) continue
            var out = 0
            for (shift in intArrayOf(0, 8, 16)) {
                val c1 = (c shr shift) and 0xFF
                var delta = (0 - c1) * mapped / 256
                if (delta == 0 && c1 != 0) delta = -1
                out = out or (((c1 + delta) and 0xFF) shl shift)
            }
            px[i] = out
        }
    }

    /** WLED blur: 1D along the strip, or 2D (rows then columns) on a matrix. */
    fun blur(amount: Int, smear: Boolean = false) {
        if (amount <= 0) return
        if (h > 1) { blurLines(amount, smear, rows = true); blurLines(amount, smear, rows = false) }
        else blurLines(amount, smear, rows = true)
    }

    private fun blurLines(amount: Int, smear: Boolean, rows: Boolean) {
        val keep = if (smear) 255 else 255 - amount
        val seep = amount shr 1
        val lines = if (rows) h else w
        val length = if (rows) w else h
        for (l in 0 until lines) {
            fun idx(k: Int) = if (rows) l * w + k else k * w + l
            var cur = px[idx(0)]
            var carry = scale(cur, seep)
            px[idx(0)] = scale(cur, keep)
            for (k in 1 until length) {
                cur = px[idx(k)]
                val part = scale(cur, seep)
                cur = add(scale(cur, keep), carry)
                px[idx(k - 1)] = add(px[idx(k - 1)], part)
                px[idx(k)] = cur
                carry = part
            }
        }
    }

    fun fillCircle(cx: Int, cy: Int, radius: Int, c: Int) {
        if (radius <= 0) return
        val r2 = radius * radius + radius
        for (y in -radius..radius) for (x in -radius..radius) {
            if (x * x + y * y < r2) setXY(cx + x, cy + y, c)
        }
    }

    // ---- colour
    /** ColorFromPalette: index 0..255 (wraps), brightness 0..255; [wrap] false = no wrap at the end. */
    fun pal(index: Int, bri: Int = 255, wrap: Boolean = true): Int {
        val i = index and 0xFF
        val c = if (wrap) ctx.palette.at(i / 256f + ctx.gradientOffset) else ctx.palette.clamped(i / 255f)
        return if (bri >= 255) c else scale(c, bri.coerceIn(0, 255))
    }

    /** color_from_palette with mapping: position along the segment picks the colour. */
    fun palMapped(i: Int, bri: Int = 255) = pal(minOf(i * 255 / maxOf(1, len), 255), bri)

    companion object {
        /** WLED's default frame time: 42 fps. */
        const val TICK = 1.0 / 42.0
        const val GRAY = 0x808080

        fun scale(c: Int, s: Int): Int {
            val f = s + 1
            return ((((c shr 16) and 0xFF) * f shr 8) shl 16) or ((((c shr 8) and 0xFF) * f shr 8) shl 8) or (((c and 0xFF) * f) shr 8)
        }

        fun add(a: Int, b: Int): Int = Rgb.add(a, b)
    }
}

/** Runs a WLED effect on a strip (1D). */
internal class WledStrip(private val fx: WledEffect) : Effect() {
    override fun render(ctx: RenderContext, out: IntArray) = fx.run(ctx, out.size, 1, out)
}

/** Runs a WLED 2D effect on a matrix, with the app's rotation and flips. */
internal class WledMatrix(private val fx: WledEffect) : Effect2D() {
    override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) = fx.run(ctx, w, h, img)
}

/**
 * WLED's sliders as settings, with WLED's labels and defaults: speed, intensity, custom1-3
 * (0-255; custom3 0-31). A null label leaves the slider out (WLED doesn't use it there).
 */
internal fun wledParams(
    speed: String? = "Speed", intensity: String? = null,
    custom1: String? = null, custom2: String? = null, custom3: String? = null,
    sx: Int = 128, ix: Int = 128, c1: Int = 128, c2: Int = 128, c3: Int = 16,
): List<Param> = listOfNotNull(
    speed?.let { IntParam("speed", it, 0, 255, sx) },
    intensity?.let { IntParam("intensity", it, 0, 255, ix) },
    custom1?.let { IntParam("custom1", it, 0, 255, c1) },
    custom2?.let { IntParam("custom2", it, 0, 255, c2) },
    custom3?.let { IntParam("custom3", it, 0, 31, c3) },
)

// ====================================================================== 1D effects

/** WLED Blink: alternates the palette and black; intensity sets the duty cycle. */
internal class WledBlink(private val rainbow: Boolean) : WledEffect() {
    private var step = -1L
    override fun frame() {
        val frameMs = (TICK * 1000).toLong()
        var cycle = (255 - speed) * 20L
        val onTime = frameMs + ((cycle * intensity) shr 8)
        cycle += frameMs * 2
        val it = now / cycle
        val rem = now % cycle
        val on = it != step || rem <= onTime // new cycle: on for at least one frame
        step = it
        if (!on) { fill(0); return }
        // Blink Rainbow: one colour, moving round the palette (WLED's color_wheel with a palette).
        if (rainbow) fill(pal((call and 0xFF).toInt())) else for (i in 0 until len) set(i, palMapped(i))
    }
}

/** WLED Bpm: palette stripes pulsing at the speed in beats per minute. */
internal class WledBpm : WledEffect() {
    override fun frame() {
        val stp = ((now / 20) and 0xFF).toInt()
        val beat = WledMath.beatsin8(speed, now, 64, 255)
        for (i in 0 until len) set(i, pal(stp + i * 2, (beat - stp + i * 10) and 0xFF))
    }
}

/** WLED Lightning: a dim leader, then a few bright flashes, then a pause. */
internal class WledLightning : WledEffect() {
    private var flashes = 0
    private var delayMs = 0L
    private var stepMs = 0L
    override fun frame() {
        if (len <= 1) { fill(0); return }
        val start = Random.nextInt(len)
        val length = 1 + Random.nextInt(len - start)
        var bri = 255 / WledMath.random8(1, 3)
        if (flashes == 0) { // leader
            flashes = WledMath.random8(4, 4 + intensity / 20) * 2
            bri = 52
            delayMs = 200
        }
        fill(0)
        if (flashes > 3 && flashes % 2 == 0) {
            for (i in start until start + length) set(i, palMapped(i, bri))
            flashes--
            stepMs = now
        } else if (now - stepMs > delayMs) {
            flashes--
            if (flashes < 2) flashes = 0
            delayMs = 50L + Random.nextInt(100)
            if (flashes == 2) delayMs = WledMath.random8(0, 255 - speed).toLong() * 100 // between strikes
            stepMs = now
        }
    }
}

/** WLED Gravfreq (by Andrew Tuline): a volume bar from the centre, coloured by the loudest frequency. */
internal class WledGravfreq : WledEffect() {
    private var topLed = 0
    private var counter = 0
    override fun reset() { topLed = 0; counter = 0 }
    override fun frame() {
        if (len <= 1) return
        fadeOut(250)
        val avg = volumeSmth * intensity / 255f * 0.125f
        val mapped = avg * 2f / 32f * (len / 2f)
        val samples = mapped.toInt().coerceIn(0, len / 2)
        val gravity = 8 - speed / 32
        if (samples >= topLed + 1) topLed = samples - 1
        else if (counter % gravity == 0) topLed--
        val index = ((log10(maxOf(1f, majorPeakHz)) - (MAX_FREQ_LOG10 - 1.78f)) * 255).toInt()
        for (i in 0 until samples) {
            val c = pal(index, wrap = true)
            set(i + len / 2, c); set(len / 2 - i - 1, c)
        }
        if (topLed >= 0) { set(topLed + len / 2, GRAY); set(len / 2 - 1 - topLed, GRAY) }
        counter = (counter + 1) % gravity
    }

    private companion object {
        /** WLED's audio sampling tops out at 11025 Hz. */
        val MAX_FREQ_LOG10 = log10(11025f)
    }
}

/** WLED Puddlepeak (by Andrew Tuline): a splash of colour at a random spot on each peak. */
internal class WledPuddlepeak : WledEffect() {
    override fun frame() {
        if (len <= 1) return
        fadeOut(224 + speed * 30 / 255)
        val pos = Random.nextInt(len)
        // WLED's peak detector has a minimum volume; here a kick above that volume.
        if (!samplePeak || volumeSmth < custom2 / 2f) return
        var size = (volumeSmth * intensity / 256f / 4f).toInt() + 1
        if (pos + size >= len) size = len - pos
        val c = pal((now and 0xFF).toInt())
        for (i in 0 until size) set(pos + i, c)
    }
}

// ====================================================================== 2D effects

/** WLED Blobs (Floating Blobs by stepko, adapted by Blaz Kristan): soft bouncing blobs. */
internal class WledBlobs : WledEffect() {
    private val x = FloatArray(MAX); private val y = FloatArray(MAX)
    private val sx = FloatArray(MAX); private val sy = FloatArray(MAX)
    private val r = FloatArray(MAX); private val grow = BooleanArray(MAX); private val color = IntArray(MAX)
    private var colorStep = 0L

    override fun reset() {
        for (i in 0 until MAX) {
            r[i] = WledMath.random8(1, if (w > 8) w / 4 else 2).toFloat()
            sx[i] = WledMath.random8(3, w) / (256f - speed)
            sy[i] = WledMath.random8(3, h) / (256f - speed)
            x[i] = WledMath.random8(0, w - 1).toFloat()
            y[i] = WledMath.random8(0, h - 1).toFloat()
            color[i] = Random.nextInt(256)
            grow[i] = r[i] < 1f
            if (sx[i] == 0f) sx[i] = 1f
            if (sy[i] == 0f) sy[i] = 1f
        }
    }

    override fun frame() {
        val amount = (intensity shr 5) + 1
        fadeToBlackBy((custom2 shr 3) + 1)
        for (i in 0 until amount) {
            if (colorStep < now) color[i] = (color[i] + 4) and 0xFF
            val step = maxOf(abs(sx[i]), abs(sy[i])) * 0.05f
            if (grow[i]) { r[i] += step; if (r[i] >= minOf(w / 4f, 2f)) grow[i] = false }
            else { r[i] -= step; if (r[i] < 1f) grow[i] = true }
            val c = pal(color[i], wrap = false)
            if (r[i] > 1f) fillCircle(x[i].roundToInt(), y[i].roundToInt(), r[i].roundToInt(), c)
            else setXY(x[i].roundToInt(), y[i].roundToInt(), c)
            x[i] += when {
                x[i] + r[i] >= w - 1 -> sx[i] * ((w - 1 - x[i]) / r[i] + 0.005f)
                x[i] - r[i] <= 0 -> sx[i] * (x[i] / r[i] + 0.005f)
                else -> sx[i]
            }
            y[i] += when {
                y[i] + r[i] >= h - 1 -> sy[i] * ((h - 1 - y[i]) / r[i] + 0.005f)
                y[i] - r[i] <= 0 -> sy[i] * (y[i] / r[i] + 0.005f)
                else -> sy[i]
            }
            if (x[i] < 0.01f) { sx[i] = WledMath.random8(3, w) / (256f - speed); x[i] = 0.01f }
            else if (x[i] > w - 1.01f) { sx[i] = -WledMath.random8(3, w) / (256f - speed); x[i] = w - 1.01f }
            if (y[i] < 0.01f) { sy[i] = WledMath.random8(3, h) / (256f - speed); y[i] = 0.01f }
            else if (y[i] > h - 1.01f) { sy[i] = -WledMath.random8(3, h) / (256f - speed); y[i] = h - 1.01f }
        }
        blur(custom1 shr 2)
        if (colorStep < now) colorStep = now + 2000 // colours drift every 2 s
    }

    private companion object { const val MAX = 8 }
}

/** WLED Octopus (Stepko and Sutaburosu, adapted by @blazoncek): swirling tentacles from a centre. */
internal class WledOctopus : WledEffect() {
    private var angle = IntArray(0)
    private var radius = IntArray(0)
    private var offX = -1; private var offY = -1
    private var t = 0L

    override fun frame() {
        if (angle.size != len || custom1 != offX || custom2 != offY) {
            offX = custom1; offY = custom2; t = 0
            angle = IntArray(len); radius = IntArray(len)
            val mapp = 180 / maxOf(w, h)
            val cX = w / 2 + ((custom1 - 128) * w) / 255
            val cY = h / 2 + ((custom2 - 128) * h) / 255
            for (x in 0 until w) for (y in 0 until h) {
                val dx = x - cX; val dy = y - cY
                angle[y * w + x] = (40.7436f * WledMath.atan2(dy.toFloat(), dx.toFloat())).toInt() and 0xFF
                radius[y * w + x] = (sqrt((dx * dx + dy * dy).toFloat()) * mapp).toInt() and 0xFF
            }
        }
        t += speed / 32 + 1
        val legs = custom3 / 4 + 1
        for (x in 0 until w) for (y in 0 until h) {
            val a = angle[y * w + x]; val r = radius[y * w + x]
            val v = WledMath.sin8(WledMath.sin8((a * 4 - r) / 4 + (t / 2).toInt()) + r - t.toInt() + a * legs)
            setXY(x, y, pal((t / 2).toInt() - r, v))
        }
    }
}

/** WLED Soap (by Stepko, idea by Stefan Petrick, adapted by @blazoncek, optimised by @dedehai). */
internal class WledSoap : WledEffect() {
    private var noise = IntArray(0)
    private var pixels = IntArray(0)
    private val coord = IntArray(3)

    override fun reset() {
        noise = IntArray(len); pixels = IntArray(len)
        for (i in 0 until 3) coord[i] = Random.nextInt()
    }

    override fun frame() {
        val scaleX = 160000 / w
        val scaleY = 160000 / h
        val mov = minOf(w, h) * (speed + 2) / 2
        val smooth = minOf(250, intensity)
        if (call > 0) for (i in 0 until 3) coord[i] += mov
        for (i in 0 until w) {
            val io = scaleX * (i - w / 2)
            for (j in 0 until h) {
                val jo = scaleY * (j - h / 2)
                val data = WledMath.perlin16(coord[0] + io, coord[1] + jo, coord[2]) shr 8
                val k = j * w + i
                noise[k] = WledMath.scale8(noise[k], smooth) + WledMath.scale8(data, 255 - smooth)
            }
        }
        if (call == 0L) for (k in 0 until len) { pixels[k] = pal(noise[k].inv() * 3); px[k] = pixels[k] }
        soapPixels(isRow = true)
        soapPixels(isRow = false)
    }

    private fun soapPixels(isRow: Boolean) {
        val tRC = if (isRow) h else w
        val tCR = if (isRow) w else h
        val amplitude = maxOf(1, (tCR - 8) shr 3) * (1 + (custom1 shr 5))
        val buf = IntArray(tCR)
        for (i in 0 until tRC) {
            val amount = (noise[if (isRow) i * w else i] - 128) * amplitude
            val delta = abs(amount) shr 8
            val fraction = abs(amount) and 255
            for (j in 0 until tCR) {
                val zD: Int; val zF: Int
                if (amount < 0) { zD = j - delta; zF = zD - 1 } else { zD = j + delta; zF = zD + 1 }
                var yA = abs(zD) % tCR; var yB = abs(zF) % tCR
                var xA = i; var xB = i
                if (isRow) { val t1 = xA; xA = yA; yA = t1; val t2 = xB; xB = yB; yB = t2 }
                val a = yA * w + xA; val b = yB * w + xB
                val pa = if (zD in 0 until tCR) pixels[a] else pal(noise[a].inv() * 3)
                val pb = if (zF in 0 until tCR) pixels[b] else pal(noise[b].inv() * 3)
                buf[j] = add(scale(pa, WledMath.ease8InOutCubic(255 - fraction)), scale(pb, WledMath.ease8InOutCubic(fraction)))
            }
            for (j in 0 until tCR) {
                val k = if (isRow) i * w + j else j * w + i
                pixels[k] = buf[j]; px[k] = buf[j]
            }
        }
    }
}

/** WLED Tartan (by Elliott Kember, modified by Andrew Tuline): woven, drifting plaid. */
internal class WledTartan : WledEffect() {
    override fun frame() {
        val offX = WledMath.beatsin16(3, now, -360, 360)
        val offY = WledMath.beatsin16(2, now, -360, 360)
        val sharpness = custom3 / 8
        val xScale = WledMath.beatsin16(10, now, 1, 10)
        for (x in 0 until w) for (y in 0 until h) {
            var hue = (x * xScale + offY) and 0xFF
            setXY(x, y, pal(hue, sharpen(WledMath.sin8(x * speed / 2 + offX), sharpness)))
            hue = (y * 3 + offX) and 0xFF
            addXY(x, y, pal(hue, sharpen(WledMath.sin8(y * intensity / 2 + offY), sharpness)))
        }
    }

    private fun sharpen(bri: Int, sharpness: Int): Int {
        var v = bri.toLong()
        repeat(sharpness) { v *= bri }
        return ((v shr (8 * sharpness)) and 0xFF).toInt()
    }
}
