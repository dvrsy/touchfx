package app.touchfx.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.audio.AudioSourceType
import app.touchfx.effects.Palettes
import app.touchfx.engine.LightEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(padding: PaddingValues, onRequestPhoneAudio: () -> Unit) {
    val source by LightEngine.audioSource.collectAsStateWithLifecycle()
    val audio by LightEngine.audio.collectAsStateWithLifecycle()
    val problem by LightEngine.audioProblem.collectAsStateWithLifecycle()
    val sensitivity by LightEngine.sensitivity.collectAsStateWithLifecycle()
    val running by LightEngine.running.collectAsStateWithLifecycle()
    val minVolumes by LightEngine.minVolume.collectAsStateWithLifecycle()
    val threshold = minVolumes[source] ?: AudioAnalyzer.DEFAULT_MIN_VOLUME

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SectionTitle("Listen to")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = source == AudioSourceType.MICROPHONE,
                onClick = { LightEngine.setAudioSource(AudioSourceType.MICROPHONE) },
                shape = segmentShape(0, 3),
                icon = { Icon(Icons.Rounded.Mic, null) },
            ) { Text("Mic") }
            SegmentedButton(
                selected = source == AudioSourceType.PHONE_AUDIO,
                onClick = {
                    LightEngine.setAudioSource(AudioSourceType.PHONE_AUDIO)
                    if (LightEngine.projection == null) onRequestPhoneAudio()
                },
                shape = segmentShape(1, 3),
                icon = { Icon(Icons.Rounded.MusicNote, null) },
            ) { Text("Phone") }
            SegmentedButton(
                selected = source == AudioSourceType.TAP,
                onClick = { LightEngine.setAudioSource(AudioSourceType.TAP) },
                shape = segmentShape(2, 3),
                icon = { Icon(Icons.Rounded.TouchApp, null) },
            ) { Text("Tap") }
        }
        Text(
            when (source) {
                AudioSourceType.MICROPHONE -> "Reacts to sound in the room."
                AudioSourceType.PHONE_AUDIO -> "Reacts to music playing on this phone (Spotify, YouTube, …). Some apps block this."
                AudioSourceType.TAP -> "You tap the beat; no sound is listened to. Beat-synced effects follow your taps; " +
                    "with Simulate sound on, the other effects move to a made-up drum pattern."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        val p = problem
        if (running && p != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p, modifier = Modifier.weight(1f))
                    if (source == AudioSourceType.PHONE_AUDIO) {
                        Button(shape = TouchShapes.control, onClick = onRequestPhoneAudio) { Text("Allow") }
                    }
                }
            }
        }

        SectionTitle("Live")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp)) {
                Spectrum(audio.bands, Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.85f * audio.beatPulse)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Kick", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(20.dp))
                    // Tempo grid that beat-synced effects follow; it pauses while silent.
                    val tempoOn = running && !audio.silent
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(
                                    alpha = if (tempoOn) 0.15f + 0.85f * (1f - audio.beatPhase) * (1f - audio.beatPhase) else 0.1f
                                )
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (tempoOn) "Tempo ${audio.bpm.roundToInt()} BPM" else "Tempo paused",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            !running -> "Engine stopped"
                            audio.silent -> "Silence"
                            else -> "Listening"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meter("Volume", audio.volume)
                Meter("Bass", audio.lows)
                Meter("Mids", audio.mids)
                Meter("Highs", audio.highs)
            }
        }

        if (source == AudioSourceType.TAP) {
            TapTempo(running = running, pulse = audio.beatPulse)
            return@Column
        }

        SectionTitle("Sensitivity")
        LabeledSlider("How strongly lights react", sensitivity) { LightEngine.setSensitivity(it) }
        Text(
            "Raise it for quiet rooms or low phone volume; lower it if everything stays maxed out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Silence threshold")
        ThresholdMeter(
            level = if (running) audio.level else 0f,
            threshold = threshold,
            open = running && !audio.silent,
            modifier = Modifier.fillMaxWidth().height(14.dp),
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (source == AudioSourceType.MICROPHONE) "Threshold for the microphone" else "Threshold for phone audio",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${AudioAnalyzer.levelToDb(threshold).roundToInt()} dB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = threshold.coerceIn(0f, THRESHOLD_MAX),
            onValueChange = { LightEngine.setMinVolume(it) },
            valueRange = 0f..THRESHOLD_MAX,
        )
        Text(
            "Lights stay dark while the input level (bar) is below the marker. Set it just above " +
                "the background noise. Microphone and phone audio each keep their own value.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tap tempo: a big pad to tap the beat, the tempo, and buttons to fine-tune it. */
@Composable
private fun TapTempo(running: Boolean, pulse: Float) {
    val bpm by LightEngine.tapBpm.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    SectionTitle("Tap tempo")
    Text(
        "${bpm.roundToInt()} BPM",
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val flash = if (running) pulse else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f + 0.6f * flash)
            )
            .pointerInput(Unit) {
                // React on touch-down, not release, so the tap lands on the beat.
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    LightEngine.tapTempo()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("TAP", style = MaterialTheme.typography.headlineLarge)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf<Pair<String, (Float) -> Float>>(
            "÷2" to { it / 2 }, "−1" to { it.roundToInt() - 1f }, "+1" to { it.roundToInt() + 1f }, "×2" to { it * 2 },
        ).forEach { (label, change) ->
            OutlinedButton(shape = TouchShapes.control, onClick = { LightEngine.setTapBpm(change(bpm)) }, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
    val simulate by LightEngine.tapSimulate.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Simulate sound", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Makes up a drum pattern from your beat (kick, snare, hi-hat) so effects that need sound " +
                    "move in time too. The frequencies aren't real, only the rhythm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = simulate, onCheckedChange = { LightEngine.setTapSimulate(it) })
    }
    Text(
        "Tap 4 or more times in time with the music to set the tempo; it keeps going until you tap " +
            "again or switch back to Mic or Phone. A single tap after a pause marks the first beat of a bar " +
            "without changing the tempo." + if (!running) " Start the lights to see the beat." else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Highest threshold offered (-20 dBFS); anything above would gate out normal music. */
private const val THRESHOLD_MAX = 0.8f

/** Input level bar with a marker at the silence threshold; coloured while sound passes the gate. */
@Composable
private fun ThresholdMeter(level: Float, threshold: Float, open: Boolean, modifier: Modifier) {
    // LED dots: blue while sound passes the gate, grey while it's held back.
    DotMeter(
        value = level / THRESHOLD_MAX,
        modifier = modifier,
        dots = 40,
        lit = if (open) TouchColors.blue else MaterialTheme.colorScheme.outline,
        marker = threshold / THRESHOLD_MAX,
    )
}

@Composable
private fun Meter(label: String, value: Float) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        DotMeter(value, Modifier.weight(1f).height(10.dp))
    }
}

@Composable
private fun Spectrum(bands: FloatArray, modifier: Modifier) {
    // Mixer-style: blue bars, the top in orange when a band is loud.
    Canvas(modifier) {
        val n = bands.size
        val slot = size.width / n
        val barW = slot * 0.7f
        drawRoundRect(TouchColors.well, cornerRadius = CornerRadius(8f, 8f))
        val full = size.height - 8f
        for (i in 0 until n) {
            val v = bands[i].coerceIn(0f, 1f)
            val h = full * maxOf(0.02f, v)
            val x = i * slot + (slot - barW) / 2
            val blueH = minOf(h, full * 0.85f)
            drawRect(TouchColors.blue, topLeft = Offset(x, size.height - 4f - blueH), size = Size(barW, blueH))
            if (h > blueH) drawRect(TouchColors.orange, topLeft = Offset(x, size.height - 4f - h), size = Size(barW, h - blueH))
        }
    }
}
