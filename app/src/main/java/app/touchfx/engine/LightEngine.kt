package app.touchfx.engine

import android.content.Context
import android.content.SharedPreferences
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioCapture
import app.touchfx.audio.AudioFrame
import app.touchfx.audio.AudioSourceType
import app.touchfx.effects.Effect
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.Preset
import app.touchfx.effects.RenderContext
import app.touchfx.effects.Rgb
import app.touchfx.effects.blurChannel
import app.touchfx.effects.frac
import app.touchfx.model.DeviceConfig
import app.touchfx.model.DeviceKind
import app.touchfx.model.EffectSettings
import app.touchfx.model.Mapping
import app.touchfx.model.Playlist
import app.touchfx.model.Scene
import app.touchfx.model.SceneKind
import app.touchfx.model.VirtualConfig
import app.touchfx.net.DdpSender
import java.net.InetAddress
import java.util.UUID
import kotlin.math.abs

/**
 * The light engine: every frame (60 fps) it analyses audio, renders each virtual's effect,
 * applies blur/mirror/flip/brightness/grouping, maps the virtuals onto their devices' pixels,
 * streams those to WLED and publishes previews.
 */
object LightEngine {
    private const val TAG = "LightEngine"
    private const val FPS = 60
    /** Segment highlight: a dim white, so long strips don't draw too much current. */
    private const val HIGHLIGHT_COLOR = 0x606060
    /** Version of the saved data format; the same number is written into backups. */
    private const val SCHEMA = Backup.SCHEMA

    private lateinit var prefs: SharedPreferences
    private lateinit var capture: AudioCapture
    private val analyzer = AudioAnalyzer()
    private val main = Handler(Looper.getMainLooper())

    /** Physical outputs. */
    private val _devices = MutableStateFlow<List<DeviceConfig>>(emptyList())
    val devices: StateFlow<List<DeviceConfig>> = _devices

    /**
     * What effects render onto, in render order (later ones win where they overlap).
     * Each device's auto-virtual shares the device's id.
     */
    private val _virtuals = MutableStateFlow<List<VirtualConfig>>(emptyList())
    val virtuals: StateFlow<List<VirtualConfig>> = _virtuals

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _audioSource = MutableStateFlow(AudioSourceType.MICROPHONE)
    val audioSource: StateFlow<AudioSourceType> = _audioSource

    private val _sensitivity = MutableStateFlow(0.5f)
    val sensitivity: StateFlow<Float> = _sensitivity

    /** Silence threshold (LedFx `min_volume`) per audio source; see [AudioAnalyzer.minVolume]. */
    private val _minVolume = MutableStateFlow(
        AudioSourceType.entries.associateWith { AudioAnalyzer.DEFAULT_MIN_VOLUME }
    )
    val minVolume: StateFlow<Map<AudioSourceType, Float>> = _minVolume

    /** App-wide brightness applied on top of every device's own brightness. */
    private val _masterBrightness = MutableStateFlow(1f)
    val masterBrightness: StateFlow<Float> = _masterBrightness

    private val _userPresets = MutableStateFlow<List<Preset>>(emptyList())
    val userPresets: StateFlow<List<Preset>> = _userPresets

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes: StateFlow<List<Scene>> = _scenes

    /** Devices not streamed to right now (e.g. while their WLED page is open). */
    private val _paused = MutableStateFlow<Set<String>>(emptySet())
    val paused: StateFlow<Set<String>> = _paused

    private val _audio = MutableStateFlow(AudioFrame.EMPTY)
    val audio: StateFlow<AudioFrame> = _audio

    private val _audioProblem = MutableStateFlow<String?>(null)
    val audioProblem: StateFlow<String?> = _audioProblem

    /** Latest pixels per virtual id (one per physical pixel), for on-screen previews (~30 fps). */
    private val _previews = MutableStateFlow<Map<String, IntArray>>(emptyMap())
    val previews: StateFlow<Map<String, IntArray>> = _previews

    @Volatile var projection: MediaProjection? = null
        private set

    private var thread: Thread? = null
    @Volatile private var loopRunning = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
        capture = AudioCapture(context, analyzer)
        load()
    }

    // ---------------------------------------------------------------- lifecycle

    @Synchronized
    fun start() {
        if (loopRunning) return
        loopRunning = true
        _running.value = true
        restartCapture()
        thread = Thread({ loop() }, "light-engine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!loopRunning) return
        loopRunning = false
        thread?.join(1000)
        thread = null
        capture.stop()
        _running.value = false
        _audio.value = AudioFrame.EMPTY
        _previews.value = emptyMap()
    }

    // ---------------------------------------------------------------- settings

    fun setAudioSource(source: AudioSourceType) {
        _audioSource.value = source
        save()
        if (loopRunning) restartCapture()
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value.coerceIn(0f, 1f)
        save()
    }

    /** Tempo of the TAP source in BPM; held until changed by tapping or the buttons. */
    private val _tapBpm = MutableStateFlow(120f)
    val tapBpm: StateFlow<Float> = _tapBpm

    /** A tap on the tap-tempo button (source TAP). */
    fun tapTempo() {
        analyzer.tap(System.nanoTime())
        _tapBpm.value = analyzer.tapBpm
        save()
    }

    /** Tap mode: simulate a drum-like spectrum from the beat, so sound-driven effects move too. */
    private val _tapSimulate = MutableStateFlow(true)
    val tapSimulate: StateFlow<Boolean> = _tapSimulate

    fun setTapSimulate(on: Boolean) {
        _tapSimulate.value = on
        save()
    }

    /** Sets the tapped tempo directly (the -1 / +1 / ÷2 / ×2 buttons). */
    fun setTapBpm(bpm: Float) {
        analyzer.tapBpm = bpm
        _tapBpm.value = analyzer.tapBpm
        save()
    }

    /** Sets the silence threshold for the audio source that is selected now. */
    fun setMinVolume(value: Float) {
        val source = _audioSource.value
        _minVolume.update { it + (source to value.coerceIn(0f, 1f)) }
        save()
    }

    fun setMasterBrightness(value: Float) {
        _masterBrightness.value = value.coerceIn(0f, 1f)
        save()
    }

    fun setPaused(id: String, paused: Boolean) {
        _paused.update { if (paused) it + id else it - id }
    }

    fun onProjectionGranted(p: MediaProjection) {
        projection?.stop()
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (projection === p) projection = null
                if (_audioSource.value == AudioSourceType.PHONE_AUDIO && loopRunning) restartCapture()
            }
        }, main)
        if (_audioSource.value == AudioSourceType.PHONE_AUDIO && loopRunning) restartCapture()
    }

    fun releaseProjection() {
        projection?.stop()
        projection = null
    }

    private fun restartCapture() {
        capture.start(_audioSource.value, projection)
        _audioProblem.value = capture.problem
    }

    // ---------------------------------------------------------------- devices and virtuals

    /** Adds a device; its auto-virtual is created with it. */
    fun addDevice(device: DeviceConfig) {
        _devices.update { it + device }
        syncVirtuals()
        save()
    }

    fun updateDevice(id: String, change: (DeviceConfig) -> DeviceConfig) {
        _devices.update { list -> list.map { if (it.id == id) change(it) else it } }
        syncVirtuals()
        save()
    }

    /** Removes a device, its auto-virtual, and its segments in groups. */
    fun removeDevice(id: String) {
        _devices.update { list -> list.filterNot { it.id == id } }
        _paused.update { it - id }
        syncVirtuals()
        save()
    }

    /** Adds a group and turns it on, switching off whatever conflicts with it (as LedFx does). */
    fun addVirtual(virtual: VirtualConfig) {
        _virtuals.update { VirtualLogic.enableExclusive(it + virtual.copy(isDevice = null), virtual.id) }
        syncVirtuals()
        save()
    }

    /**
     * Turns a light on or off. Turning one on switches off conflicting ones: a group and its
     * devices' own lights, or groups whose pixels overlap.
     */
    fun setEnabled(id: String, on: Boolean) {
        _virtuals.update { list ->
            if (on) VirtualLogic.enableExclusive(list, id)
            else list.map { if (it.id == id) it.copy(enabled = false) else it }
        }
        save()
    }

    /**
     * The X/Y pad while it is touched (or held): changes the running effects live; never saved.
     * null = not in use.
     */
    private val _pad = MutableStateFlow<PadState?>(null)
    val pad: StateFlow<PadState?> = _pad

    fun setPad(state: PadState?) {
        _pad.value = state
    }

    /** Lights a range of a device white (segment editor), drawn over the effects. */
    data class Highlight(val deviceId: String, val start: Int, val end: Int)

    private val _highlight = MutableStateFlow<Highlight?>(null)
    val highlight: StateFlow<Highlight?> = _highlight

    fun setHighlight(h: Highlight?) {
        _highlight.value = h
    }

    fun updateVirtual(id: String, change: (VirtualConfig) -> VirtualConfig) {
        _virtuals.update { list -> list.map { if (it.id == id) change(it) else it } }
        syncVirtuals()
        save()
    }

    fun updateSettings(virtualId: String, change: (EffectSettings) -> EffectSettings) =
        updateVirtual(virtualId) { it.copy(settings = change(it.settings)) }

    /** Removes a group. A device's own virtual goes only with the device. */
    fun removeVirtual(id: String) {
        _virtuals.update { list -> list.filterNot { it.id == id && it.isGroup } }
        save()
    }

    private fun syncVirtuals() {
        _virtuals.update { VirtualLogic.sync(_devices.value, it) }
    }

    // ---------------------------------------------------------------- presets

    /** Saves the device's current effect settings and colours as a named preset. */
    fun savePreset(name: String, settings: EffectSettings): Preset {
        val gradient = Palettes.resolve(settings.paletteId, settings.customGradient).previewColors
        val preset = Preset("user:" + UUID.randomUUID(), name, settings.effectId, settings.config(), gradient)
        _userPresets.update { it + preset }
        save()
        return preset
    }

    fun deletePreset(id: String) {
        _userPresets.update { list -> list.filterNot { it.id == id } }
        save()
    }

    // ---------------------------------------------------------------- scenes

    /** Saves the current look of the lights in [included] as a new scene; the rest are ignored. */
    fun saveScene(name: String, included: Set<String>, momentary: Boolean = false): Scene {
        val scene = SceneLogic.capture("scene:" + UUID.randomUUID(), name, _virtuals.value, included)
            .copy(momentary = momentary)
        _scenes.update { it + scene }
        save()
        return scene
    }

    /** Replaces a scene's saved look with the lights' current look. */
    fun updateSceneFromCurrent(id: String, name: String, included: Set<String>, momentary: Boolean) {
        _scenes.update { list ->
            list.map {
                if (it.id == id) SceneLogic.capture(id, name, _virtuals.value, included).copy(momentary = momentary) else it
            }
        }
        save()
    }

    fun renameScene(id: String, name: String) {
        _scenes.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
        save()
    }

    fun setSceneMomentary(id: String, momentary: Boolean) {
        _scenes.update { list -> list.map { if (it.id == id) it.copy(momentary = momentary) else it } }
        save()
    }

    fun deleteScene(id: String) {
        if (_playlist.value?.playlistId == id) stopPlaylist()
        _scenes.update { list -> list.filterNot { it.id == id } }
        save()
    }

    /**
     * Tap on a scene: a playlist starts playing; a normal scene is applied to all its lights in
     * one step (so no frame shows it half applied) and stops a running playlist.
     */
    fun activateScene(id: String) {
        val scene = _scenes.value.firstOrNull { it.id == id } ?: return
        if (scene.kind == SceneKind.PLAYLIST) { startPlaylist(id); return }
        releaseHeldScene()
        stopPlaylist()
        applyLook(scene)
    }

    private fun applyLook(scene: Scene) {
        _virtuals.update { SceneLogic.apply(scene, it) }
        save()
    }

    // ---------------------------------------------------------------- playlists

    /** The running playlist, or null. */
    private val _playlist = MutableStateFlow<PlaylistState?>(null)
    val playlist: StateFlow<PlaylistState?> = _playlist

    /** Clock for playlists (ms); not affected by changes of the wall-clock time. */
    fun clockMs(): Long = SystemClock.elapsedRealtime()

    private val playlistTick = Runnable { tickPlaylist() }

    /** Creates a playlist scene, or updates [id]'s; a running playlist that was edited restarts. */
    fun savePlaylist(id: String?, name: String, list: Playlist) {
        val sceneId = id ?: ("scene:" + UUID.randomUUID())
        _scenes.update { scenes ->
            if (id != null && scenes.any { it.id == id }) scenes.map { if (it.id == id) it.copy(name = name, playlist = list, momentary = false, entries = emptyMap()) else it }
            else scenes + Scene(sceneId, name, emptyMap(), playlist = list)
        }
        save()
        if (_playlist.value?.playlistId == sceneId) startPlaylist(sceneId)
    }

    fun startPlaylist(id: String) {
        releaseHeldScene()
        val scene = _scenes.value.firstOrNull { it.id == id } ?: return
        val step = PlaylistLogic.start(scene, _scenes.value, clockMs())
        if (step == null) { stopPlaylist(); return }
        _playlist.value = step.state
        _scenes.value.firstOrNull { it.id == step.sceneId }?.let { applyLook(it) }
        schedulePlaylist()
    }

    fun stopPlaylist() {
        main.removeCallbacks(playlistTick)
        _playlist.value = null
    }

    /** Skips to the next scene of the running playlist. */
    fun skipInPlaylist() {
        val st = _playlist.value ?: return
        releaseHeldScene()
        advancePlaylist(st)
    }

    private fun tickPlaylist() {
        val st = _playlist.value ?: return
        val scene = _scenes.value.firstOrNull { it.id == st.playlistId }
        if (scene == null || scene.kind != SceneKind.PLAYLIST) { stopPlaylist(); return }
        if (PlaylistLogic.due(st, clockMs())) advancePlaylist(st) else schedulePlaylist()
    }

    private fun advancePlaylist(st: PlaylistState) {
        val scene = _scenes.value.firstOrNull { it.id == st.playlistId }
        val step = scene?.let { PlaylistLogic.advance(st, it, _scenes.value, clockMs()) }
        if (step == null) { stopPlaylist(); return }
        _playlist.value = step.state
        _scenes.value.firstOrNull { it.id == step.sceneId }?.let { applyLook(it) }
        schedulePlaylist()
    }

    private fun schedulePlaylist() {
        main.removeCallbacks(playlistTick)
        if (_playlist.value != null) main.postDelayed(playlistTick, 200)
    }

    /** What a held scene's lights showed before it was pressed; null when nothing is held. */
    private var held: Map<String, VirtualConfig>? = null

    /**
     * Hold-to-play: applies the scene until [releaseHeldScene]. Not saved, so if the app is
     * closed while holding, the lights come back as they were before.
     */
    @Synchronized
    fun holdScene(id: String) {
        releaseHeldScene()
        val scene = _scenes.value.firstOrNull { it.id == id } ?: return
        // A running playlist waits while the hold scene plays, and carries on afterwards.
        _playlist.update { st -> st?.let { PlaylistLogic.pause(it, clockMs()) } }
        held = SceneLogic.snapshot(scene, _virtuals.value)
        _virtuals.update { SceneLogic.apply(scene, it) }
    }

    /** Ends a held scene and puts its lights back as they were. */
    @Synchronized
    fun releaseHeldScene() {
        val snap = held ?: return
        held = null
        _virtuals.update { SceneLogic.restore(snap, it) }
        _playlist.update { st -> st?.let { PlaylistLogic.resume(it, clockMs()) } }
    }

    // ---------------------------------------------------------------- render loop

    /** Render state for one virtual. */
    private class VirtualRuntime {
        var effectId = ""
        var effect: Effect? = null
        var values = ParamValues(emptyList())
        /** Effect pixels (after mirror: half of them). */
        var work = IntArray(0)
        /** Effect pixels after mirror/flip/brightness, one per pixel group. */
        var grouped = IntArray(0)
        /** One per physical pixel the virtual drives, in logical (row-major) order. */
        var out = IntArray(0)
        /** [out] in wiring order (serpentine matrices); same array as [out] otherwise. */
        var wired = IntArray(0)
        var r = FloatArray(0); var g = FloatArray(0); var b = FloatArray(0); var tmp = FloatArray(0)
        var gradientOffset = 0f
        /** This light's own clock, for the pad's Overall speed. */
        val clock = EffectClock()
        /** Segment of each effect pixel, and the layout it was computed for. */
        var segmentOf = IntArray(0)
        var segmentKey: Any? = null
    }

    /** Output state for one device. */
    private class DeviceRuntime {
        var pixels = IntArray(0)
        var covered = false
        var host = ""
        var address: InetAddress? = null
        var nextResolveNanos = 0L
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        val sender = DdpSender()
        val virtualRuntimes = HashMap<String, VirtualRuntime>()
        val deviceRuntimes = HashMap<String, DeviceRuntime>()
        val buffers = HashMap<String, IntArray>()
        val ctx = RenderContext()
        val frameNanos = 1_000_000_000L / FPS
        val startNanos = System.nanoTime()
        var lastNanos = startNanos
        var next = startNanos
        var frameNo = 0L

        try {
            while (loopRunning) {
                val now = System.nanoTime()
                analyzer.sensitivity = _sensitivity.value
                analyzer.tapMode = _audioSource.value == AudioSourceType.TAP
                analyzer.tapSimulate = _tapSimulate.value
                analyzer.minVolume = _minVolume.value[_audioSource.value] ?: AudioAnalyzer.DEFAULT_MIN_VOLUME
                val audio = analyzer.analyze(now)
                _audio.value = audio

                ctx.audio = audio
                ctx.time = (now - startNanos) / 1e9
                ctx.dt = ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.1f)
                val frameTime = ctx.time
                val frameDt = ctx.dt
                lastNanos = now

                val devices = _devices.value
                val virtuals = _virtuals.value
                val paused = _paused.value
                val master = _masterBrightness.value
                val publish = frameNo % 2 == 0L
                val previews = if (publish) HashMap<String, IntArray>(virtuals.size) else null

                // Fresh black buffer per device each frame.
                buffers.clear()
                for (dev in devices) {
                    val rt = deviceRuntimes.getOrPut(dev.id) { DeviceRuntime() }
                    if (rt.pixels.size != dev.ledCount) rt.pixels = IntArray(dev.ledCount) else rt.pixels.fill(0)
                    rt.covered = false
                    buffers[dev.id] = rt.pixels
                }

                // Render every virtual; map the enabled ones onto their devices.
                for (v in virtuals) {
                    val rt = virtualRuntimes.getOrPut(v.id) { VirtualRuntime() }
                    ctx.time = frameTime; ctx.dt = frameDt // a light's Overall speed mustn't leak into the next
                    renderVirtual(v, rt, ctx, master)
                    if (v.enabled) {
                        if (PixelMapper.usesPanels(v)) PixelMapper.mapPanels(v, rt.out, buffers)
                        else PixelMapper.map(v, rt.wired, buffers)
                        for (seg in v.segments) deviceRuntimes[seg.deviceId]?.covered = true
                    }
                    previews?.put(v.id, rt.out.copyOf())
                }

                _highlight.value?.let { h ->
                    val rt = deviceRuntimes[h.deviceId]
                    if (rt != null) {
                        val end = minOf(h.end, rt.pixels.size - 1)
                        for (i in h.start.coerceAtLeast(0)..end) rt.pixels[i] = HIGHLIGHT_COLOR
                        rt.covered = true
                    }
                }

                // Only devices that an enabled virtual drives are streamed to, so the others
                // fall back to their own WLED effects.
                for (dev in devices) {
                    val rt = deviceRuntimes[dev.id] ?: continue
                    if (rt.covered && dev.kind == DeviceKind.WLED && dev.id !in paused) sendDevice(dev, rt, sender, now)
                }

                if (virtualRuntimes.size > virtuals.size) {
                    val ids = virtuals.mapTo(HashSet()) { it.id }
                    virtualRuntimes.keys.retainAll(ids)
                }
                if (deviceRuntimes.size > devices.size) {
                    val ids = devices.mapTo(HashSet()) { it.id }
                    deviceRuntimes.keys.retainAll(ids)
                }
                if (previews != null) _previews.value = previews

                frameNo++
                next += frameNanos
                val sleep = next - System.nanoTime()
                if (sleep > 0) Thread.sleep(sleep / 1_000_000, (sleep % 1_000_000).toInt())
                else next = System.nanoTime()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Render loop crashed", e)
        } finally {
            sender.close()
        }
    }

    private fun renderVirtual(v: VirtualConfig, rt: VirtualRuntime, ctx: RenderContext, master: Float) {
        val s = v.settings
        val frameTime = ctx.time // the engine's clock, before Overall speed shifts this light's
        val info = Effects.info(s.effectId)
        if (rt.effectId != info.id || rt.effect == null) {
            rt.effectId = info.id
            rt.effect = info.create()
            rt.values = ParamValues(info.params)
            rt.gradientOffset = 0f
            rt.clock.reset()
        }
        val pad = _pad.value?.takeIf { it.appliesTo(v.id) }
        // Colours: a different palette, and the effect's own colours from it.
        val padPalette = pad?.let { XyPad.pickPalette(it, s.paletteId) }
        var config = s.config(info.id)
        if (pad != null) config = XyPad.applyToConfig(info, config, pad)
        if (padPalette != null) config = XyPad.applyColours(info, config) { t -> padPalette.clamped(t) }
        rt.values.values = config
        // Overall speed: applied when rendering, in the way that suits the effect (EffectClock).
        // A clock that ran ahead stays ahead when released, so animations carry on without a jump.
        val timeScale = if (pad != null) XyPad.timeScale(pad) else 1f
        val p = rt.values
        ctx.rows = v.rows
        ctx.matrixRotate = v.rotate
        val layoutKey = listOf(v.segments, v.mapping, v.grouping, v.rows, v.panelLayout)
        if (rt.segmentKey != layoutKey) {
            rt.segmentKey = layoutKey
            rt.segmentOf = PixelMapper.segmentIndex(v)
        }
        ctx.segmentOf = rt.segmentOf
        ctx.effectConfigs = s.configs
        ctx.segmentCount = if (v.mapping == Mapping.COPY) 1 else v.segments.size.coerceAtLeast(1)
        val mirror = p.b("mirror")
        val flip = p.b("flip")

        val physical = PixelMapper.pixelCount(v)
        val n = PixelMapper.effectPixelCount(v)
        if (rt.out.size != physical) rt.out = IntArray(physical)
        // Side-by-side panels apply serpentine per panel in mapPanels instead.
        val serpentine = v.rows > 1 && v.serpentine && !PixelMapper.usesPanels(v)
        rt.wired = if (!serpentine) rt.out else if (rt.wired.size == physical && rt.wired !== rt.out) rt.wired else IntArray(physical)
        if (rt.grouped.size != n) rt.grouped = IntArray(n)
        if (n == 0) return

        val renderN = if (mirror) (n + 1) / 2 else n
        if (rt.work.size != renderN) {
            rt.work = IntArray(renderN)
            rt.r = FloatArray(renderN); rt.g = FloatArray(renderN); rt.b = FloatArray(renderN); rt.tmp = FloatArray(renderN)
        }

        // Gradient roll: LedFx shifts the gradient by `gradient_roll` LEDs per frame.
        val roll = p.f("gradient_roll")
        rt.gradientOffset = if (roll > 0f) frac(rt.gradientOffset + roll * ctx.dt * 60f / renderN) else 0f
        ctx.gradientOffset = if (pad != null) frac(rt.gradientOffset + XyPad.paletteShift(pad)) else rt.gradientOffset
        ctx.p = p
        ctx.palette = padPalette ?: Palettes.resolve(s.paletteId, s.customGradient)
        try {
            rt.clock.render(rt.effect!!, XyPad.timing(info.id), timeScale, ctx, rt.work)
        } catch (e: Exception) {
            Log.w(TAG, "Effect ${info.id} failed", e)
            rt.work.fill(0)
        }

        // Blur (LedFx sigma in LEDs).
        val blur = p.f("blur")
        if (blur >= 0.3f) {
            for (i in 0 until renderN) {
                val c = rt.work[i]
                rt.r[i] = Rgb.r(c).toFloat(); rt.g[i] = Rgb.g(c).toFloat(); rt.b[i] = Rgb.b(c).toFloat()
            }
            blurChannel(rt.r, rt.tmp, blur); blurChannel(rt.g, rt.tmp, blur); blurChannel(rt.b, rt.tmp, blur)
            for (i in 0 until renderN) rt.work[i] = Rgb.of(rt.r[i].toInt(), rt.g[i].toInt(), rt.b[i].toInt())
        }

        // Strobe: between flashes the light is dark (the effect keeps running underneath).
        val strobeOn = pad == null || XyPad.strobeOpen(pad, frameTime, ctx.audio.beatPhase)
        val bright = if (strobeOn) (s.brightness * master).coerceIn(0f, 1f) else 0f
        val hue = if (pad != null) XyPad.hueShift(pad) else 0f
        val padBright = if (pad != null) XyPad.brightness(pad) else 1f
        val sat = if (pad != null) XyPad.saturation(pad) else 0f
        val recolor = hue != 0f || padBright != 1f || sat != 0f
        val grouped = rt.grouped
        for (i in 0 until n) {
            // Mirror: the effect's first pixel sits in the middle, spreading to both ends.
            val src = if (mirror) (abs(2 * i - (n - 1)) / 2).coerceAtMost(renderN - 1) else i
            val dst = if (flip) n - 1 - i else i
            val c = rt.work[src]
            grouped[dst] = Rgb.scale(if (recolor) XyPad.recolor(c, hue, padBright, sat) else c, bright)
        }
        PixelMapper.expandGrouping(grouped, v.grouping, rt.out)
        if (serpentine) PixelMapper.wire(rt.out, v.rows, true, rt.wired)
    }

    private fun sendDevice(dev: DeviceConfig, rt: DeviceRuntime, sender: DdpSender, now: Long) {
        if (rt.host != dev.host) {
            rt.host = dev.host
            rt.address = null
            rt.nextResolveNanos = 0
        }
        if (rt.address == null && now >= rt.nextResolveNanos) {
            rt.address = runCatching { InetAddress.getByName(dev.host) }.getOrNull()
            if (rt.address == null) rt.nextResolveNanos = now + 5_000_000_000L
        }
        val addr = rt.address ?: return
        try {
            sender.send(addr, rt.pixels)
        } catch (e: Exception) {
            rt.address = null
            rt.nextResolveNanos = now + 2_000_000_000L
        }
    }

    // ---------------------------------------------------------------- backup / restore

    /** Everything the app saves, as it will be stored (a held scene counts as released). */
    fun snapshot(): AppState = AppState(
        devices = _devices.value,
        virtuals = held?.let { SceneLogic.restore(it, _virtuals.value) } ?: _virtuals.value,
        scenes = _scenes.value,
        presets = _userPresets.value,
        audioSource = _audioSource.value,
        sensitivity = _sensitivity.value,
        masterBrightness = _masterBrightness.value,
        minVolume = _minVolume.value,
        tapBpm = _tapBpm.value,
        tapSimulate = _tapSimulate.value,
    )

    /** The backup file's text (pretty-printed JSON). */
    fun exportBackup(appVersion: String, exportedAt: String): String =
        Backup.encode(snapshot(), SCHEMA, appVersion, exportedAt).toString(2)

    /** Replaces devices, lights, scenes, own presets and settings with [state], and saves. */
    @Synchronized
    fun restore(state: AppState) {
        releaseHeldScene()
        stopPlaylist()
        val sourceChanged = state.audioSource != _audioSource.value
        _paused.value = emptySet()
        _devices.value = state.devices
        _virtuals.value = state.virtuals
        _scenes.value = state.scenes
        _userPresets.value = state.presets
        _audioSource.value = state.audioSource
        _sensitivity.value = state.sensitivity
        _masterBrightness.value = state.masterBrightness
        _minVolume.value = state.minVolume
        analyzer.tapBpm = state.tapBpm
        _tapBpm.value = analyzer.tapBpm
        _tapSimulate.value = state.tapSimulate
        save()
        if (sourceChanged && loopRunning) restartCapture()
    }

    // ---------------------------------------------------------------- persistence

    private fun load() {
        val devicesJson = prefs.getString("devices", null)
        var (devices, virtuals) = when {
            devicesJson == null -> {
                val preview = DeviceConfig(name = "Phone preview", kind = DeviceKind.PREVIEW, ledCount = 90)
                listOf(preview) to listOf(VirtualLogic.autoVirtual(preview))
            }
            else -> {
                val d = runCatching {
                    val arr = JSONArray(devicesJson)
                    (0 until arr.length()).map { DeviceConfig.fromJson(arr.getJSONObject(it)) }
                }.getOrDefault(emptyList())
                val v = runCatching {
                    val arr = JSONArray(prefs.getString("virtuals", "[]"))
                    (0 until arr.length()).map { VirtualConfig.fromJson(arr.getJSONObject(it)) }
                }.getOrDefault(emptyList())
                d to v
            }
        }
        // Saved data may name effects that no longer exist.
        virtuals = VirtualLogic.sync(devices, virtuals).map { v ->
            if (Effects.exists(v.settings.effectId)) v
            else v.copy(settings = v.settings.copy(effectId = "wavelength"))
        }
        _devices.value = devices
        _virtuals.value = virtuals
        _audioSource.value = runCatching {
            AudioSourceType.valueOf(prefs.getString("audioSource", "MICROPHONE")!!)
        }.getOrDefault(AudioSourceType.MICROPHONE)
        _sensitivity.value = prefs.getFloat("sensitivity", 0.5f)
        _masterBrightness.value = prefs.getFloat("masterBrightness", 1f)
        analyzer.tapBpm = prefs.getFloat("tapBpm", 120f)
        _tapBpm.value = analyzer.tapBpm
        _tapSimulate.value = prefs.getBoolean("tapSimulate", true)
        _minVolume.value = AudioSourceType.entries.associateWith {
            prefs.getFloat("minVolume.${it.name}", AudioAnalyzer.DEFAULT_MIN_VOLUME)
        }
        _scenes.value = runCatching {
            val arr = JSONArray(prefs.getString("scenes", "[]"))
            (0 until arr.length()).map { Scene.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        _userPresets.value = runCatching {
            val arr = JSONArray(prefs.getString("presets", "[]"))
            (0 until arr.length()).map { Preset.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val arr = JSONArray()
        _devices.value.forEach { arr.put(it.toJson()) }
        val virtuals = JSONArray()
        // A held scene is temporary: store the lights as they will be after it is released.
        val toStore = held?.let { SceneLogic.restore(it, _virtuals.value) } ?: _virtuals.value
        toStore.forEach { virtuals.put(it.toJson()) }
        val presets = JSONArray()
        _userPresets.value.forEach { presets.put(it.toJson()) }
        val scenes = JSONArray()
        _scenes.value.forEach { scenes.put(it.toJson()) }
        val edit = prefs.edit()
        _minVolume.value.forEach { (source, v) -> edit.putFloat("minVolume.${source.name}", v) }
        edit
            .putInt("schema", SCHEMA)
            .putFloat("tapBpm", _tapBpm.value)
            .putBoolean("tapSimulate", _tapSimulate.value)
            .putString("devices", arr.toString())
            .putString("virtuals", virtuals.toString())
            .putString("audioSource", _audioSource.value.name)
            .putFloat("sensitivity", _sensitivity.value)
            .putFloat("masterBrightness", _masterBrightness.value)
            .putString("presets", presets.toString())
            .putString("scenes", scenes.toString())
            .apply()
    }
}
