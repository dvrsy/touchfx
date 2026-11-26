package app.touchfx

import org.junit.Assert.assertEquals
import org.junit.Test
import app.touchfx.engine.LightKind
import app.touchfx.engine.ViewFilters
import app.touchfx.model.Playlist
import app.touchfx.model.Scene
import app.touchfx.model.SceneKind
import app.touchfx.model.Segment
import app.touchfx.model.VirtualConfig

class ViewFiltersTest {
    private val own = VirtualConfig(id = "f1", name = "WLED-F1", segments = listOf(Segment("f1", 0, 9)), isDevice = "f1")
    private val group = VirtualConfig(id = "g", name = "Panels", segments = listOf(Segment("f1", 0, 9)))

    @Test
    fun lightsFilterShowsLightsGroupsOrBoth() {
        val all = listOf(own, group)
        assertEquals(listOf(own, group), ViewFilters.lights(all, setOf(LightKind.LIGHT, LightKind.GROUP)))
        assertEquals(listOf(own), ViewFilters.lights(all, setOf(LightKind.LIGHT)))
        assertEquals(listOf(group), ViewFilters.lights(all, setOf(LightKind.GROUP)))
    }

    @Test
    fun scenesFilterByKindKeepsTheOrder() {
        val normal = Scene("n", "Chill", emptyMap())
        val hold = Scene("h", "Strobe", emptyMap(), momentary = true)
        val list = Scene("p", "Party", emptyMap(), playlist = Playlist(emptyList()))
        val all = listOf(list, normal, hold)
        assertEquals(listOf(list, hold), ViewFilters.scenes(all, setOf(SceneKind.PLAYLIST, SceneKind.HOLD)))
        assertEquals(listOf(normal), ViewFilters.scenes(all, setOf(SceneKind.NORMAL)))
    }

    @Test
    fun theLastSelectedKindCantBeSwitchedOff() {
        val both = setOf(LightKind.LIGHT, LightKind.GROUP)
        assertEquals(setOf(LightKind.GROUP), ViewFilters.toggle(both, LightKind.LIGHT))
        assertEquals(setOf(LightKind.GROUP), ViewFilters.toggle(setOf(LightKind.GROUP), LightKind.GROUP))
        assertEquals(both, ViewFilters.toggle(setOf(LightKind.GROUP), LightKind.LIGHT))
    }
}
