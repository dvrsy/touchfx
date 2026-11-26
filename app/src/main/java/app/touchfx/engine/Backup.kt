package app.touchfx.engine

import org.json.JSONArray
import org.json.JSONObject
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioSourceType
import app.touchfx.effects.Effects
import app.touchfx.effects.Preset
import app.touchfx.model.DeviceConfig
import app.touchfx.model.Scene
import app.touchfx.model.VirtualConfig

/** Everything the app saves: devices, lights and groups, scenes, own presets and settings. */
data class AppState(
    val devices: List<DeviceConfig>,
    val virtuals: List<VirtualConfig>,
    val scenes: List<Scene>,
    val presets: List<Preset>,
    val audioSource: AudioSourceType = AudioSourceType.MICROPHONE,
    val sensitivity: Float = 0.5f,
    val masterBrightness: Float = 1f,
    val minVolume: Map<AudioSourceType, Float> = AudioSourceType.entries.associateWith { AudioAnalyzer.DEFAULT_MIN_VOLUME },
    val tapBpm: Float = 120f,
    val tapSimulate: Boolean = true,
)

/**
 * Backup files: one JSON object with the whole [AppState].
 *
 * ```
 * { "app": "touchfx", "format": 1, "schema": 2, "app_version": "0.1.0", "exported_at": "...",
 *   "devices": [...], "virtuals": [...], "scenes": [...], "presets": [...],
 *   "settings": { "audio_source": "MICROPHONE", "sensitivity": 0.5, ... } }
 * ```
 * `format` is the backup layout; `schema` is the data model of devices and lights, the same as in
 * the saved data. Kept free of Android so it can be unit tested.
 */
object Backup {
    const val APP = "touchfx"
    const val FORMAT = 1
    /** Data model of devices and lights (also the saved data's version). */
    const val SCHEMA = 2

    fun encode(state: AppState, schema: Int, appVersion: String, exportedAt: String): JSONObject {
        val settings = JSONObject()
            .put("audio_source", state.audioSource.name)
            .put("sensitivity", state.sensitivity.toDouble())
            .put("master_brightness", state.masterBrightness.toDouble())
            .put("tap_bpm", state.tapBpm.toDouble())
            .put("tap_simulate", state.tapSimulate)
        val minVolume = JSONObject()
        state.minVolume.forEach { (source, v) -> minVolume.put(source.name, v.toDouble()) }
        settings.put("min_volume", minVolume)
        return JSONObject()
            .put("app", APP).put("format", FORMAT).put("schema", schema)
            .put("app_version", appVersion).put("exported_at", exportedAt)
            .put("devices", JSONArray().also { a -> state.devices.forEach { a.put(it.toJson()) } })
            .put("virtuals", JSONArray().also { a -> state.virtuals.forEach { a.put(it.toJson()) } })
            .put("scenes", JSONArray().also { a -> state.scenes.forEach { a.put(it.toJson()) } })
            .put("presets", JSONArray().also { a -> state.presets.forEach { a.put(it.toJson()) } })
            .put("settings", settings)
    }

    /**
     * Reads a backup. Throws [IllegalArgumentException] with a readable message if the text isn't
     * a TouchFx backup or comes from a newer, unknown format; nothing is half-read.
     */
    fun decode(text: String): AppState {
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("This file isn't valid JSON.") }
        require(o.optString("app") == APP) { "This isn't a TouchFx backup." }
        require(o.optInt("format", 0) in 1..FORMAT && o.optInt("schema", SCHEMA) <= SCHEMA) {
            "This backup was made by a newer version of TouchFx."
        }
        require(o.optInt("schema", SCHEMA) == SCHEMA) { "This backup is from an older test version and can't be read." }
        try {
            val devices = list(o.optJSONArray("devices")) { DeviceConfig.fromJson(it) }
            val rawVirtuals = list(o.optJSONArray("virtuals")) { VirtualConfig.fromJson(it) }
            // Same clean-up as on start: lights in sync with devices, unknown effects replaced.
            val virtuals = VirtualLogic.sync(devices, rawVirtuals).map { v ->
                if (Effects.exists(v.settings.effectId)) v else v.copy(settings = v.settings.copy(effectId = "wavelength"))
            }
            val s = o.optJSONObject("settings") ?: JSONObject()
            val mv = s.optJSONObject("min_volume")
            return AppState(
                devices = devices,
                virtuals = virtuals,
                scenes = list(o.optJSONArray("scenes")) { Scene.fromJson(it) },
                presets = list(o.optJSONArray("presets")) { Preset.fromJson(it) },
                audioSource = runCatching { AudioSourceType.valueOf(s.optString("audio_source")) }.getOrDefault(AudioSourceType.MICROPHONE),
                sensitivity = s.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f),
                masterBrightness = s.optDouble("master_brightness", 1.0).toFloat().coerceIn(0f, 1f),
                minVolume = AudioSourceType.entries.associateWith {
                    (mv?.optDouble(it.name, AudioAnalyzer.DEFAULT_MIN_VOLUME.toDouble()) ?: AudioAnalyzer.DEFAULT_MIN_VOLUME.toDouble())
                        .toFloat().coerceIn(0f, 1f)
                },
                tapBpm = s.optDouble("tap_bpm", 120.0).toFloat().coerceIn(AudioAnalyzer.TAP_MIN_BPM, AudioAnalyzer.TAP_MAX_BPM),
                tapSimulate = s.optBoolean("tap_simulate", true),
            )
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("The backup is damaged and can't be read (${e.message}).")
        }
    }

    /** One line for the confirmation dialog, e.g. "3 devices, 2 groups, 4 scenes, 1 preset". */
    fun summary(state: AppState): String {
        fun n(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
        return listOf(
            n(state.devices.size, "device", "devices"),
            n(state.virtuals.count { it.isGroup }, "group", "groups"),
            n(state.scenes.size, "scene", "scenes"),
            n(state.presets.size, "own preset", "own presets"),
        ).joinToString(", ")
    }

    private fun <T> list(a: JSONArray?, read: (JSONObject) -> T): List<T> =
        if (a == null) emptyList() else List(a.length()) { read(a.getJSONObject(it)) }
}
