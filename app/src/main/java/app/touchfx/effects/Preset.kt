package app.touchfx.effects

import org.json.JSONArray
import org.json.JSONObject
import app.touchfx.model.EffectSettings
import app.touchfx.model.jsonToMap
import app.touchfx.model.mapToJson

/**
 * A saved look for one effect: its settings plus (optionally) a gradient.
 * Built-in ones come from LedFx (LedFxPresets.kt); user ones are saved in the app.
 */
class Preset(
    val id: String,
    val name: String,
    val effectId: String,
    val values: Map<String, Any>,
    val gradient: List<Int>?,
) {
    val builtIn get() = id.startsWith("ledfx:")

    /** Applies this preset: switches to its effect and replaces that effect's settings. */
    fun applyTo(s: EffectSettings): EffectSettings = s.copy(
        effectId = effectId,
        configs = s.configs + (effectId to values),
        customGradient = gradient ?: s.customGradient,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("effect", effectId)
        .put("values", mapToJson(values))
        .put("gradient", gradient?.let { JSONArray(it) } ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Preset(
            id = o.getString("id"),
            name = o.optString("name", "Preset"),
            effectId = o.optString("effect"),
            values = o.optJSONObject("values")?.let { jsonToMap(it) } ?: emptyMap(),
            gradient = o.optJSONArray("gradient")?.let { a -> List(a.length()) { a.getInt(it) } },
        )

        fun builtInFor(effectId: String) = LedFxPresets.all.filter { it.effectId == effectId }
    }
}
