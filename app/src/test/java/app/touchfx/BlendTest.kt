package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.effects.Blend
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.RenderContext
import app.touchfx.effects.Rgb

class BlendTest {
    private val blue = 0x0000FF
    private val red = 0xFF0000
    private val text = mapOf("text" to "HI", "speed_option_1" to 0f) // still, centred, white

    /** Blend on a 32x8 matrix: Single Color = blue, Gradient = red (red palette), Texter = white "HI". */
    private fun blend(settings: Map<String, Any>, rows: Int = 8): IntArray {
        val info = Effects.info("blend")
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, settings)
            palette = Palettes.get("red")
            this.rows = rows
            effectConfigs = mapOf("singleColor" to mapOf("color" to blue), "texter2d" to text)
        }
        return IntArray(32 * rows).also { info.create().render(ctx, it) }
    }

    /** Texter alone: which pixels are text. */
    private val textPixels: Set<Int> by lazy {
        val info = Effects.info("texter2d")
        val ctx = RenderContext().apply { p = ParamValues(info.params, text); rows = 8 }
        IntArray(256).also { info.create().render(ctx, it) }.withIndex().filter { it.value != 0 }.map { it.index }.toSet()
    }

    @Test
    fun overShowsTheForegroundWhereItIsBright() {
        val out = blend(mapOf("background" to "singleColor", "foreground" to "texter2d", "mode" to "Over"))
        assertTrue(textPixels.isNotEmpty())
        out.forEachIndexed { i, c -> assertEquals("pixel $i", if (i in textPixels) 0xFFFFFF else blue, c) }
    }

    @Test
    fun amountWeakensTheForeground() {
        val out = blend(mapOf("background" to "singleColor", "foreground" to "texter2d", "amount" to 0.5f))
        val t = out[textPixels.first()]
        assertEquals(Rgb.lerp(blue, 0xFFFFFF, 0.5f), t)
    }

    @Test
    fun layerModesOnSolidColours() {
        fun mode(m: String, amount: Float = 1f) =
            blend(mapOf("background" to "singleColor", "foreground" to "gradient", "mode" to m, "amount" to amount))[0]
        assertEquals(Rgb.lerp(blue, red, 0.5f), mode("Mix", 0.5f))
        assertEquals(0xFF00FF, mode("Add"))
        assertEquals(0xFF00FF, mode("Screen"))
        assertEquals(0x000000, mode("Multiply"))
        assertEquals(blue, mode("Multiply", 0f))
        assertEquals(0xFF00FF, mode("Lighten"))
        assertEquals(0x000000, mode("Darken"))
    }

    @Test
    fun aSeparateMaskDecidesWhereTheForegroundShows() {
        val out = blend(mapOf("background" to "singleColor", "foreground" to "gradient", "mask" to "texter2d"))
        out.forEachIndexed { i, c -> assertEquals(if (i in textPixels) red else blue, c) }
    }

    @Test
    fun maskCutoffMakesAHardEdge() {
        // Blue as the mask is dim (luma 0.11): soft = a little red, hard cutoff = all or nothing.
        fun over(cutoff: Float) = blend(mapOf("background" to "texter2d", "foreground" to "gradient", "mask" to "singleColor", "mask_cutoff" to cutoff))[0]
        assertEquals(Rgb.lerp(0, red, Blend.luminance(blue)), over(1f))
        assertEquals("dim mask below the cutoff: background", 0, over(0.5f))
        assertEquals("dim mask above the cutoff: foreground", red, over(0.95f))
    }

    @Test
    fun swapExchangesForegroundAndBackground() {
        val out = blend(mapOf("background" to "texter2d", "foreground" to "singleColor", "swap" to true))
        out.forEachIndexed { i, c -> assertEquals(if (i in textPixels) 0xFFFFFF else blue, c) }
    }

    @Test
    fun matrixEffectsCountAsBlackOnAStrip() {
        val out = blend(mapOf("background" to "singleColor", "foreground" to "texter2d"), rows = 1)
        assertTrue(out.all { it == blue })
    }
}
