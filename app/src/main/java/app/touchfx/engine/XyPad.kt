package app.touchfx.engine

import app.touchfx.effects.ChoiceParam
import app.touchfx.effects.ColorParam
import app.touchfx.effects.EffectInfo
import app.touchfx.effects.FloatParam
import app.touchfx.effects.IntParam
import app.touchfx.effects.Rgb
import kotlin.math.roundToInt

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
    TIME("Overall speed");

    /** Targets that work on every effect (colours, the finished picture, time itself). */
    val isColor get() = this == HUE || this == PALETTE || this == BRIGHTNESS || this == COLOURS || this == TIME
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

    /** True if [target] does something on effect [effectId]. */
    fun affects(target: PadTarget, effectId: String) =
        target.isColor || MACROS[target]?.containsKey(effectId) == true

    /**
     * The effect's settings with the pad applied: for each axis with an effect target, the mapped
     * settings move from the saved value (centre) towards their minimum (0) or maximum (1).
     */
    fun applyToConfig(info: EffectInfo, config: Map<String, Any>, pad: PadState): Map<String, Any> {
        var out = config
        for ((target, pos) in listOf(pad.xTarget to pad.x, pad.yTarget to pad.y)) {
            val macros = MACROS[target]?.get(info.id) ?: continue
            for (mac in macros) {
                val param = info.params.firstOrNull { it.key == mac.key } ?: continue
                val p = if (mac.inverted) 1f - pos else pos
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

    /** Within this distance of the centre, Colours keeps each light's own colours. */
    const val COLOUR_DEAD_ZONE = 0.04f

    /**
     * Colours: the palette to use instead of the light's own, or null near the centre. Moving away
     * from the centre steps through [palettes] from the light's own one ([baseIndex]) towards the
     * first (left/down) or last (right/up).
     */
    fun <P> pickPalette(pad: PadState, palettes: List<P>, baseIndex: Int): P? {
        val p = axes(pad, PadTarget.COLOURS).firstOrNull() ?: return null
        if (kotlin.math.abs(p - 0.5f) < COLOUR_DEAD_ZONE || palettes.isEmpty()) return null
        val base = baseIndex.coerceIn(0, palettes.size - 1).toFloat()
        return palettes[around(base, 0f, (palettes.size - 1).toFloat(), p).roundToInt()]
    }

    /**
     * An effect's own colour settings (e.g. Single Color, Energy's lows/mids/highs, Texter's text)
     * taken from [color] (the picked palette at 0..1), spread across the palette. Background and
     * peak-marker colours stay as they are.
     */
    fun applyColours(info: EffectInfo, config: Map<String, Any>, color: (Float) -> Int): Map<String, Any> {
        val keys = info.params.filterIsInstance<ColorParam>().map { it.key }
            .filter { !it.contains("background") && !it.contains("peak") }
        if (keys.isEmpty()) return config
        return config + keys.mapIndexed { i, k -> k to color(if (keys.size == 1) 0.5f else i.toFloat() / (keys.size - 1)) }
    }

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

    private fun axes(pad: PadState, t: PadTarget) = listOfNotNull(pad.x.takeIf { pad.xTarget == t }, pad.y.takeIf { pad.yTarget == t })

    /** Rotates a colour's hue by [degrees] and scales it by [bright] (each channel clamped at full). */
    fun recolor(c: Int, degrees: Float, bright: Float): Int {
        if (c == 0) return 0
        var out = c
        if (degrees != 0f) {
            val r = Rgb.r(c) / 255f; val g = Rgb.g(c) / 255f; val b = Rgb.b(c) / 255f
            val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
            var h = when {
                d == 0f -> 0f
                max == r -> ((g - b) / d).mod(6f)
                max == g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
            h = (h + degrees / 360f).mod(1f)
            out = Rgb.hsv(h, if (max == 0f) 0f else d / max, max)
        }
        if (bright != 1f) {
            out = Rgb.of((Rgb.r(out) * bright).roundToInt(), (Rgb.g(out) * bright).roundToInt(), (Rgb.b(out) * bright).roundToInt())
        }
        return out
    }
}
