package app.touchfx

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.touchfx.engine.AppState
import app.touchfx.engine.Backup
import app.touchfx.engine.PlaylistLogic
import app.touchfx.engine.PlaylistState
import app.touchfx.model.Playlist
import app.touchfx.model.PlaylistItem
import app.touchfx.model.Scene
import app.touchfx.model.SceneAction
import app.touchfx.model.SceneEntry
import app.touchfx.model.SceneKind
import kotlin.random.Random

class PlaylistTest {
    private fun look(id: String) = Scene(id, id.uppercase(), mapOf("l" to SceneEntry(SceneAction.ACTIVATE, effectId = "rainbow")))
    private val a = look("a"); private val b = look("b"); private val c = look("c")
    private val strobe = look("strobe").copy(momentary = true)

    private fun playlist(vararg items: Pair<String, Int>, shuffle: Boolean = false) =
        Scene("p", "Party", emptyMap(), playlist = Playlist(items.map { PlaylistItem(it.first, it.second) }, shuffle))

    /** Scene ids played, following the timing: each step starts when the previous one is due. */
    private fun play(pl: Scene, scenes: List<Scene>, steps: Int, random: Random = Random(1)): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        var step = PlaylistLogic.start(pl, scenes, 0, random) ?: return out
        out += step.sceneId to 0L
        repeat(steps - 1) {
            val t = step.state.endsAt
            assertFalse(PlaylistLogic.due(step.state, t - 1))
            assertTrue(PlaylistLogic.due(step.state, t))
            step = PlaylistLogic.advance(step.state, pl, scenes, t, random) ?: return out
            out += step.sceneId to t
        }
        return out
    }

    @Test
    fun inOrderEachSceneRunsForItsTimeThenTheNextStarts() {
        val pl = playlist("a" to 10, "b" to 5, "c" to 20)
        val played = play(pl, listOf(a, b, c, pl), 5)
        assertEquals(listOf("a" to 0L, "b" to 10_000L, "c" to 15_000L, "a" to 35_000L, "b" to 45_000L), played)
    }

    @Test
    fun randomPlaysEverySceneOncePerRoundAndNeverTwiceInARow() {
        val pl = playlist("a" to 1, "b" to 1, "c" to 1, shuffle = true)
        val ids = play(pl, listOf(a, b, c, pl), 300, Random(42)).map { it.first }
        ids.chunked(3).forEach { round -> assertEquals(setOf("a", "b", "c"), round.toSet()) }
        ids.zipWithNext().forEach { (x, y) -> assertNotEquals("same scene twice in a row", x, y) }
        assertTrue("actually shuffled", ids.chunked(3).map { it.joinToString() }.toSet().size > 1)
    }

    @Test
    fun aHoldPausesThePlaylistAndItCarriesOnWithTheTimeLeft() {
        val pl = playlist("a" to 10, "b" to 10)
        val start = PlaylistLogic.start(pl, listOf(a, b, pl), 0)!!
        val paused = PlaylistLogic.pause(start.state, 4_000)          // hold pressed after 4 s
        assertTrue(paused.paused)
        assertEquals(6_000L, PlaylistLogic.remaining(paused, 99_999))
        assertFalse("doesn't move on while held", PlaylistLogic.due(paused, 1_000_000))
        val resumed = PlaylistLogic.resume(paused, 50_000)            // released much later
        assertEquals(56_000L, resumed.endsAt)
        assertEquals("still on the same scene", start.state.itemIndex, resumed.itemIndex)
        assertEquals(resumed, PlaylistLogic.resume(resumed, 60_000))  // resuming twice is harmless
    }

    @Test
    fun deletedAndHoldToPlayScenesAreSkipped() {
        val pl = playlist("a" to 1, "gone" to 1, "strobe" to 1, "c" to 1)
        assertEquals(listOf("a", "c", "a", "c"), play(pl, listOf(a, c, strobe, pl), 4).map { it.first })
        // A scene deleted while the playlist runs is skipped from then on.
        val step = PlaylistLogic.start(playlist("a" to 1, "b" to 1, "c" to 1), listOf(a, b, c), 0)!!
        val next = PlaylistLogic.advance(step.state, playlist("a" to 1, "b" to 1, "c" to 1), listOf(a, c), 1_000)!!
        assertEquals("c", next.sceneId)
    }

    @Test
    fun aPlaylistWithNothingPlayableStops() {
        assertNull(PlaylistLogic.start(playlist("gone" to 5, "strobe" to 5), listOf(strobe), 0))
        val pl = playlist("a" to 5)
        val step = PlaylistLogic.start(pl, listOf(a, pl), 0)!!
        assertNull(PlaylistLogic.advance(step.state, pl, listOf(pl), 5_000))
    }

    @Test
    fun playlistsAreSavedAndBackedUp() {
        val pl = playlist("a" to 10, "b" to 25, shuffle = true)
        val back = Scene.fromJson(JSONObject(pl.toJson().toString()))
        assertEquals(SceneKind.PLAYLIST, back.kind)
        assertEquals(pl.playlist, back.playlist)
        assertEquals(SceneKind.NORMAL, Scene.fromJson(a.toJson()).kind)
        assertEquals(SceneKind.HOLD, Scene.fromJson(strobe.toJson()).kind)
        val state = AppState(devices = emptyList(), virtuals = emptyList(), scenes = listOf(a, b, pl), presets = emptyList())
        val restored = Backup.decode(Backup.encode(state, 2, "0.5.2", "x").toString())
        assertEquals(pl.playlist, restored.scenes.first { it.id == "p" }.playlist)
    }

    @Test
    fun stateIndexPointsAtTheCurrentItem() {
        val st = PlaylistState("p", listOf(2, 0, 1), pos = 1, endsAt = 0)
        assertEquals(0, st.itemIndex)
    }
}
