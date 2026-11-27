package app.touchfx.engine

import app.touchfx.effects.ChoiceParam
import app.touchfx.effects.ColorParam
import app.touchfx.effects.EffectInfo
import app.touchfx.effects.FloatParam
import app.touchfx.effects.Effects
import app.touchfx.effects.IntParam
import app.touchfx.effects.Palette
import app.touchfx.effects.Palettes
import app.touchfx.effects.Rgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** What an axis of the X/Y pad changes. */
enum class PadTarget(val label: String) {
    NONE("Nothing"),
    SPEED("Speed"),
    SIZE("Size"),
    INTENSITY("Intensity"),
    COLOURS("Colours"),
    HUE("Hue"),
    PALETTE("Palette shift"),
    BRIGHTNESS("Brightness"),
    SATURATION("Saturation"),
    STROBE("Strobe"),
    TIME("Overall speed"),
}

/**
 * The pad as touched: positions 0..1 (0.5 = centre = as saved). [lightId]: only that light
 * (pad opened from a light's screen); null = every running light.
 */
data class PadState(val x: Float, val y: Float, val xTarget: PadTarget, val yTarget: PadTarget, val lightId: String? = null) {
    fun appliesTo(id: String) = lightId == null || lightId == id
}

/**
 * The X/Y pad's rules, free of Android so they can be unit tested. Effect targets map to each
 * effect's own setting ([MACROS], picked by hand from all effects' settings); the centre leaves
 * the saved value, the edges reach the setting's minimum and maximum.
 */
object XyPad {
    /** A setting driven by a target; [inverted]: a higher value means slower / smaller / less. */
    data class Macro(val key: String, val inverted: Boolean = false)

    private fun m(vararg keys: String) = keys.map { Macro(it) }
    private fun inv(vararg keys: String) = keys.map { Macro(it, inverted = true) }

    val MACROS: Map<PadTarget, Map<String, List<Macro>>> = mapOf(
        PadTarget.SPEED to mapOf(
            "scroll" to m("speed"), "scroll_plus" to m("scroll_per_sec"),
            "scan" to m("speed"), "scan_and_flare" to m("speed"), "scan_multi" to m("speed"),
            "spotlight" to m("gradient_speed"),
            "segment_chase" to inv("speed"), "effect_chase" to inv("speed"), // choices run fast -> slow
            "fire" to m("speed"), "block_reflections" to m("speed"), "crawler" to m("speed"),
            "energy2" to m("speed"), "glitch" to m("speed"), "lava_lamp" to m("speed"),
            "marching" to m("speed"), "melt" to m("speed"), "melt_and_sparkle" to m("speed"), "water" to m("speed"),
            "filter" to m("roll_speed"), "gradient" to m("modulation_speed"), "singleColor" to m("modulation_speed"),
            "fade" to m("speed"), "rainbow" to m("speed"), "random_flash" to m("strobe_rate"), "ripples" to m("speed"),
            "wled_blink" to m("speed"), "wled_blink_rainbow" to m("speed"), "wled_bpm" to m("speed"),
            "wled_lightning" to m("speed"), "wled_blobs" to m("speed"), "wled_octopus" to m("speed"), "wled_soap" to m("speed"),
            "plasmawled" to inv("speed"), "concentric" to m("idle_speed"), "texter2d" to m("speed_option_1"),
            "equalizer2d" to m("spin_multiplier"), "radial" to m("spin"),
        ),
        PadTarget.SIZE to mapOf(
            "scan" to m("scan_width"), "scan_and_flare" to m("scan_width"), "scan_multi" to m("scan_width"),
            "spotlight" to m("spot_width"), "real_strobe" to m("strobe_width"), "random_flash" to m("hit_relative_size"),
            "melt_and_sparkle" to m("lava_width"), "crawler" to m("stretch"), "rainbow" to inv("frequency"),
            "segment_chase" to inv("parts"), "effect_chase" to inv("parts"),
            "wled_puddlepeak" to m("intensity"), "wled_tartan" to m("speed", "intensity"),
            "plasma2d" to inv("density"), "concentric" to m("gradient_scale"),
            "plasmawled" to inv("stretch_horizontal", "stretch_vertical"),
            "texter2d" to m("height_percent"), "radial" to inv("source_pixels"),
        ),
        PadTarget.INTENSITY to mapOf(
            "energy" to m("sensitivity"), "blade_power_plus" to m("multiplier"),
            "scan" to m("multiplier"), "scan_and_flare" to m("multiplier"), "scan_multi" to m("multiplier"),
            "fire" to m("intensity"), "block_reflections" to m("reactivity"), "crawler" to m("reactivity"),
            "energy2" to m("reactivity"), "glitch" to m("reactivity"), "lava_lamp" to m("reactivity"),
            "marching" to m("reactivity"), "melt" to m("reactivity"), "melt_and_sparkle" to m("reactivity"),
            "sparkle" to m("density"), "random_flash" to m("hit_probability_per_sec"), "blend" to m("amount"),
            "texter2d" to m("multiplier"), "wled_blink" to m("intensity"), "wled_lightning" to m("intensity"),
            "wled_gravfreq" to m("intensity"), "wled_blobs" to m("intensity"),
            "concentric" to m("power_multiplier"), "plasmawled" to m("size_multiplication"),
        ),
    )

    /** How each effect moves in time (see [Timing]); effects not listed follow the clock. */
    val TIMING: Map<String, Timing> =
        listOf("scroll", "water", "plasmawled").associateWith { Timing.STEPS } +
            listOf("bar", "multiBar", "strobe", "segment_chase", "effect_chase").associateWith { Timing.BEAT } +
            listOf(
                "energy", "spectrum", "wavelength", "magnitude", "pitchSpectrum", "blade_power_plus", "power", "rain",
                "sparkle", "hierarchy", "vumeter", "bands", "bands_matrix", "blocks", "equalizer", "equalizer2d", "matrix_test",
            ).associateWith { Timing.NONE }

    fun timing(effectId: String): Timing = TIMING[effectId] ?: Timing.CLOCK

    /** Effects that ignore Palette shift: they don't draw from the palette by position. */
    val NO_PALETTE_SHIFT = setOf(
        "energy", "spectrum", "scroll", "scroll_plus", "rain", "hierarchy", "vumeter", "rainbow", "singleColor",
        "random_flash", "wled_blobs", "matrix_test",
    )

    /** True if [target] does something on effect [effectId]. */
    fun affects(target: PadTarget, effectId: String): Boolean = when (target) {
        PadTarget.NONE -> false
        PadTarget.SPEED, PadTarget.SIZE, PadTarget.INTENSITY -> MACROS[target]?.containsKey(effectId) == true
        PadTarget.HUE, PadTarget.BRIGHTNESS, PadTarget.SATURATION, PadTarget.STROBE -> true
        PadTarget.PALETTE -> effectId !in NO_PALETTE_SHIFT
        PadTarget.TIME -> timing(effectId) != Timing.NONE
        PadTarget.COLOURS -> Effects.info(effectId).let { it.usesGradient || ownColourKeys(it).isNotEmpty() }
    }

    /**
     * What the pad can offer for lights running [effectIds]: each target that works on at least
     * one of them, with on how many it works. "Nothing" is always offered.
     */
    fun available(effectIds: List<String>): Map<PadTarget, Int> =
        PadTarget.entries.associateWith { t -> if (t == PadTarget.NONE) effectIds.size else effectIds.count { affects(t, it) } }
            .filter { (t, n) -> t == PadTarget.NONE || n > 0 }

    /** The axis target in use: the chosen one if it works on the running lights, otherwise Nothing. */
    fun effective(chosen: PadTarget, available: Map<PadTarget, Int>): PadTarget =
        if (chosen in available) chosen else PadTarget.NONE

    /** Around the centre the pad does nothing, so a touch near it leaves everything as saved. */
    const val DEAD_ZONE = 0.04f

    /** Higher = finer near the centre and coarser towards the edges. */
    const val CURVE = 2f

    /**
     * The finger position (0..1) as the pad uses it: the centre zone counts as the centre, and
     * small moves away from it change little, while the edges still reach 0 and 1.
     */
    fun shape(p: Float): Float {
        val d = p.coerceIn(0f, 1f) - 0.5f
        if (abs(d) <= DEAD_ZONE) return 0.5f
        val t = (abs(d) - DEAD_ZONE) / (0.5f - DEAD_ZONE)
        return 0.5f + sign(d) * t.pow(CURVE) * 0.5f
    }

    /**
     * The effect's settings with the pad applied: for each axis with an effect target, the mapped
     * settings move from the saved value (centre) towards their minimum (0) or maximum (1).
     */
    fun applyToConfig(info: EffectInfo, config: Map<String, Any>, pad: PadState): Map<String, Any> {
        var out = config
        for ((target, pos) in listOf(pad.xTarget to pad.x, pad.yTarget to pad.y)) {
            val macros = MACROS[target]?.get(info.id) ?: continue
            val shaped = shape(pos)
            for (mac in macros) {
                val param = info.params.firstOrNull { it.key == mac.key } ?: continue
                val p = if (mac.inverted) 1f - shaped else shaped
                val v: Any = when (param) {
                    is FloatParam -> around(num(config[param.key], param.default), param.min, param.max, p)
                    is IntParam -> around(num(config[param.key], param.default.toFloat()), param.min.toFloat(), param.max.toFloat(), p).roundToInt()
                    is ChoiceParam -> {
                        val base = param.options.indexOf(config[param.key] as? String ?: param.default).coerceAtLeast(0)
                        param.options[around(base.toFloat(), 0f, (param.options.size - 1).toFloat(), p).roundToInt()]
                    }
                    else -> continue
                }
                out = out + (param.key to v)
            }
        }
        return out
    }

    /** Centre = [base]; 0 = [min]; 1 = [max]; linear in between. */
    fun around(base: Float, min: Float, max: Float, p: Float): Float {
        val b = base.coerceIn(min, max)
        val q = p.coerceIn(0f, 1f)
        return if (q < 0.5f) min + (b - min) * (q / 0.5f) else b + (max - b) * ((q - 0.5f) / 0.5f)
    }

    private fun num(v: Any?, default: Float) = (v as? Number)?.toFloat() ?: default

    /**
     * The palettes in the order Colours goes through them: sorted by their main colour, so a
     * small move gives a similar palette. Palettes without one main colour (rainbows, greys) come last.
     */
    val PAD_PALETTES: List<Palette> by lazy { paletteOrder(Palettes.all) }

    fun paletteOrder(palettes: List<Palette>): List<Palette> {
        val keyed = palettes.map { p ->
            // Average the colours' hues as arrows, weighted by how colourful each one is.
            var x = 0.0; var y = 0.0
            for (k in 0 until 32) {
                val c = p.clamped(k / 31f)
                val (h, sat) = hueAndSaturation(c)
                x += cos(h * 2 * Math.PI) * sat; y += sin(h * 2 * Math.PI) * sat
            }
            val strength = hypot(x, y) / 32
            val hue = ((atan2(y, x) / (2 * Math.PI)) + 1.0) % 1.0
            Triple(p, strength >= 0.35, hue)
        }
        return keyed.filter { it.second }.sortedBy { it.third }.map { it.first } +
            keyed.filterNot { it.second }.map { it.first }
    }

    private fun hueAndSaturation(c: Int): Pair<Double, Double> {
        val r = Rgb.r(c) / 255.0; val g = Rgb.g(c) / 255.0; val b = Rgb.b(c) / 255.0
        val max = maxOf(r, g, b); val d = max - minOf(r, g, b)
        if (d == 0.0) return 0.0 to 0.0
        val h = when (max) {
            r -> ((g - b) / d).mod(6.0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } / 6
        return h to d // saturation × brightness: dark and pale colours count less
    }

    /**
     * Colours: the palette to use instead of the light's own ([ownId]), or null in the centre.
     * Moving away from the centre glides through [palettes] from the light's own towards the first
     * (left/down) or last (right/up), blending each one smoothly into the next.
     */
    fun pickPalette(pad: PadState, ownId: String, palettes: List<Palette> = PAD_PALETTES): Palette? {
        val raw = axes(pad, PadTarget.COLOURS).firstOrNull() ?: return null
        val p = shape(raw)
        if (p == 0.5f || palettes.isEmpty()) return null
        val own = palettes.indexOfFirst { it.id == ownId }
        val base = (if (own >= 0) own else palettes.size / 2).toFloat()
        val pos = around(base, 0f, (palettes.size - 1).toFloat(), p)
        val i = floor(pos).toInt().coerceIn(0, palettes.size - 1)
        val f = pos - i
        if (f < 0.02f || i == palettes.size - 1) return palettes[i]
        if (f > 0.98f) return palettes[i + 1]
        return blend(palettes[i], palettes[i + 1], f)
    }

    /** A palette part way ([f], 0..1) from [a] to [b]. */
    fun blend(a: Palette, b: Palette, f: Float): Palette =
        Palette("pad-blend", "Blend", List(16) { k -> Rgb.lerp(a.clamped(k / 15f), b.clamped(k / 15f), f) })

    /**
     * An effect's own colour settings (e.g. Single Color, Energy's lows/mids/highs, Texter's text)
     * taken from [color] (the picked palette at 0..1), spread across the palette. Background and
     * peak-marker colours stay as they are.
     */
    fun applyColours(info: EffectInfo, config: Map<String, Any>, color: (Float) -> Int): Map<String, Any> {
        val keys = ownColourKeys(info)
        if (keys.isEmpty()) return config
        return config + keys.mapIndexed { i, k -> k to color(if (keys.size == 1) 0.5f else i.toFloat() / (keys.size - 1)) }
    }

    /** The effect's own colour settings that Colours replaces (not background or peak markers). */
    private fun ownColourKeys(info: EffectInfo) = info.params.filterIsInstance<ColorParam>().map { it.key }
        .filter { !it.contains("background") && !it.contains("peak") }

    /** Overall speed: how fast time runs for the effects: 0.25x (low edge), 1x (centre), 4x (high edge). */
    fun timeScale(pad: PadState): Float = axes(pad, PadTarget.TIME).fold(1f) { f, p ->
        f * Math.pow(4.0, ((p.coerceIn(0f, 1f) - 0.5f) * 2f).toDouble()).toFloat()
    }

    /** Palette shift for the gradient (fraction of the palette), from the colour targets. */
    fun paletteShift(pad: PadState): Float = axes(pad, PadTarget.PALETTE).sumOf { (it - 0.5).toDouble() }.toFloat()

    /** Hue rotation in degrees (centre 0, edges ±180). */
    fun hueShift(pad: PadState): Float = axes(pad, PadTarget.HUE).sumOf { (it - 0.5) * 360.0 }.toFloat()

    /** Brightness factor: 0 at the low edge, 1 in the centre, 2 at the high edge. */
    fun brightness(pad: PadState): Float = axes(pad, PadTarget.BRIGHTNESS).fold(1f) { f, p -> f * 2f * p.coerceIn(0f, 1f) }

    /** Saturation change, -1..1: below 0 towards grey (−1 = no colour), above 0 towards full colour. */
    fun saturation(pad: PadState): Float =
        axes(pad, PadTarget.SATURATION).sumOf { (it - 0.5) * 2.0 }.toFloat().coerceIn(-1f, 1f)

    /** Strobe left of the centre: flashes per second, from slow (near the centre) to fast. */
    const val STROBE_MIN_HZ = 2f
    const val STROBE_MAX_HZ = 20f

    /** Strobe right of the centre: flashes per beat, in steps from the centre outwards. */
    val STROBE_PER_BEAT = listOf(1, 2, 4, 8)

    /** Part of each flash period the lights are on. */
    const val STROBE_ON = 0.3f

    /**
     * Strobe: true while the lights are on. Left of the centre it flashes at a steady rate
     * ([timeSec] is the engine's clock, so all lights flash together); right of it, on the beat
     * ([beatPhase] 0..1 within the beat). In the centre the lights stay on.
     */
    fun strobeOpen(pad: PadState, timeSec: Double, beatPhase: Float): Boolean {
        val p = axes(pad, PadTarget.STROBE).firstOrNull() ?: return true
        if (p == 0.5f) return true
        val t = (abs(p - 0.5f) * 2f).coerceIn(0f, 1f)
        val phase = if (p < 0.5f) {
            val hz = STROBE_MIN_HZ + (STROBE_MAX_HZ - STROBE_MIN_HZ) * t
            (timeSec * hz).let { (it - floor(it)).toFloat() }
        } else {
            val per = STROBE_PER_BEAT[(t * STROBE_PER_BEAT.size).toInt().coerceAtMost(STROBE_PER_BEAT.size - 1)]
            (beatPhase * per).let { it - floor(it) }
        }
        return phase < STROBE_ON
    }

    /** Positions of the axes set to [t], shaped (see [shape]). */
    private fun axes(pad: PadState, t: PadTarget) =
        listOfNotNull(pad.x.takeIf { pad.xTarget == t }, pad.y.takeIf { pad.yTarget == t }).map { shape(it) }

    /**
     * Rotates a colour's hue by [degrees], changes its saturation by [sat] (see [saturation]) and
     * scales it by [bright] (each channel clamped at full).
     */
    fun recolor(c: Int, degrees: Float, bright: Float, sat: Float = 0f): Int {
        if (c == 0) return 0
        var out = c
        if (degrees != 0f || sat != 0f) {
            val r = Rgb.r(c) / 255f; val g = Rgb.g(c) / 255f; val b = Rgb.b(c) / 255f
            val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
            var h = when {
                d == 0f -> 0f
                max == r -> ((g - b) / d).mod(6f)
                max == g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
            h = (h + degrees / 360f).mod(1f)
            var s = if (max == 0f) 0f else d / max
            // Grey has no colour to strengthen, so it stays grey.
            if (sat < 0f) s *= 1f + sat else if (s > 0f) s += (1f - s) * sat
            out = Rgb.hsv(h, s.coerceIn(0f, 1f), max)
        }
        if (bright != 1f) {
            out = Rgb.of((Rgb.r(out) * bright).roundToInt(), (Rgb.g(out) * bright).roundToInt(), (Rgb.b(out) * bright).roundToInt())
        }
        return out
    }
}
