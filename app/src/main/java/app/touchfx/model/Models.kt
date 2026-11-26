package app.touchfx.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * How one device's lights look. Every effect keeps its own settings in [configs], so switching
 * effects and back restores what you had (like LedFx).
 */
data class EffectSettings(
    val effectId: String = "wavelength",
    val paletteId: String = "rainbow",
    /** Gradient from a preset; overrides [paletteId] until a palette is picked. */
    val customGradient: List<Int>? = null,
    /** Device brightness 0..1 (multiplied by the app-wide master brightness). */
    val brightness: Float = 1f,
    val configs: Map<String, Map<String, Any>> = emptyMap(),
) {
    fun config(effect: String = effectId): Map<String, Any> = configs[effect] ?: emptyMap()

    fun withValue(key: String, value: Any): EffectSettings =
        copy(configs = configs + (effectId to (config() + (key to value))))

    fun toJson(): JSONObject {
        val cfg = JSONObject()
        configs.forEach { (effect, values) -> cfg.put(effect, mapToJson(values)) }
        return JSONObject()
            .put("effect", effectId).put("palette", paletteId)
            .put("gradient", customGradient?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("brightness", brightness.toDouble())
            .put("configs", cfg)
    }

    companion object {
        fun fromJson(o: JSONObject): EffectSettings {
            val configs = HashMap<String, Map<String, Any>>()
            o.optJSONObject("configs")?.let { c ->
                c.keys().forEach { k -> c.optJSONObject(k)?.let { configs[k] = jsonToMap(it) } }
            }
            return EffectSettings(
                effectId = o.optString("effect", "wavelength"),
                paletteId = o.optString("palette", "rainbow"),
                customGradient = o.optJSONArray("gradient")?.let { a -> List(a.length()) { a.getInt(it) } },
                brightness = o.optDouble("brightness", 1.0).toFloat(),
                configs = configs,
            )
        }
    }
}

/** WLED is a controller on Wi-Fi; PREVIEW is a strip shown only in the app. */
enum class DeviceKind {
    WLED, PREVIEW;

    companion object {
        fun parse(s: String?): DeviceKind = when (s) {
            "PREVIEW" -> PREVIEW
            else -> WLED
        }
    }
}

/**
 * A physical LED output. Effects don't render onto devices directly: they render onto
 * virtuals ([VirtualConfig]), whose segments say which device pixels they drive (as in LedFx).
 */
data class DeviceConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: DeviceKind,
    val host: String = "",
    val ledCount: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("kind", kind.name).put("host", host).put("leds", ledCount)

    companion object {
        fun fromJson(o: JSONObject) = DeviceConfig(
            id = o.getString("id"),
            name = o.optString("name", "LEDs"),
            kind = DeviceKind.parse(o.optString("kind")),
            host = o.optString("host", ""),
            ledCount = o.optInt("leds", 60).coerceIn(1, MAX_LEDS),
        )
    }
}

const val MAX_LEDS = 4096

/** A run of one device's pixels inside a virtual. [end] is inclusive, like LedFx. */
data class Segment(val deviceId: String, val start: Int, val end: Int, val invert: Boolean = false) {
    val length get() = end - start + 1

    fun toJson(): JSONArray = JSONArray().put(deviceId).put(start).put(end).put(invert)

    companion object {
        /** LedFx stores segments as `[device_id, start, end, invert]`. */
        fun fromJson(a: JSONArray) = Segment(a.getString(0), a.getInt(1), a.getInt(2), a.optBoolean(3, false))
    }
}

/** SPAN: the effect stretches across all segments in order. COPY: each segment shows the whole effect. */
enum class Mapping { SPAN, COPY }

/**
 * How the segments of a spanning matrix group sit. SIDE_BY_SIDE: each segment is a panel `rows`
 * tall, placed left to right in segment order. STACKED: the pixels run on from one segment to
 * the next, so panels sit one above the other (one long matrix).
 */
enum class PanelLayout { SIDE_BY_SIDE, STACKED }

/**
 * What an effect renders onto: one or more device segments (LedFx "virtual").
 * Every device owns an auto-virtual ([isDevice] = its id, same id as the device) that covers it
 * completely; groups are virtuals the user builds from several segments.
 */
data class VirtualConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val segments: List<Segment>,
    val mapping: Mapping = Mapping.SPAN,
    /** Physical LEDs driven by each effect pixel (LedFx `grouping`). */
    val grouping: Int = 1,
    /** Rows of a matrix; 1 = strip. The matrix is `pixels / rows` wide. */
    val rows: Int = 1,
    /** Matrix wired zig-zag: every second row runs backwards. */
    val serpentine: Boolean = false,
    /** Matrix rotation in 90° steps (LedFx virtual `rotate`), added to each 2D effect's own. */
    val rotate: Int = 0,
    /** How the segments of a spanning matrix sit; see [PanelLayout]. */
    val panelLayout: PanelLayout = PanelLayout.SIDE_BY_SIDE,
    /** Id of the device this auto-virtual belongs to, or null for a group. */
    val isDevice: String? = null,
    val enabled: Boolean = true,
    val settings: EffectSettings = EffectSettings(),
) {
    val isGroup get() = isDevice == null

    fun toJson(): JSONObject {
        val segs = JSONArray()
        segments.forEach { segs.put(it.toJson()) }
        return JSONObject()
            .put("id", id).put("name", name).put("segments", segs)
            .put("mapping", mapping.name).put("grouping", grouping).put("rows", rows)
            .put("serpentine", serpentine).put("rotate", rotate).put("panel_layout", panelLayout.name)
            .put("is_device", isDevice ?: JSONObject.NULL)
            .put("enabled", enabled).put("settings", settings.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): VirtualConfig {
            val segs = o.optJSONArray("segments")
            return VirtualConfig(
                id = o.getString("id"),
                name = o.optString("name", "Group"),
                segments = segs?.let { a -> List(a.length()) { Segment.fromJson(a.getJSONArray(it)) } } ?: emptyList(),
                mapping = runCatching { Mapping.valueOf(o.optString("mapping")) }.getOrDefault(Mapping.SPAN),
                grouping = o.optInt("grouping", 1).coerceAtLeast(1),
                rows = o.optInt("rows", 1).coerceAtLeast(1),
                serpentine = o.optBoolean("serpentine", false),
                rotate = Math.floorMod(o.optInt("rotate", 0), 4),
                panelLayout = runCatching { PanelLayout.valueOf(o.optString("panel_layout")) }
                    .getOrDefault(PanelLayout.SIDE_BY_SIDE),
                isDevice = if (o.isNull("is_device")) null else o.optString("is_device"),
                enabled = o.optBoolean("enabled", true),
                settings = o.optJSONObject("settings")?.let { EffectSettings.fromJson(it) } ?: EffectSettings(),
            )
        }
    }
}

fun mapToJson(m: Map<String, Any>): JSONObject {
    val o = JSONObject()
    m.forEach { (k, v) -> o.put(k, if (v is Float) v.toDouble() else v) }
    return o
}

fun jsonToMap(o: JSONObject): Map<String, Any> {
    val m = HashMap<String, Any>()
    o.keys().forEach { k ->
        when (val v = o.get(k)) {
            is Number, is Boolean, is String -> m[k] = v
        }
    }
    return m
}
