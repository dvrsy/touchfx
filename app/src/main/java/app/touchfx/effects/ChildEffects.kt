package app.touchfx.effects

/**
 * Runs other effects inside an effect (Effect Chase, Blend). Each chosen effect keeps its own
 * instance, runs over the whole light every frame with the light's own saved settings for it
 * ([RenderContext.effectConfigs]), and renders into its own buffer.
 */
class ChildEffects {
    private class Child(val effect: Effect, val values: ParamValues) {
        var buf = IntArray(0)
    }

    private val children = HashMap<String, Child>()

    /**
     * Renders [ids] (each once, even if listed twice) for a light of [n] pixels and returns their
     * buffers by id. A failing effect renders black. Effects no longer used are dropped.
     */
    fun render(ctx: RenderContext, ids: Collection<String>, n: Int): Map<String, IntArray> {
        val own = ctx.p
        val out = LinkedHashMap<String, IntArray>()
        for (id in ids) {
            if (id in out) continue
            val info = Effects.info(id)
            val child = children.getOrPut(id) { Child(info.create(), ParamValues(info.params)) }
            child.values.values = ctx.effectConfigs[id] ?: emptyMap()
            if (child.buf.size != n) child.buf = IntArray(n)
            ctx.p = child.values
            try { child.effect.render(ctx, child.buf) } catch (e: Exception) { child.buf.fill(0) }
            out[id] = child.buf
        }
        ctx.p = own
        children.keys.retainAll(out.keys)
        return out
    }

    companion object {
        /** Chosen effects that exist, aren't combining effects themselves, and fit the light (2D only on matrices). */
        fun usable(value: String, matrix: Boolean): List<String> = EffectListParam.parse(value).filter { id ->
            Effects.exists(id) && Effects.info(id).let { !it.composite && (matrix || !it.is2d) }
        }
    }
}
