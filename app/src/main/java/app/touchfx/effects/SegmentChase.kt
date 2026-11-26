package app.touchfx.effects

import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random

/**
 * Segment Chase (not in LedFx): steps through a group's segments in time with the music —
 * device 1, then 2, then 3. On side-by-side matrix panels a step is a whole panel. On a single
 * strip the strip is split into `parts` equal parts.
 *
 * Modes: Chase (one part at a time, with a fading trail), Fill (parts light up one after
 * another, then it starts over) and Colour shift (all parts lit, colours move on each step).
 *
 * Stepping ("Step on"):
 * - Tempo / Kicks: a step every so many beats (speed) or on every kick.
 * - Kick runs a cycle / Beat runs a cycle: a trigger runs one pass through all parts within
 *   `cycle_length` beats (at most the time between cycles), then waits (dark, in Chase). Speed
 *   sets how often: on the beat grid every 1/speed beats; on kicks, faster than 1 per beat runs
 *   a burst of that many cycles per kick, slower lets only every 2nd / 4th kick start one.
 *   A new trigger during a pass starts a fresh pass.
 */
class SegmentChase : Effect() {
    private val stepper = ChaseStepper()

    override fun render(ctx: RenderContext, out: IntArray) {
        val n = out.size
        if (n == 0) return
        val p = ctx.p
        val bySegment = ctx.segmentCount > 1 && ctx.segmentOf.size == n
        val parts = if (bySegment) ctx.segmentCount else p.i("parts").coerceIn(1, n)
        val mode = p.s("mode")
        stepper.update(ctx, parts, fill = mode == "Fill", allLit = mode == "Colour shift")
        val levels = stepper.levels

        // ---- colours
        val one = p.b("one_color")
        val solid = p.color("color")
        val offset = if (mode == "Colour shift") Math.floorMod(stepper.shift, parts) else 0
        for (i in 0 until n) {
            val part = if (bySegment) ctx.segmentOf[i].coerceIn(0, parts - 1) else (i.toLong() * parts / n).toInt()
            val level = if (mode == "Colour shift" && one) {
                // One colour: every second part lit, swapping on each step.
                if ((part + offset) % 2 == 0) 1f else 0f
            } else levels[part]
            if (level <= 0.003f) { out[i] = 0; continue }
            val colorIndex = (part + offset) % parts
            val c = if (one) solid else ctx.grad(if (parts > 1) colorIndex.toFloat() / (parts - 1) else 0f)
            out[i] = Rgb.scale(c, level)
        }
    }

    companion object {
        /** Speed choice -> steps per beat. */
        val SPEEDS = ChaseStepper.SPEEDS
        val STEP_ON = ChaseStepper.STEP_ON
    }
}

/**
 * The stepping shared by Segment Chase and Effect Chase: which part is lit when, following the
 * tempo, kicks or triggered cycles (settings `step_on`, `speed`, `cycle_length`, `order`, `trail`),
 * and how bright every part is (held parts full, the others fading with the trail).
 */
class ChaseStepper {
    /** Brightness 0..1 per part, updated by [update]. */
    var levels = FloatArray(0); private set
    /** Part lit by the current step, or -1 while idle between triggered cycles. */
    var active = -1; private set
    /** Advances by one on every new step (colour or effect shift). */
    var shift = 0; private set
    /** Identity of the current step, to notice when a new one begins; null = idle. */
    private var lastStepKey: Long? = Long.MIN_VALUE
    private var kicks = 0L
    private var filled = BooleanArray(0)
    // Triggered cycles
    private var runs = 0L
    private var runStart = Double.NaN
    /** Cycles in the current run (more than 1 for a kick burst at "4 per beat" etc.). */
    private var burst = 1
    /** Last position on the tempo grid, in cycles ("Beat runs a cycle"). */
    private var lastGridPos = Long.MIN_VALUE

    /**
     * Advances to this frame. [fill]: parts stay lit until all had their turn; [allLit]: every
     * part is lit (shift modes). Returns true on the frame a new step begins.
     */
    fun update(ctx: RenderContext, parts: Int, fill: Boolean, allLit: Boolean): Boolean {
        val p = ctx.p
        if (levels.size != parts) {
            levels = FloatArray(parts); filled = BooleanArray(parts); lastStepKey = Long.MIN_VALUE; active = -1
        }
        val order = p.s("order")

        // ---- which step are we on? (key identifies it; index is its place in the order)
        val perBeat = SPEEDS[p.s("speed")] ?: 1f
        if (ctx.audio.beat) kicks++
        val stepOn = p.s("step_on")
        var key: Long?
        var index: Long
        when (stepOn) {
            "Kick runs a cycle", "Beat runs a cycle" -> {
                // Speed sets how often a cycle starts: every 1/perBeat beats.
                val beatSec = 60.0 / ctx.audio.bpm.coerceIn(30f, 300f)
                val interval = beatSec / perBeat
                if (stepOn == "Beat runs a cycle") {
                    // On the tempo grid: a cycle at every 1/perBeat of a beat.
                    val pos = floor((ctx.audio.beatCount + ctx.audio.beatPhase) * perBeat.toDouble() + 1e-6).toLong()
                    if (pos != lastGridPos) {
                        if (lastGridPos != Long.MIN_VALUE) { runs++; runStart = ctx.time; burst = 1 }
                        lastGridPos = pos
                    }
                } else if (ctx.audio.beat) {
                    // On kicks: faster than 1 per beat runs a burst of cycles after each kick;
                    // slower only lets every 2nd (4th) kick start one.
                    val everyNth = maxOf(1L, Math.round(1.0 / perBeat))
                    if (kicks % everyNth == 0L) {
                        runs++; runStart = ctx.time; burst = maxOf(1, perBeat.toInt())
                    }
                }
                // A pass never runs longer than the time between cycles.
                val cycleSec = minOf(p.f("cycle_length").coerceAtLeast(0.05f) * beatSec, interval)
                val elapsed = ctx.time - runStart
                // Tiny epsilons: frame times like 10/60 s should land on the step they're meant to.
                val nth = if (elapsed >= 0.0) (elapsed / interval + 1e-6).toLong() else 0L
                val inCycle = elapsed - nth * interval
                if (!runStart.isNaN() && elapsed >= 0.0 && nth < burst && inCycle < cycleSec) {
                    index = (inCycle / (cycleSec / parts) + 1e-6).toLong().coerceIn(0L, parts - 1L)
                    key = (runs * 64 + nth) * 4096 + index
                } else {
                    index = 0; key = null
                }
            }
            "Kicks" -> {
                // Kicks can't be split, so faster than one step per kick means every kick.
                index = kicks / maxOf(1L, (1f / perBeat).toLong()); key = index
            }
            else -> {
                index = floor((ctx.audio.beatCount + ctx.audio.beatPhase) * perBeat.toDouble()).toLong(); key = index
            }
        }

        var newStep = false
        if (key != lastStepKey) {
            if (key == null) {
                active = -1 // idle between triggered cycles
            } else {
                val newRun = stepOn.endsWith("runs a cycle") && index == 0L
                active = partFor(index, parts, order)
                shift++
                newStep = true
                if (fill) {
                    // Start over once every part has had its turn, or with each triggered cycle.
                    if (newRun || filled.all { it }) filled.fill(false)
                    filled[active] = true
                }
            }
            lastStepKey = key
        }

        // ---- brightness per part: held parts at full, the others fade (the trail)
        val trail = p.f("trail").coerceIn(0f, 1f)
        val fadePerSecond = 30f * (1f - trail) * (1f - trail) + 0.8f
        val keep = exp(-fadePerSecond * ctx.dt)
        for (k in 0 until parts) {
            val held = when {
                fill -> filled[k] && active >= 0
                allLit -> true
                else -> k == active
            }
            levels[k] = if (held) 1f else levels[k] * keep
        }
        return newStep
    }

    /** The part lit on step [index] for the chosen order. */
    private fun partFor(index: Long, parts: Int, order: String): Int {
        if (parts <= 1) return 0
        return when (order) {
            "Backward" -> parts - 1 - Math.floorMod(index, parts.toLong()).toInt()
            "Bounce" -> {
                val period = 2L * parts - 2
                val k = Math.floorMod(index, period).toInt()
                if (k < parts) k else (period - k).toInt()
            }
            "Random" -> {
                if (active < 0) return Random.nextInt(parts)
                var r = Random.nextInt(parts - 1)
                if (r >= active) r++ // never the same part twice in a row
                r
            }
            else -> Math.floorMod(index, parts.toLong()).toInt()
        }
    }

    companion object {
        /** Speed choice -> steps per beat. */
        val SPEEDS: Map<String, Float> = linkedMapOf(
            "4 per beat" to 4f, "2 per beat" to 2f, "1 per beat" to 1f,
            "Every 2 beats" to 0.5f, "Every bar (4 beats)" to 0.25f,
        )
        val STEP_ON = listOf("Tempo", "Kicks", "Kick runs a cycle", "Beat runs a cycle")
    }
}
