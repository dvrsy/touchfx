package app.touchfx.engine

import app.touchfx.model.Scene
import app.touchfx.model.SceneKind
import kotlin.random.Random

/**
 * A running playlist. [order] holds this round's item indices; [pos] is the current one.
 * While running, the current scene ends at [endsAt] (ms on the engine's clock); while paused
 * (a hold-to-play scene is pressed) [pausedLeft] keeps the time that was left.
 */
data class PlaylistState(
    val playlistId: String,
    val order: List<Int>,
    val pos: Int,
    val endsAt: Long,
    val pausedLeft: Long? = null,
) {
    val paused get() = pausedLeft != null
    val itemIndex get() = order[pos]
}

/** A playlist step: the new state, and the scene to switch to. */
data class PlaylistStep(val state: PlaylistState, val sceneId: String)

/** Playlist rules, free of Android and of the real clock so they can be unit tested. */
object PlaylistLogic {

    /**
     * Item indices that can play: their scene still exists and is a normal scene (hold-to-play
     * scenes and other playlists are skipped).
     */
    fun playable(playlist: Scene, scenes: List<Scene>): List<Int> {
        val normal = scenes.filter { it.kind == SceneKind.NORMAL }.mapTo(HashSet()) { it.id }
        return playlist.playlist?.items?.indices?.filter { playlist.playlist.items[it].sceneId in normal } ?: emptyList()
    }

    /** Starts [playlist] at its first scene (or a random one); null if nothing can play. */
    fun start(playlist: Scene, scenes: List<Scene>, now: Long, random: Random = Random.Default): PlaylistStep? {
        val order = roundOrder(playlist, scenes, lastItem = null, random) ?: return null
        return stepAt(playlist, order, 0, now)
    }

    /** Moves on to the next scene; a new round starts (reshuffled if random) after the last one. */
    fun advance(state: PlaylistState, playlist: Scene, scenes: List<Scene>, now: Long, random: Random = Random.Default): PlaylistStep? {
        val valid = playable(playlist, scenes).toSet()
        var pos = state.pos + 1
        // Skip items whose scene was deleted or changed kind meanwhile.
        while (pos < state.order.size && state.order[pos] !in valid) pos++
        if (pos < state.order.size) return stepAt(playlist, state.order, pos, now)
        val order = roundOrder(playlist, scenes, lastItem = state.order.getOrNull(state.pos), random) ?: return null
        return stepAt(playlist, order, 0, now)
    }

    fun due(state: PlaylistState, now: Long) = !state.paused && now >= state.endsAt

    /** Pauses the countdown, e.g. while a hold-to-play scene is pressed. */
    fun pause(state: PlaylistState, now: Long): PlaylistState =
        if (state.paused) state else state.copy(pausedLeft = (state.endsAt - now).coerceAtLeast(0))

    /** Continues with the time that was left when it was paused. */
    fun resume(state: PlaylistState, now: Long): PlaylistState {
        val left = state.pausedLeft ?: return state
        return state.copy(endsAt = now + left, pausedLeft = null)
    }

    /** Milliseconds left of the current scene. */
    fun remaining(state: PlaylistState, now: Long): Long = state.pausedLeft ?: (state.endsAt - now).coerceAtLeast(0)

    private fun stepAt(playlist: Scene, order: List<Int>, pos: Int, now: Long): PlaylistStep {
        val item = playlist.playlist!!.items[order[pos]]
        return PlaylistStep(PlaylistState(playlist.id, order, pos, now + item.seconds * 1000L), item.sceneId)
    }

    /** One round: the playable items in order, or shuffled without repeating the last scene. */
    private fun roundOrder(playlist: Scene, scenes: List<Scene>, lastItem: Int?, random: Random): List<Int>? {
        val items = playable(playlist, scenes)
        if (items.isEmpty()) return null
        if (playlist.playlist?.shuffle != true) return items
        val order = items.shuffled(random).toMutableList()
        val items0 = playlist.playlist.items
        if (order.size > 1 && lastItem != null && items0[order[0]].sceneId == items0.getOrNull(lastItem)?.sceneId) {
            // Don't start the new round with the scene that just played.
            val swapWith = order.indexOfFirst { items0[it].sceneId != items0[lastItem].sceneId }
            if (swapWith > 0) { val t = order[0]; order[0] = order[swapWith]; order[swapWith] = t }
        }
        return order
    }
}
