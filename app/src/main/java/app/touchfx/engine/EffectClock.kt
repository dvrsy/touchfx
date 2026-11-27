package app.touchfx.engine

import app.touchfx.audio.AudioFrame
import app.touchfx.effects.Effect
import app.touchfx.effects.RenderContext
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/** How an effect moves in time, and so how the pad's Overall speed can change it. */
enum class Timing {
    /** Follows the clock: Overall speed runs its clock faster or slower (smooth). */
    CLOCK,

    /**
     * Moves a fixed step each frame (ported that way from LedFx): Overall speed renders it more or
     * less often, like a higher or lower frame rate just for this effect.
     */
    STEPS,

    /** Follows the beat: Overall speed switches to quarter, half, double or quadruple time. */
    BEAT,

    /** Follows only the sound (or stands still): Overall speed can't change it. */
    NONE,
}

/**
 * Renders one light's effect for one frame at the pad's Overall speed, in the way that suits
 * the effect's [Timing]. One instance per light; kept free of Android so it can be unit tested.
 */
class EffectClock {
    /** How far this light's clock is ahead of (or behind) the engine's; kept when the pad is let go. */
    var timeOffset = 0.0
        private set
    private var stepCredit = 0f
    private var lastBeat = Int.MIN_VALUE
    private var last = IntArray(0)

    /** Forgets the last picture and beat (another effect starts); the clock offset stays. */
    fun reset() {
        stepCredit = 0f
        lastBeat = Int.MIN_VALUE
        last = IntArray(0)
    }

    /**
     * Renders [effect] into [out]. [ctx] holds the engine's time, frame length and audio for this
     * frame; [scale] is the Overall speed (1 = normal, 0.25..4).
     */
    fun render(effect: Effect, timing: Timing, scale: Float, ctx: RenderContext, out: IntArray) {
        val frameTime = ctx.time
        val frameDt = ctx.dt
        when (timing) {
            Timing.CLOCK -> {
                timeOffset += (scale - 1f) * frameDt
                ctx.time = frameTime + timeOffset
                ctx.dt = frameDt * scale
                effect.render(ctx, out)
            }
            Timing.STEPS -> {
                stepCredit += scale
                val steps = floor(stepCredit).toInt()
                stepCredit -= steps
                if (steps == 0 && last.size == out.size) {
                    // Not this frame's turn: show the last picture; the effect's clock stands still.
                    last.copyInto(out)
                    timeOffset -= frameDt
                    return
                }
                val n = steps.coerceIn(1, MAX_STEPS)
                for (i in 0 until n) {
                    ctx.time = frameTime + timeOffset + i * frameDt
                    ctx.dt = frameDt
                    effect.render(ctx, out)
                }
                timeOffset += (n - 1) * frameDt
                if (last.size != out.size) last = IntArray(out.size)
                out.copyInto(last)
            }
            Timing.BEAT -> {
                ctx.time = frameTime + timeOffset
                // The context is shared by all lights: put the real audio back afterwards.
                val audio = ctx.audio
                ctx.audio = beatTime(audio, beatSteps(scale))
                try {
                    effect.render(ctx, out)
                } finally {
                    ctx.audio = audio
                }
            }
            Timing.NONE -> {
                ctx.time = frameTime + timeOffset
                effect.render(ctx, out)
            }
        }
    }

    /**
     * The audio as if the beat ran 2^[steps] times as fast: -2 = quarter time (one beat lasts four),
     * -1 = half, 0 = as it is, 1 = double, 2 = quadruple. Still lands on the real beats.
     */
    fun beatTime(a: AudioFrame, steps: Int): AudioFrame {
        if (steps == 0) {
            lastBeat = a.beatCount
            return a
        }
        val phase: Float
        val count: Int
        if (steps < 0) {
            val m = 1 shl -steps
            phase = (Math.floorMod(a.beatCount, m) + a.beatPhase) / m
            count = Math.floorDiv(a.beatCount, m)
        } else {
            val m = 1 shl steps
            val sub = (a.beatPhase * m).toInt().coerceIn(0, m - 1)
            phase = a.beatPhase * m - sub
            count = a.beatCount * m + sub
        }
        val started = lastBeat != Int.MIN_VALUE && count != lastBeat
        lastBeat = count
        return AudioFrame(
            bands = a.bands, bandsRaw = a.bandsRaw, volume = a.volume, power = a.power, powerRaw = a.powerRaw,
            beat = a.beat, onset = a.onset, beatPulse = a.beatPulse,
            beatPhase = phase.coerceIn(0f, 0.999f), barPhase = Math.floorMod(count, 4) + phase.coerceIn(0f, 0.999f),
            beatCount = count, tempoBeat = started,
            bpm = a.bpm * (if (steps < 0) 1f / (1 shl -steps) else (1 shl steps).toFloat()),
            pitchMidi = a.pitchMidi, silent = a.silent, level = a.level,
        )
    }

    companion object {
        /** Most renders in one frame (Overall speed tops out at 4×). */
        const val MAX_STEPS = 4

        /** Overall speed rounded to whole doublings for beat effects: 0.25× → -2 … 4× → 2. */
        fun beatSteps(scale: Float): Int =
            if (scale <= 0f) -2 else (ln(scale.toDouble()) / ln(2.0)).roundToInt().coerceIn(-2, 2)
    }
}
