package app.touchfx.effects

import kotlin.math.max
import kotlin.math.min

/**
 * Blend: puts one effect over another, like LedFx's Blender, which combines a background, a
 * foreground and a mask. Here these are chosen effects (with the light's own settings for them)
 * rather than other lights. Works on strips and matrices.
 *
 * Modes: "Over" uses the mask's brightness (the foreground itself unless a mask effect is chosen):
 * bright mask = foreground, dark = background; `mask_cutoff` below 1 makes it a hard edge, as in
 * LedFx. "Mix" crossfades; "Add", "Screen", "Multiply", "Lighten", "Darken" are the usual layer
 * modes. `amount` is how strongly the foreground is blended in; `swap` exchanges foreground and
 * background (LedFx's invert).
 */
class Blend : Effect() {
    private val children = ChildEffects()

    override fun render(ctx: RenderContext, out: IntArray) {
        val n = out.size
        val p = ctx.p
        val matrix = ctx.rows > 1
        var bgId = ChildEffects.usable(p.s("background"), matrix).firstOrNull()
        var fgId = ChildEffects.usable(p.s("foreground"), matrix).firstOrNull()
        if (p.b("swap")) { val t = bgId; bgId = fgId; fgId = t }
        val maskId = ChildEffects.usable(p.s("mask"), matrix).firstOrNull()
        val buffers = children.render(ctx, listOfNotNull(bgId, fgId, maskId), n)
        val bg = bgId?.let { buffers[it] }
        val fg = fgId?.let { buffers[it] }
        val mask = maskId?.let { buffers[it] } ?: fg

        val amount = p.f("amount").coerceIn(0f, 1f)
        val cutoff = p.f("mask_cutoff").coerceIn(0.01f, 1f)
        val mode = p.s("mode")
        for (i in 0 until n) {
            val b = bg?.get(i) ?: 0
            val f = fg?.get(i) ?: 0
            out[i] = when (mode) {
                "Mix" -> Rgb.lerp(b, f, amount)
                "Add" -> Rgb.add(b, Rgb.scale(f, amount))
                "Screen" -> channels(b, f) { x, y -> 255 - (255 - x) * (255 - y * amount) / 255 }
                "Multiply" -> channels(b, f) { x, y -> x * (1 - amount + amount * y / 255f) }
                "Lighten" -> Rgb.lerp(b, channels(b, f) { x, y -> max(x, y).toFloat() }, amount)
                "Darken" -> Rgb.lerp(b, channels(b, f) { x, y -> min(x, y).toFloat() }, amount)
                else -> { // "Over": the mask's brightness decides, like LedFx's Blender
                    val lum = luminance(mask?.get(i) ?: 0)
                    val alpha = if (cutoff < 1f) (if (lum > 1f - cutoff) 1f else 0f) else lum
                    Rgb.lerp(b, f, alpha * amount)
                }
            }
        }
    }

    private inline fun channels(a: Int, b: Int, f: (Int, Int) -> Float): Int = Rgb.of(
        f(Rgb.r(a), Rgb.r(b)).toInt(), f(Rgb.g(a), Rgb.g(b)).toInt(), f(Rgb.b(a), Rgb.b(b)).toInt(),
    )

    companion object {
        /** Brightness 0..1 (ITU-R 601 luma, as PIL's "L" conversion LedFx uses). */
        fun luminance(c: Int) = (0.299f * Rgb.r(c) + 0.587f * Rgb.g(c) + 0.114f * Rgb.b(c)) / 255f
        val MODES = listOf("Over", "Mix", "Add", "Screen", "Multiply", "Lighten", "Darken")
    }
}
