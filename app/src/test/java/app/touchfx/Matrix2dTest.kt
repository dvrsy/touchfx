package app.touchfx

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.audio.AudioAnalyzer
import app.touchfx.effects.Effect2D
import app.touchfx.effects.EffectCategory
import app.touchfx.effects.Effects
import app.touchfx.effects.Palettes
import app.touchfx.effects.ParamValues
import app.touchfx.effects.PixelFont
import app.touchfx.effects.RenderContext
import app.touchfx.effects.twodParams
import app.touchfx.engine.PixelMapper
import app.touchfx.model.PanelLayout
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig
import kotlin.math.PI
import kotlin.math.sin

class Matrix2dTest {
    /** Draws each pixel's row-major index + 1, so outputs show where every pixel went. */
    private class Coords : Effect2D() {
        override fun draw(ctx: RenderContext, img: IntArray, w: Int, h: Int) {
            for (i in img.indices) img[i] = i + 1
        }
    }

    private fun render(size: Int, rows: Int, settings: Map<String, Any> = emptyMap(), matrixRotate: Int = 0): IntArray {
        val ctx = RenderContext().apply {
            p = ParamValues(twodParams(), settings)
            this.rows = rows
            this.matrixRotate = matrixRotate
        }
        return IntArray(size).also { Coords().render(ctx, it) }
    }

    // A 3-wide, 2-tall matrix. Unrotated the effect draws:  1 2 3 / 4 5 6

    @Test
    fun noRotationIsRowMajor() = assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), render(6, 2))

    @Test
    fun rotate90MatchesPilRotate90() {
        // Drawn 2 wide × 3 tall (1 2 / 3 4 / 5 6), then turned counter-clockwise.
        assertArrayEquals(intArrayOf(2, 4, 6, 1, 3, 5), render(6, 2, mapOf("rotate" to 1)))
    }

    @Test
    fun rotate180() = assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), render(6, 2, mapOf("rotate" to 2)))

    @Test
    fun rotate270MatchesPilRotate270() {
        assertArrayEquals(intArrayOf(5, 3, 1, 6, 4, 2), render(6, 2, mapOf("rotate" to 3)))
    }

    @Test
    fun flips() {
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), render(6, 2, mapOf("flip_horizontal" to true)))
        assertArrayEquals(intArrayOf(4, 5, 6, 1, 2, 3), render(6, 2, mapOf("flip_vertical" to true)))
    }

    @Test
    fun lightRotationAddsToEffectRotation() {
        assertArrayEquals(render(6, 2, mapOf("rotate" to 2)), render(6, 2, mapOf("rotate" to 1), matrixRotate = 1))
        assertArrayEquals(render(6, 2), render(6, 2, mapOf("rotate" to 3), matrixRotate = 1))
    }

    @Test
    fun pixelsAfterTheLastFullRowStayBlack() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 0), render(7, 2))
    }

    @Test
    fun serpentineReversesEverySecondRow() {
        val src = IntArray(9) { it + 1 }
        val dst = IntArray(9)
        PixelMapper.wire(src, 3, serpentine = true, dst = dst)
        assertArrayEquals(intArrayOf(1, 2, 3, 6, 5, 4, 7, 8, 9), dst)
        PixelMapper.wire(src, 3, serpentine = false, dst = dst)
        assertArrayEquals(src, dst)
    }

    @Test
    fun matrixTestPatternShowsOrientation() {
        val info = Effects.info("matrix_test")
        val ctx = RenderContext().apply { p = ParamValues(info.params); rows = 8 }
        val out = IntArray(64).also { info.create().render(ctx, it) }
        fun at(x: Int, y: Int) = out[y * 8 + x]
        assertEquals(0xFFFFFF, at(3, 0))   // frame
        assertEquals(0xFF0000, at(0, 0))   // lines start in the corners, over the frame (as in LedFx)
        assertEquals(0xFF0000, at(1, 1))   // red from top-left
        assertEquals(0x0000FF, at(6, 1))   // blue from top-right
        assertEquals(0x00FF00, at(1, 6))   // green from bottom-left
        assertEquals(0xFFFFFF, at(6, 6))   // white from bottom-right
    }

    @Test
    fun everyMatrixEffectRendersAtAnySize() {
        val analyzer = AudioAnalyzer()
        val hop = AudioAnalyzer.SAMPLE_RATE / 60
        val buf = FloatArray(hop)
        val matrix = Effects.all.filter { it.category == EffectCategory.MATRIX }
        assertTrue(matrix.size >= 2)
        for (info in matrix) {
            for ((w, h) in listOf(1 to 1, 8 to 8, 32 to 16, 5 to 1)) {
                val effect = info.create()
                val ctx = RenderContext().apply {
                    p = ParamValues(info.params); rows = h; palette = Palettes.get("plasma")
                }
                val out = IntArray(w * h)
                for (k in 0 until 30) {
                    for (i in 0 until hop) buf[i] = 0.3f * sin(2 * PI * 80 * (k * hop + i) / AudioAnalyzer.SAMPLE_RATE).toFloat()
                    analyzer.push(buf, hop)
                    ctx.audio = analyzer.analyze(k * 16_666_667L)
                    ctx.time = k / 60.0
                    effect.render(ctx, out)
                    assertTrue("${info.id} ${w}x$h invalid colour", out.all { it in 0..0xFFFFFF })
                }
                // Tiny matrices can legitimately be dark (e.g. scrolling text between letters).
                if (w * h >= 64) assertTrue("${info.id} ${w}x$h stayed black", out.any { it != 0 })
            }
        }
    }

    @Test
    fun matrixSettingsSurviveJson() {
        val v = VirtualConfig(id = "m", name = "Panel", segments = listOf(Segment("d", 0, 255)), rows = 16, serpentine = true, rotate = 3)
        assertEquals(v, VirtualConfig.fromJson(JSONObject(v.toJson().toString())))
    }

    // ---------------------------------------------------------------- Texter + pixel font

    @Test
    fun pixelFontCoversAsciiAndGermanCharacters() {
        for (c in ' '..'~') assertTrue("missing '$c'", PixelFont.covers(c))
        for (c in "ÄÖÜäöüß€°") assertTrue("missing '$c'", PixelFont.covers(c))
        // H is 5 wide, I is 3 wide, with a 1-column gap.
        assertEquals(9, PixelFont.width("HI"))
        assertEquals(5 + 1 + PixelFont.SPACE_WIDTH + 1 + 3, PixelFont.width("H I"))
    }

    private fun texter(text: String, settings: Map<String, Any> = emptyMap()): Pair<RenderContext, Effect2D> {
        val info = Effects.info("texter2d")
        val ctx = RenderContext().apply {
            p = ParamValues(info.params, mapOf("text" to text) + settings)
            rows = 8
            dt = 1f / 60f
            palette = Palettes.get("rainbow")
        }
        return ctx to (info.create() as Effect2D)
    }

    /** Leftmost lit column of a 32x8 frame, or -1. */
    private fun leftmost(out: IntArray) = (0 until 32).firstOrNull { x -> (0 until 8).any { y -> out[y * 32 + x] != 0 } } ?: -1

    @Test
    fun texterCentresTextWhenSpeedIsZero() {
        val (ctx, fx) = texter("HI", mapOf("speed_option_1" to 0f))
        val out = IntArray(32 * 8).also { fx.render(ctx, it) }
        assertEquals((32 - 9) / 2, leftmost(out))
        assertTrue(out.all { it == 0 || it == 0xFFFFFF })
    }

    @Test
    fun texterScrollsHalfTheWidthPerSecondAndStartsAgain() {
        val (ctx, fx) = texter("HI")
        val out = IntArray(32 * 8)
        fx.render(ctx, out)
        assertTrue("enters from the right", leftmost(out) >= 30)
        repeat(59) { fx.render(ctx, out) }
        // Speed 1 = half the width (16 px) per second, as in LedFx.
        assertTrue("after 1 s at ${leftmost(out)}", leftmost(out) in 15..16)
        repeat(100) { fx.render(ctx, out) }      // 2.67 s: fully gone (needs 32 + 9 px)
        fx.render(ctx, out)
        assertTrue("starts again on the right, at ${leftmost(out)}", leftmost(out) >= 28)
    }

    @Test
    fun texterUsesTheGradientPerWordWhenAsked() {
        val (ctx, fx) = texter("A B", mapOf("speed_option_1" to 0f, "use_gradient" to true))
        val out = IntArray(32 * 8).also { fx.render(ctx, it) }
        val colours = out.filter { it != 0 }.toSet()
        assertEquals("two words, two colours", 2, colours.size)
    }

    // ---------------------------------------------------------------- panels in matrix groups

    private fun panelGroup(vararg segs: Segment, rows: Int, layout: PanelLayout = PanelLayout.SIDE_BY_SIDE, serpentine: Boolean = false) =
        VirtualConfig(id = "g", name = "Panels", segments = segs.toList(), rows = rows, serpentine = serpentine, panelLayout = layout)

    @Test
    fun twoSixteenByEightPanelsSideBySideFormOneThirtyTwoByEightImage() {
        // The reported setup: WLED-F1 and WLED-F2, 128 LEDs each, 8 rows, spanned in one group.
        val v = panelGroup(Segment("f1", 0, 127), Segment("f2", 0, 127), rows = 8)
        assertTrue(PixelMapper.usesPanels(v))
        val image = IntArray(256) { it + 1 }                 // 32 x 8, row-major
        val bufs = mapOf("f1" to IntArray(128), "f2" to IntArray(128))
        PixelMapper.mapPanels(v, image, bufs)
        for (y in 0 until 8) for (x in 0 until 16) {
            assertEquals("f1 ($x,$y)", image[y * 32 + x], bufs["f1"]!![y * 16 + x])
            assertEquals("f2 ($x,$y)", image[y * 32 + 16 + x], bufs["f2"]!![y * 16 + x])
        }
    }

    @Test
    fun panelSerpentineAndInvertApplyPerPanel() {
        val image = IntArray(16) { it + 1 }                  // 8 x 2: two 4 x 2 panels
        val bufs = mapOf("a" to IntArray(8), "b" to IntArray(8))
        PixelMapper.mapPanels(panelGroup(Segment("a", 0, 7), Segment("b", 0, 7, invert = true), rows = 2, serpentine = true), image, bufs)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 12, 11, 10, 9), bufs["a"])
        // Panel b holds 5 6 7 8 / 13 14 15 16; serpentine gives 5 6 7 8 16 15 14 13; turned 180°:
        assertArrayEquals(intArrayOf(13, 14, 15, 16, 8, 7, 6, 5), bufs["b"])
    }

    @Test
    fun aSinglePanelMapsLikeTheWholeMatrix() {
        val image = IntArray(12) { it + 1 }                  // 4 x 3
        for (serp in listOf(false, true)) {
            val v = panelGroup(Segment("a", 0, 11), rows = 3, serpentine = serp)
            val viaPanels = IntArray(12).also { PixelMapper.mapPanels(v, image, mapOf("a" to it)) }
            val wired = IntArray(12).also { PixelMapper.wire(image, 3, serp, it) }
            val viaStrip = IntArray(12).also { PixelMapper.map(v, wired, mapOf("a" to it)) }
            assertArrayEquals("serpentine=$serp", viaStrip, viaPanels)
        }
    }

    @Test
    fun stackedPanelsKeepTheLinearMapping() {
        val v = panelGroup(Segment("a", 0, 7), Segment("b", 0, 7), rows = 4, layout = PanelLayout.STACKED)
        assertFalse(PixelMapper.usesPanels(v))
        val bufs = mapOf("a" to IntArray(8), "b" to IntArray(8))
        PixelMapper.map(v, IntArray(16) { it + 1 }, bufs)
        assertArrayEquals(IntArray(8) { it + 1 }, bufs["a"])
        assertArrayEquals(IntArray(8) { it + 9 }, bufs["b"])
    }

    @Test
    fun panelLayoutDefaultsToSideBySideWhenMissing() {
        val o = VirtualConfig(id = "m", name = "P", segments = listOf(Segment("d", 0, 15)), rows = 4).toJson()
        o.remove("panel_layout")
        assertEquals(PanelLayout.SIDE_BY_SIDE, VirtualConfig.fromJson(o).panelLayout)
        val stacked = VirtualConfig(id = "m", name = "P", segments = listOf(Segment("d", 0, 15)), rows = 4, panelLayout = PanelLayout.STACKED)
        assertEquals(stacked, VirtualConfig.fromJson(JSONObject(stacked.toJson().toString())))
    }
}
