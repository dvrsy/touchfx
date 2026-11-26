package app.touchfx.effects

/**
 * Effect Chase (not in LedFx): like Segment Chase, but the lit parts show other effects instead
 * of a solid colour. One or more effects are chosen ([EffectListParam] "effects"); each uses the
 * light's own saved settings for it and runs over the whole light every frame (so moving effects
 * keep moving), and the chase decides which part shows which effect and how brightly.
 *
 * Which effect a part shows: "Each step" (every new step brings the next effect) or "Per part"
 * (part 1 always effect 1, …). Mode "Effect shift" lights all parts and moves the effects on by
 * one part per step. Stepping is shared with Segment Chase ([ChaseStepper]).
 */
class EffectChase : Effect() {
    private val stepper = ChaseStepper()
    private val children = ChildEffects()
    /** For "Each step": the chosen-effect index each part was lit with. */
    private var partEffect = IntArray(0)

    override fun render(ctx: RenderContext, out: IntArray) {
        val n = out.size
        if (n == 0) return
        val p = ctx.p
        val ids = usableEffects(p.s("effects"), ctx.rows > 1)
        if (ids.isEmpty()) { out.fill(0); return }

        val bySegment = ctx.segmentCount > 1 && ctx.segmentOf.size == n
        val parts = if (bySegment) ctx.segmentCount else p.i("parts").coerceIn(1, n)
        val mode = p.s("mode")
        val newStep = stepper.update(ctx, parts, fill = mode == "Fill", allLit = mode == "Effect shift")
        if (partEffect.size != parts) partEffect = IntArray(parts) { it % ids.size }
        // Each new step brings the next effect to the part it lights.
        if (newStep && stepper.active >= 0) partEffect[stepper.active] = Math.floorMod(stepper.shift - 1, ids.size)
        val perPart = p.s("effect_per") == "Per part"
        fun effectFor(part: Int) = when {
            mode == "Effect shift" -> Math.floorMod(part + stepper.shift, ids.size)
            perPart -> part % ids.size
            else -> partEffect[part] % ids.size
        }

        // Run every chosen effect over the whole light, with its own settings.
        val rendered = children.render(ctx, ids, n)
        val buffers = ids.map { rendered.getValue(it) }

        val levels = stepper.levels
        for (i in 0 until n) {
            val part = if (bySegment) ctx.segmentOf[i].coerceIn(0, parts - 1) else (i.toLong() * parts / n).toInt()
            val level = levels[part]
            out[i] = if (level <= 0.003f) 0 else Rgb.scale(buffers[effectFor(part)][i], level)
        }
    }

    companion object {
        fun usableEffects(value: String, matrix: Boolean): List<String> = ChildEffects.usable(value, matrix)
    }
}
