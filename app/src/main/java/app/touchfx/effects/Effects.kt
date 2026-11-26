package app.touchfx.effects

import app.touchfx.effects.EffectCategory.AMBIENT
import app.touchfx.effects.EffectCategory.ATMOSPHERIC
import app.touchfx.effects.EffectCategory.BANDS
import app.touchfx.effects.EffectCategory.BPM
import app.touchfx.effects.EffectCategory.CLASSIC
import app.touchfx.effects.EffectCategory.EXTRA
import app.touchfx.effects.EffectCategory.MATRIX
import app.touchfx.effects.EffectCategory.WLED
import app.touchfx.effects.EffectCategory.SIMPLE

/**
 * The effect catalogue. Ids and setting keys follow LedFx so its presets apply directly.
 * Implementations live in EffectsClassic.kt, EffectsAtmospheric.kt, EffectsMore.kt, Effects2D.kt and Wled.kt.
 */
object Effects {
    private val roll = FloatParam("gradient_roll", "Gradient roll", 0f, 10f, 0f)
    private val modulation = listOf(
        BoolParam("modulate", "Modulate", false),
        ChoiceParam("modulation_effect", "Modulation", listOf("sine", "breath"), "sine"),
        FloatParam("modulation_speed", "Modulation speed", 0.01f, 1f, 0.5f),
    )
    private fun range(default: String = "Lows (beat+bass)") =
        ChoiceParam("frequency_range", "Frequency range", FREQUENCY_RANGES, default)
    private val easing = listOf("ease_in_out", "ease_in", "ease_out", "linear")
    val STROBE_FREQUENCIES = listOf("1/1 (.,. )", "1/2 (.-. )", "1/4 (.o. )", "1/8 (◉◡◉ )", "1/16 (◉﹏◉ )", "1/32 (⊙▃⊙ )")

    val all: List<EffectInfo> = listOf(
        // ---------------------------------------------------------------- Classic
        EffectInfo("energy", "Energy", CLASSIC, "Bass, mids and highs grow as coloured bars", listOf(
            ColorParam("color_lows", "Lows colour", 0xFF0000),
            ColorParam("color_mids", "Mids colour", 0x00FF00),
            ColorParam("color_high", "Highs colour", 0x0000FF),
            FloatParam("sensitivity", "Sensitivity", 0.3f, 0.99f, 0.6f),
            ChoiceParam("mixing_mode", "Mixing", listOf("additive", "overlap"), "additive"),
            BoolParam("color_cycler", "New colours on beats", false),
        ) + commonParams(blur = 4f, mirror = true), usesGradient = false) { Energy() },
        EffectInfo("spectrum", "Spectrum", CLASSIC, "Each colour channel shows a different view of the spectrum", listOf(
            IntParam("rgb_mix", "Colour mix", 0, 5, 0),
        ) + commonParams(), usesGradient = false) { Spectrum() },
        EffectInfo("wavelength", "Wavelength", CLASSIC, "Gradient lit up by the frequencies under each LED",
            listOf(roll) + commonParams(blur = 3f)) { Wavelength() },
        EffectInfo("scroll", "Scroll", CLASSIC, "Lows, mids and highs scroll down the strip", listOf(
            IntParam("speed", "Speed", 1, 10, 3),
            FloatParam("decay", "Decay", 0.8f, 1f, 0.97f),
            FloatParam("threshold", "Threshold", 0f, 1f, 0f),
            ColorParam("color_lows", "Lows colour", 0xFF0000),
            ColorParam("color_mids", "Mids colour", 0x00FF00),
            ColorParam("color_high", "Highs colour", 0x0000FF),
        ) + commonParams(blur = 3f, mirror = true), usesGradient = false) { Scroll() },
        EffectInfo("scroll_plus", "Scroll+", CLASSIC, "Smoother, time-based Scroll", listOf(
            FloatParam("scroll_per_sec", "Scroll speed", 0.01f, 2f, 0.7f),
            FloatParam("decay_per_sec", "Decay", 0f, 2f, 0.5f),
            FloatParam("threshold", "Threshold", 0f, 1f, 0f),
            ColorParam("color_lows", "Lows colour", 0xFF0000),
            ColorParam("color_mids", "Mids colour", 0x00FF00),
            ColorParam("color_high", "Highs colour", 0x0000FF),
        ) + commonParams(blur = 3f, mirror = true), usesGradient = false) { ScrollPlus() },
        EffectInfo("power", "Power", CLASSIC, "Spectrum background, bass bar and sparks on hits", listOf(
            ColorParam("sparks_color", "Sparks colour", 0xFFFFFF),
            FloatParam("bass_decay_rate", "Bass decay", 0f, 1f, 0.05f),
            FloatParam("sparks_decay_rate", "Sparks decay", 0f, 1f, 0.15f),
            roll,
        ) + commonParams(mirror = true)) { Power() },
        EffectInfo("magnitude", "Magnitude", CLASSIC, "Whole gradient brightens with one frequency range",
            listOf(range(), roll) + commonParams()) { Magnitude() },
        EffectInfo("pitchSpectrum", "Pitch Spectrum", CLASSIC, "Colour follows the musical note being played", listOf(
            FloatParam("fade_rate", "Fade rate", 0f, 1f, 0.15f),
            FloatParam("responsiveness", "Responsiveness", 0f, 1f, 0.15f),
            roll,
        ) + commonParams(blur = 1f, mirror = true)) { PitchSpectrum() },
        EffectInfo("blade_power_plus", "Blade Power+", CLASSIC, "Rainbow bar that shoots up with the music", listOf(
            FloatParam("decay", "Decay", 0f, 1f, 0.7f),
            FloatParam("multiplier", "Multiplier", 0f, 1f, 0.5f),
            ColorParam("background_color", "Background", 0x000000),
            range(), roll,
        ) + commonParams(blur = 2f)) { BladePowerPlus() },
        EffectInfo("rain", "Rain", CLASSIC, "Drops fall where lows, mids and highs hit", listOf(
            ColorParam("lows_color", "Lows colour", 0xFFFFFF),
            ColorParam("mids_color", "Mids colour", 0xFF0000),
            ColorParam("high_color", "Highs colour", 0x0000FF),
            ChoiceParam("pulse_strip", "Pulse whole strip on", listOf("Off", "Lows", "Mids", "Highs"), "Off"),
            FloatParam("lows_sensitivity", "Lows sensitivity", 0.03f, 0.3f, 0.1f),
            FloatParam("mids_sensitivity", "Mids sensitivity", 0.03f, 0.3f, 0.05f),
            FloatParam("high_sensitivity", "Highs sensitivity", 0.03f, 0.3f, 0.1f),
        ) + commonParams(mirror = true), usesGradient = false) { Rain() },
        EffectInfo("real_strobe", "Strobe", CLASSIC, "Bass flashes plus white strobes on hits", listOf(
            FloatParam("color_step", "Colour step", 0f, 0.25f, 0.0625f),
            FloatParam("bass_strobe_decay_rate", "Bass strobe decay", 0f, 1f, 0.5f),
            ColorParam("strobe_color", "Strobe colour", 0xFFFFFF),
            IntParam("strobe_width", "Strobe width (LEDs)", 0, 1000, 10),
            FloatParam("strobe_decay_rate", "Strobe decay", 0f, 1f, 0.5f),
            FloatParam("color_shift_delay", "Colour shift delay (s)", 0f, 1f, 1f),
        ) + commonParams()) { RealStrobe() },
        EffectInfo("scan", "Scan", CLASSIC, "Blocks sweep back and forth, pushed by the music", listOf(
            BoolParam("bounce", "Bounce", true),
            IntParam("scan_width", "Width (%)", 1, 100, 30),
            IntParam("speed", "Speed", 0, 100, 50),
            ColorParam("color_scan", "Colour", 0xFF0000),
            range(),
            FloatParam("multiplier", "Multiplier", 0f, 5f, 3f),
            BoolParam("color_intensity", "Brightness follows music", true),
            BoolParam("use_grad", "Use gradient", false),
            BoolParam("full_grad", "Full gradient", false),
            IntParam("count", "Count", 1, 10, 1),
        ) + commonParams(blur = 3f)) { Scan(false) },
        EffectInfo("scan_and_flare", "Scan and Flare", CLASSIC, "Scan that throws off white flares on peaks", listOf(
            BoolParam("bounce", "Bounce", true),
            IntParam("scan_width", "Width (%)", 1, 100, 30),
            IntParam("speed", "Speed", 0, 100, 50),
            IntParam("sparkles_max", "Max flares", 1, 20, 10),
            FloatParam("sparkles_size", "Flare size", 0.01f, 0.3f, 0.1f),
            FloatParam("sparkles_time", "Flare time (s)", 0.01f, 2f, 1f),
            FloatParam("sparkles_threshold", "Flare threshold", 0.1f, 0.9f, 0.6f),
            ColorParam("color_scan", "Colour", 0xFF0000),
            range(),
            FloatParam("multiplier", "Multiplier", 0f, 5f, 3f),
            BoolParam("color_intensity", "Brightness follows music", true),
            BoolParam("use_grad", "Use gradient", false),
        ) + commonParams(blur = 3f)) { Scan(true) },
        EffectInfo("scan_multi", "Scan Multi", CLASSIC, "Three scanners for lows, mids and highs", listOf(
            BoolParam("bounce", "Bounce", true),
            IntParam("scan_width", "Width (%)", 1, 100, 30),
            IntParam("speed", "Speed", 0, 100, 50),
            ColorParam("color_low", "Lows colour", 0xFF0000),
            ColorParam("color_mid", "Mids colour", 0x00FF00),
            ColorParam("color_high", "Highs colour", 0x0000FF),
            FloatParam("multiplier", "Multiplier", 0f, 5f, 3f),
            BoolParam("color_intensity", "Brightness follows music", true),
            BoolParam("use_grad", "Use gradient", false),
            ChoiceParam("input_source", "Input", listOf("Power", "Melbank"), "Power"),
            FloatParam("attack", "Attack", 0.01f, 0.99f, 0.9f),
            FloatParam("decay", "Decay", 0.01f, 0.99f, 0.7f),
            BoolParam("filter", "Smooth input", false),
        ) + commonParams(blur = 3f)) { ScanMulti() },
        EffectInfo("spotlight", "Spotlight", CLASSIC, "Club-style spots that pop up with the music", listOf(
            FloatParam("spot_width", "Spot width (%)", 0.5f, 100f, 8f),
            FloatParam("fade_time", "Fade time (s)", 0.05f, 8f, 0.8f),
            IntParam("max_active_spots", "Max spots", 1, 128, 28),
            BoolParam("use_gradient", "Use gradient", true),
            FloatParam("gradient_speed", "Gradient speed", 0f, 3f, 0.12f),
            FloatParam("spot_color_span", "Colour spread", 0f, 1f, 0.08f),
            ColorParam("center_color", "Centre colour", 0xFFFFFF),
            ColorParam("edge_color", "Edge colour", 0x4AA3FF),
        ) + commonParams()) { Spotlight() },

        // ---------------------------------------------------------------- Beat synced
        EffectInfo("bar", "Bar", BPM, "A bar wipes or bounces in time with the beat", listOf(
            ChoiceParam("mode", "Mode", listOf("bounce", "wipe", "in-out"), "wipe"),
            ChoiceParam("ease_method", "Easing", easing, "ease_out"),
            FloatParam("color_step", "Colour step", 0.0625f, 0.5f, 0.125f),
            ChoiceParam("beat_skip", "Skip beats", listOf("none", "odds", "even"), "none"),
            FloatParam("beat_offset", "Beat offset", 0f, 1f, 0f),
            ChoiceParam("skip_every", "Skip every", listOf("1", "2"), "1"),
            roll,
        ) + commonParams()) { Bar() },
        EffectInfo("multiBar", "Multicolor Bar", BPM, "Colours wipe across the strip each beat", listOf(
            ChoiceParam("mode", "Mode", listOf("cascade", "wipe"), "wipe"),
            ChoiceParam("ease_method", "Easing", easing, "linear"),
            FloatParam("color_step", "Colour step", 0.0625f, 0.5f, 0.125f),
            roll,
        ) + commonParams()) { MultiBar() },
        EffectInfo("strobe", "BPM Strobe", BPM, "Strobe locked to the tempo", listOf(
            ChoiceParam("strobe_frequency", "Frequency", STROBE_FREQUENCIES, STROBE_FREQUENCIES[1]),
            FloatParam("strobe_decay", "Strobe decay", 1f, 10f, 1.5f),
            FloatParam("beat_decay", "Beat decay", 0f, 10f, 2f),
            ChoiceParam("strobe_pattern", "Pattern", listOf("****", "*.*.", ".*.*", "*...", "...*"), "****"),
            roll,
        ) + commonParams()) { BpmStrobe() },
        EffectInfo("segment_chase", "Segment Chase", BPM,
            "Lights a group's devices (or parts of a strip) one after another, in time with the beat", listOf(
            ChoiceParam("mode", "Mode", listOf("Chase", "Fill", "Colour shift"), "Chase"),
            ChoiceParam("step_on", "Step on", SegmentChase.STEP_ON, "Tempo"),
            ChoiceParam("speed", "Speed", SegmentChase.SPEEDS.keys.toList(), "1 per beat"),
            FloatParam("cycle_length", "Cycle length in beats (\"runs a cycle\")", 0.1f, 2f, 0.5f),
            ChoiceParam("order", "Order", listOf("Forward", "Backward", "Bounce", "Random"), "Forward"),
            FloatParam("trail", "Trail (fade out)", 0f, 1f, 0.3f),
            BoolParam("one_color", "One colour instead of the gradient", false),
            ColorParam("color", "Colour", 0xFF0050),
            IntParam("parts", "Parts on a single strip", 2, 16, 4),
            roll,
        )) { SegmentChase() },
        EffectInfo("effect_chase", "Effect Chase", BPM,
            "Like Segment Chase, but the parts show other effects instead of a colour", listOf(
            EffectListParam("effects", "Effects shown in the parts", "rainbow,fire"),
            ChoiceParam("effect_per", "Which effect a part shows", listOf("Each step", "Per part"), "Each step"),
            ChoiceParam("mode", "Mode", listOf("Chase", "Fill", "Effect shift"), "Chase"),
            ChoiceParam("step_on", "Step on", SegmentChase.STEP_ON, "Tempo"),
            ChoiceParam("speed", "Speed", SegmentChase.SPEEDS.keys.toList(), "1 per beat"),
            FloatParam("cycle_length", "Cycle length in beats (\"runs a cycle\")", 0.1f, 2f, 0.5f),
            ChoiceParam("order", "Order", listOf("Forward", "Backward", "Bounce", "Random"), "Forward"),
            FloatParam("trail", "Trail (fade out)", 0f, 1f, 0.3f),
            IntParam("parts", "Parts on a single strip", 2, 16, 4),
        ), composite = true) { EffectChase() },

        // ---------------------------------------------------------------- Atmospheric
        EffectInfo("fire", "Fire", ATMOSPHERIC, "Flames fed by the bass", listOf(
            FloatParam("speed", "Speed", 0.00001f, 0.5f, 0.04f),
            FloatParam("color_shift", "Colour shift", 0f, 1f, 0.15f),
            IntParam("intensity", "Intensity", 1, 30, 8),
            FloatParam("fade_chance", "Fade chance", 0.05f, 1f, 0.5f),
            roll,
        ) + commonParams()) { Fire() },
        EffectInfo("block_reflections", "Block Reflections", ATMOSPHERIC, "Shifting reflected blocks of colour", listOf(
            FloatParam("speed", "Speed", 0.00001f, 1f, 0.5f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 1f, 0.5f), roll,
        ) + commonParams()) { BlockReflections() },
        EffectInfo("crawler", "Crawler", ATMOSPHERIC, "Crawling, swaying waves", listOf(
            FloatParam("speed", "Speed", 0.00001f, 1f, 0.5f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 1f, 0.25f),
            FloatParam("sway", "Sway", 0.00001f, 50f, 20f),
            FloatParam("chop", "Chop", 0.00001f, 100f, 30f),
            FloatParam("stretch", "Stretch", 0.00001f, 10f, 2.5f), roll,
        ) + commonParams()) { Crawler() },
        EffectInfo("energy2", "Energy 2", ATMOSPHERIC, "Pulsing waves pushed by the bass", listOf(
            FloatParam("speed", "Speed", 0.00001f, 1f, 0.1f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 1f, 0.2f), roll,
        ) + commonParams()) { Energy2() },
        EffectInfo("glitch", "Glitch", ATMOSPHERIC, "Jittery digital patterns", listOf(
            FloatParam("speed", "Speed", 0.00001f, 10f, 0.5f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 1f, 0.2f),
            FloatParam("saturation_threshold", "Saturation", 0f, 1f, 1f), roll,
        ) + commonParams()) { Glitch() },
        EffectInfo("lava_lamp", "Lava lamp", ATMOSPHERIC, "Slow blobs of colour", listOf(
            FloatParam("speed", "Speed", 0.1f, 15f, 7f),
            FloatParam("contrast", "Contrast", 0f, 1f, 0.6f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 0.9f, 0.3f), roll,
        ) + commonParams()) { LavaLamp() },
        EffectInfo("marching", "Marching", ATMOSPHERIC, "Bands of colour marching along", listOf(
            FloatParam("speed", "Speed", 0.00001f, 1f, 0.1f),
            FloatParam("reactivity", "Reactivity", 0.00001f, 1f, 0.2f), roll,
        ) + commonParams()) { Marching() },
        EffectInfo("melt", "Melt", ATMOSPHERIC, "Melting, flowing colour", listOf(
            FloatParam("speed", "Speed", 0.001f, 1f, 0.5f),
            FloatParam("reactivity", "Reactivity", 0.0001f, 1f, 0.5f), roll,
        ) + commonParams()) { Melt() },
        EffectInfo("melt_and_sparkle", "Melt and Sparkle", ATMOSPHERIC, "Melt with strobe sparkles on hits", listOf(
            FloatParam("speed", "Speed", 0.001f, 1f, 0.5f),
            FloatParam("reactivity", "Reactivity", 0.0001f, 1f, 0.5f),
            FloatParam("bg_bright", "Background brightness", 0f, 1f, 0.4f),
            FloatParam("lava_width", "Lava width", 0f, 1f, 0.5f),
            FloatParam("strobe_threshold", "Strobe threshold", 0f, 1f, 0.75f),
            FloatParam("strobe_rate", "Strobe rate", 0f, 1f, 0.75f),
            FloatParam("strobe_width", "Strobe width", 0f, 1f, 0.3f),
            FloatParam("strobe_decay_rate", "Strobe decay", 0f, 1f, 0.25f),
            FloatParam("strobe_blur", "Strobe blur", 0f, 10f, 3.5f), roll,
        ) + commonParams()) { MeltAndSparkle() },
        EffectInfo("water", "Water", ATMOSPHERIC, "Ripples on water, dropped in by the music", listOf(
            FloatParam("speed", "Speed", 1f, 3f, 1f),
            FloatParam("vertical_shift", "Vertical shift", -0.2f, 1f, 0.12f),
            FloatParam("bass_size", "Bass drop size", 0f, 15f, 8f),
            FloatParam("mids_size", "Mids drop size", 0f, 15f, 6f),
            FloatParam("high_size", "Highs drop size", 0f, 15f, 3f),
            FloatParam("viscosity", "Viscosity", 2f, 12f, 6f), roll,
        ) + commonParams()) { Water() },

        // ---------------------------------------------------------------- Simple
        EffectInfo("filter", "Filter", SIMPLE, "Whole strip glows with one frequency range", listOf(
            ColorParam("color", "Colour", 0xFF0000),
            range(),
            BoolParam("use_gradient", "Use gradient", false),
            FloatParam("roll_speed", "Gradient cycle speed", 0f, 1f, 0f),
            FloatParam("boost", "Boost", 0f, 1f, 0f),
        ) + commonParams()) { Filter() },
        EffectInfo("hierarchy", "Hierarchy", SIMPLE, "Bass wins, then mids, then highs pick the colour", listOf(
            ColorParam("color_lows", "Lows colour", 0xFF0000),
            ColorParam("color_mids", "Mids colour", 0x00FF00),
            ColorParam("color_high", "Highs colour", 0x0000FF),
            FloatParam("brightness_boost", "Brightness boost", 0f, 1f, 0f),
            FloatParam("threshold_lows", "Lows threshold", 0f, 1f, 0.05f),
            FloatParam("threshold_mids", "Mids threshold", 0f, 1f, 0.05f),
            FloatParam("switch_time", "Switch time (s)", 0f, 1f, 0.1f),
        ) + commonParams(), usesGradient = false) { Hierarchy() },
        EffectInfo("vumeter", "VU Meter", SIMPLE, "Classic volume meter with peak markers", listOf(
            FloatParam("peak_decay", "Peak decay", 0.01f, 0.3f, 0.1f),
            ColorParam("color_min", "Low colour", 0x0000FF),
            ColorParam("color_mid", "Mid colour", 0x00FF00),
            ColorParam("color_max", "High colour", 0xFF0000),
            ColorParam("color_peak", "Peak colour", 0xFFFFFF),
            IntParam("peak_percent", "Peak size (%)", 0, 5, 1),
            FloatParam("max_volume", "Red zone from", 0f, 1f, 0.8f),
        ) + commonParams(), usesGradient = false) { VuMeter() },

        // ---------------------------------------------------------------- Bands
        EffectInfo("bands", "Bands", BANDS, "Strip split into bands that fill with their frequencies", listOf(
            IntParam("band_count", "Bands", 1, 16, 6),
            ChoiceParam("align", "Align", listOf("left", "right", "invert", "center"), "left"), roll,
        ) + commonParams()) { Bands() },
        EffectInfo("bands_matrix", "Bands Matrix", BANDS, "Bands with a gradient inside each, alternating direction", listOf(
            IntParam("band_count", "Bands", 1, 16, 6),
            BoolParam("flip_gradient", "Flip gradient", false),
            BoolParam("flip_horizontal", "Flip band order", false), roll,
        ) + commonParams()) { BandsMatrix() },
        EffectInfo("blocks", "Blocks", BANDS, "Coloured blocks that pulse with their part of the spectrum", listOf(
            IntParam("block_count", "Blocks", 1, 10, 4), roll,
        ) + commonParams()) { Blocks() },
        EffectInfo("equalizer", "Equalizer", BANDS, "Graphic equalizer across the gradient", listOf(
            ChoiceParam("align", "Align", listOf("left", "right", "invert", "center"), "left"),
            IntParam("gradient_repeat", "Sections", 1, 16, 6), roll,
        ) + commonParams()) { Equalizer() },

        // ---------------------------------------------------------------- No audio
        EffectInfo("gradient", "Gradient", AMBIENT, "Static or rolling gradient",
            listOf(roll) + modulation + commonParams()) { GradientFx() },
        EffectInfo("fade", "Fade", AMBIENT, "Whole strip fades through the gradient", listOf(
            FloatParam("speed", "Speed", 0.1f, 10f, 0.5f), roll,
        ) + commonParams()) { Fade() },
        EffectInfo("rainbow", "Rainbow", AMBIENT, "Classic moving rainbow", listOf(
            FloatParam("speed", "Speed", 0.1f, 20f, 1f),
            FloatParam("frequency", "Frequency", 0.1f, 64f, 1f),
        ) + commonParams(), usesGradient = false) { Rainbow() },
        EffectInfo("singleColor", "Single Color", AMBIENT, "One solid colour", listOf(
            ColorParam("color", "Colour", 0xFF0000),
        ) + modulation + commonParams(), usesGradient = false) { SingleColor() },
        EffectInfo("random_flash", "Random Flash", AMBIENT, "Random lightning-like flashes, or a steady strobe", listOf(
            ChoiceParam("timing", "Timing", listOf("Random", "Fixed rate"), "Random"),
            FloatParam("strobe_rate", "Strobe rate (flashes per second, Fixed rate)", 0.5f, 25f, 4f),
            ColorParam("hit_color", "Flash colour", 0xFFFFFF),
            FloatParam("hit_duration", "Flash duration (s)", 0.02f, 5f, 0.5f),
            FloatParam("hit_probability_per_sec", "Flash chance (Random)", 0.01f, 1f, 0.1f),
            IntParam("hit_relative_size", "Flash size (%)", 1, 100, 10),
        ) + commonParams(), usesGradient = false) { RandomFlash() },

        // ---------------------------------------------------------------- Extras (not in LedFx)
        EffectInfo("ripples", "Ripples", EXTRA, "Beats send out expanding ripples", listOf(
            FloatParam("speed", "Speed", 0f, 1f, 0.5f),
            FloatParam("glow", "Background glow", 0f, 1f, 0.5f), roll,
        ) + commonParams()) { Ripples() },
        EffectInfo("blend", "Blend", EXTRA, "Puts one effect over another (e.g. text over a plasma)", listOf(
            EffectListParam("background", "Background effect", "rainbow", maxCount = 1),
            EffectListParam("foreground", "Foreground effect", "ripples", maxCount = 1),
            ChoiceParam("mode", "Blend mode", Blend.MODES, "Over"),
            FloatParam("amount", "Amount (how strongly the foreground shows)", 0f, 1f, 1f),
            EffectListParam("mask", "Mask (Over mode)", "", maxCount = 1, emptyLabel = "The foreground's own brightness"),
            FloatParam("mask_cutoff", "Mask cutoff (1 = soft, lower = hard edge)", 0.01f, 1f, 1f),
            BoolParam("swap", "Swap foreground and background", false),
        ), composite = true) { Blend() },
        EffectInfo("sparkle", "Sparkle", EXTRA, "Twinkles driven by the highs", listOf(
            FloatParam("density", "Density", 0f, 1f, 0.5f),
            FloatParam("decay", "Decay speed", 0f, 1f, 0.5f), roll,
        ) + commonParams()) { Sparkle() },

        // ---------------------------------------------------------------- From WLED (Wled.kt)
        EffectInfo("wled_blink", "Blink", WLED, "WLED: the palette blinks on and off", wledParams(
            intensity = "Duty cycle") + commonParams(), audioReactive = false) { WledStrip(WledBlink(rainbow = false)) },
        EffectInfo("wled_blink_rainbow", "Blink Rainbow", WLED, "WLED: blinks, moving through the palette's colours", wledParams(
            speed = "Frequency", intensity = "Blink duration") + commonParams(), audioReactive = false) { WledStrip(WledBlink(rainbow = true)) },
        EffectInfo("wled_bpm", "Bpm", WLED, "WLED: palette stripes pulsing at a set BPM", wledParams(
            speed = "Speed (BPM)", sx = 64) + commonParams(), audioReactive = false) { WledStrip(WledBpm()) },
        EffectInfo("wled_lightning", "Lightning", WLED, "WLED: leader flash, then bright strikes, then a pause", wledParams(
            speed = "Speed (shorter pauses)", intensity = "Flashes per strike") + commonParams(), audioReactive = false) { WledStrip(WledLightning()) },
        EffectInfo("wled_gravfreq", "Gravfreq", WLED, "WLED: volume bar from the centre with falling peaks, coloured by pitch", wledParams(
            speed = "Rate of fall", intensity = "Sensitivity") + commonParams()) { WledStrip(WledGravfreq()) },
        EffectInfo("wled_puddlepeak", "Puddlepeak", WLED, "WLED: a splash of colour on each beat peak", wledParams(
            speed = "Fade rate", intensity = "Puddle size", custom2 = "Volume (min)", c2 = 0) + commonParams()) { WledStrip(WledPuddlepeak()) },

        // ---------------------------------------------------------------- Matrix (2D)
        EffectInfo("wled_blobs", "Blobs", MATRIX, "WLED: soft blobs that grow, shrink and bounce", wledParams(
            intensity = "# blobs", custom1 = "Blur", custom2 = "Trail", c1 = 8) + twodParams(),
            audioReactive = false) { WledMatrix(WledBlobs()) },
        EffectInfo("wled_octopus", "Octopus", MATRIX, "WLED: swirling tentacles from a centre", wledParams(
            custom1 = "Offset X", custom2 = "Offset Y", custom3 = "Legs") + twodParams(),
            audioReactive = false) { WledMatrix(WledOctopus()) },
        EffectInfo("wled_soap", "Soap", MATRIX, "WLED: flowing soap-film colours", wledParams(
            intensity = "Smoothness", custom1 = "Density") + twodParams(),
            audioReactive = false) { WledMatrix(WledSoap()) },
        EffectInfo("wled_tartan", "Tartan", MATRIX, "WLED: woven, drifting plaid", wledParams(
            speed = "X scale", intensity = "Y scale", custom3 = "Sharpness") + twodParams(),
            audioReactive = false) { WledMatrix(WledTartan()) },

        EffectInfo("plasma2d", "Plasma2d", MATRIX, "Flowing plasma that zooms with the music", listOf(
            range(),
            FloatParam("density_vertical", "Vertical density", 0.01f, 0.3f, 0.1f),
            FloatParam("twist", "Twist", 0.01f, 0.3f, 0.07f),
            FloatParam("radius", "Radius", 0.01f, 1f, 0.2f),
            FloatParam("density", "Density", 0.001f, 2f, 0.5f),
            FloatParam("lower", "Lower density", 0.01f, 1f, 0.01f),
        ) + twodParams()) { Plasma2d() },
        EffectInfo("concentric", "Concentric", MATRIX, "Rings of colour flowing from the centre", listOf(
            range(),
            BoolParam("invert", "Flow inwards", false),
            FloatParam("power_multiplier", "Music push", 0f, 1f, 0.5f),
            FloatParam("gradient_scale", "Gradient scale", 0.1f, 10f, 1f),
            FloatParam("stretch_height", "Stretch vertically", 0.1f, 5f, 1f),
            FloatParam("center_smoothing", "Soften the centre", 0f, 5f, 0.5f),
            FloatParam("idle_speed", "Idle speed", 0f, 1f, 1f),
            roll,
        ) + twodParams()) { Concentric2d() },
        EffectInfo("plasmawled", "PlasmaWled2d", MATRIX, "WLED's classic plasma, sized and sped by the music", listOf(
            range(),
            IntParam("speed", "Speed divider (higher = slower)", 0, 255, 128),
            IntParam("stretch_horizontal", "Stretch rows", 0, 255, 128),
            IntParam("stretch_vertical", "Stretch columns", 0, 255, 128),
            FloatParam("size_multiplication", "Music to size", 0f, 1f, 0.4f),
            FloatParam("speed_multiplication", "Music to speed (0 = steady clock)", 0f, 1f, 0.4f),
        ) + twodParams()) { PlasmaWled2d() },
        EffectInfo("texter2d", "Texter", MATRIX, "Scrolling text, optionally pushed by the bass", listOf(
            TextParam("text", "Text", "Your text here"),
            FloatParam("speed_option_1", "Speed (0 = still, centred)", 0f, 3f, 1f),
            IntParam("height_percent", "Text size (% of height)", 10, 150, 100),
            ColorParam("text_color", "Text colour", 0xFFFFFF),
            BoolParam("use_gradient", "Colour words from the gradient", false),
            BoolParam("option_1", "Scroll faster on the bass", false),
            BoolParam("alpha", "Pulse brightness with the bass", false),
            FloatParam("multiplier", "Bass strength", 0f, 10f, 1f),
            FloatParam("impulse_decay", "Bass decay", 0.01f, 0.3f, 0.1f),
            roll,
        ) + twodParams()) { Texter2d() },
        EffectInfo("equalizer2d", "Equalizer2d", MATRIX, "Frequency bars across the matrix, or a ring of bands", listOf(
            IntParam("bands", "Bands", 1, 64, 16),
            BoolParam("center", "Bars from the middle", false),
            BoolParam("ring", "Ring instead of bars", false),
            BoolParam("spin", "Spin the ring with the music", false),
            ChoiceParam("power_gradient", "Bar colours", listOf("Off", "Solid", "Progressive", "Stretch"), "Off"),
            BoolParam("max_vs_mean", "Bar height from the loudest value (not the average)", false),
            BoolParam("peak_marks", "Peak markers", false),
            ColorParam("peak_color", "Peak colour", 0xFFFFFF),
            IntParam("peak_percent", "Peak size (% of height)", 0, 5, 1),
            FloatParam("peak_decay", "Peak decay", 0.01f, 0.1f, 0.03f),
            ChoiceParam("frequency_range", "Spin: frequency range", FREQUENCY_RANGES, "Lows (beat+bass)"),
            FloatParam("spin_multiplier", "Spin: strength", 0f, 5f, 1f),
            FloatParam("spin_decay", "Spin: decay", 0.01f, 0.3f, 0.1f),
            BoolParam("filtered", "Smoothed levels", true),
            roll,
        ) + twodParams()) { Equalizer2d() },
        EffectInfo("radial", "Radial", MATRIX, "Wraps a strip effect around a centre: circles, polygons, stars", listOf(
            EffectListParam("source", "Source (a strip effect)", "gradient", maxCount = 1),
            IntParam("source_pixels", "Source strip length (pixels)", 8, 300, 64),
            IntParam("edges", "Edges (0 = circle, 1 = bar, 2 = oval, 3+ = polygon)", 0, 8, 0),
            BoolParam("polygon", "Polygon (off = round lobes)", true),
            FloatParam("star", "Star shape", -1f, 1f, 0f),
            FloatParam("twist", "Twist", -4f, 4f, 0f),
            FloatParam("rotation", "Rotation", -0.5f, 0.5f, 0f),
            FloatParam("spin", "Spin with the music", -1f, 1f, 0f),
            range(),
            FloatParam("x_offset", "Centre X", 0f, 1f, 0.5f),
            FloatParam("y_offset", "Centre Y", 0f, 1f, 0.5f),
        ) + twodParams(), composite = true) { Radial2d() },
        EffectInfo("matrix_test", "Matrix test", MATRIX,
            "Frame and corner lines (red top-left, blue top-right) to check rows, wiring and rotation",
            twodParams(), usesGradient = false, audioReactive = false) { MatrixTest() },
    )

    private val byId = all.associateBy { it.id }
    fun info(id: String): EffectInfo = byId[id] ?: all[0]
    fun exists(id: String) = byId.containsKey(id)
}
