package app.touchfx.model

import org.json.JSONArray
import org.json.JSONObject

/** What a scene does with one light when it is activated (LedFx `SceneVirtual.action`). */
enum class SceneAction {
    /** Turn the light on with the saved look. */
    ACTIVATE,
    /** Leave the light as it is. */
    IGNORE,
    /** Turn the light off. */
    STOP,
}

/**
 * One light's part of a scene. For [SceneAction.ACTIVATE] it holds the look: the effect, its
 * settings and the colours. Only that effect's settings are stored, so activating a scene keeps
 * the light's remembered settings for other effects.
 */
data class SceneEntry(
    val action: SceneAction,
    val effectId: String? = null,
    val paletteId: String? = null,
    val customGradient: List<Int>? = null,
    val brightness: Float? = null,
    val config: Map<String, Any>? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("action", action.name)
        .put("effect", effectId ?: JSONObject.NULL)
        .put("palette", paletteId ?: JSONObject.NULL)
        .put("gradient", customGradient?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("brightness", brightness?.toDouble() ?: JSONObject.NULL)
        .put("config", config?.let { mapToJson(it) } ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = SceneEntry(
            action = runCatching { SceneAction.valueOf(o.optString("action")) }.getOrDefault(SceneAction.IGNORE),
            effectId = o.optStringOrNull("effect"),
            paletteId = o.optStringOrNull("palette"),
            customGradient = o.optJSONArray("gradient")?.let { a -> List(a.length()) { a.getInt(it) } },
            brightness = if (o.isNull("brightness")) null else o.optDouble("brightness").toFloat(),
            config = o.optJSONObject("config")?.let { jsonToMap(it) },
        )
    }
}

/** One step of a playlist: a scene and how long it runs before the next one. */
data class PlaylistItem(val sceneId: String, val seconds: Int) {
    fun toJson(): JSONObject = JSONObject().put("scene", sceneId).put("seconds", seconds)

    companion object {
        fun fromJson(o: JSONObject) = PlaylistItem(o.getString("scene"), o.optInt("seconds", 30).coerceIn(1, MAX_SECONDS))
        const val MAX_SECONDS = 3600
    }
}

/** A playlist: existing (normal) scenes played one after another, in order or shuffled. */
data class Playlist(val items: List<PlaylistItem>, val shuffle: Boolean = false) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        items.forEach { a.put(it.toJson()) }
        return JSONObject().put("items", a).put("shuffle", shuffle)
    }

    companion object {
        fun fromJson(o: JSONObject): Playlist {
            val a = o.optJSONArray("items") ?: JSONArray()
            return Playlist(List(a.length()) { PlaylistItem.fromJson(a.getJSONObject(it)) }, o.optBoolean("shuffle", false))
        }
    }
}

/** What a scene card does: switch to the look, play it while held, or run a playlist. */
enum class SceneKind { NORMAL, HOLD, PLAYLIST }

/**
 * A saved look for several lights, recalled in one tap (LedFx scenes).
 * [entries] is keyed by the target's id: a device id now, a virtual id once virtuals exist.
 * Auto-virtuals reuse their device's id, so scenes keep working across that change.
 */
data class Scene(
    val id: String,
    val name: String,
    val entries: Map<String, SceneEntry>,
    /**
     * Hold to play: the scene is on only while its button is held; letting go brings back what
     * its lights showed before (e.g. for strobe flashes). Otherwise a tap switches to it.
     */
    val momentary: Boolean = false,
    /** Set for a playlist scene: it has no look of its own ([entries] is empty). */
    val playlist: Playlist? = null,
) {
    val kind: SceneKind get() = when {
        playlist != null -> SceneKind.PLAYLIST
        momentary -> SceneKind.HOLD
        else -> SceneKind.NORMAL
    }

    fun toJson(): JSONObject {
        val e = JSONObject()
        entries.forEach { (target, entry) -> e.put(target, entry.toJson()) }
        val o = JSONObject().put("id", id).put("name", name).put("entries", e).put("momentary", momentary)
        playlist?.let { o.put("playlist", it.toJson()) }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Scene {
            val entries = LinkedHashMap<String, SceneEntry>()
            o.optJSONObject("entries")?.let { e ->
                e.keys().forEach { k -> e.optJSONObject(k)?.let { entries[k] = SceneEntry.fromJson(it) } }
            }
            return Scene(
                o.getString("id"), o.optString("name", "Scene"), entries, o.optBoolean("momentary", false),
                o.optJSONObject("playlist")?.let { Playlist.fromJson(it) },
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
