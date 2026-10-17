package com.alpha.showcase.common.ui.play

import coil3.Image
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of scanning the pages after the current one; [page] is null when none may be played. */
internal data class PlayableProbe(val page: Int?, val rejections: Int)

/**
 * Decides whether an upcoming photo may be played, before the pager shows it.
 *
 * Playback must never display a photo that contradicts the way the device is held, so the
 * decision cannot wait for the pager to settle on a page: the next photos are read ahead here,
 * their orientation is compared with the gravity-sensor orientation, and a mismatch is hidden and
 * stepped over instead of being drawn for a frame and then swapped out.
 *
 * Only [LOOKAHEAD_PAGES] pages are probed. When a whole cycle disagrees with the sensor the
 * filter stands down, so a set holding nothing but the other orientation keeps playing rather
 * than freezing on a blank frame.
 */
internal class OrientationPlaybackGate(
    private val wanted: DeviceOrientation,
    private val itemCount: Int,
    private val dataAt: (Int) -> Any,
    private val stateAt: (Int) -> MediaItemState,
    private val decode: suspend (Any) -> Image?,
) {
    private val decisions = mutableMapOf<Any, Boolean>()
    private val hiddenStates = mutableSetOf<MediaItemState>()
    private var rejectedStreak = 0
    private var filtering = true

    /** Reads the photos ahead of the switch so their decision already exists when it happens. */
    suspend fun preroll(from: Int) {
        if (!filtering) return
        var page = nextPageInCycle(from, itemCount)
        repeat((minOf(LOOKAHEAD_PAGES, itemCount - 1)).coerceAtLeast(0)) {
            if (pageKeyOf(page)?.let(decisions::get) == null) decide(page)
            page = nextPageInCycle(page, itemCount)
        }
    }

    /**
     * Blanks the pages whose photo has not been judged yet, so the pager cannot draw one before
     * the sensor has agreed with it. Called from composition, before the pager composes its
     * pages — that ordering is what keeps the very first photo of a session from flashing.
     */
    fun hideUndecided(from: Int) {
        if (!filtering) return
        var page = from
        repeat((minOf(LOOKAHEAD_PAGES + 1, itemCount)).coerceAtLeast(0)) {
            val state = stateAt(page)
            val key = keyOf(state.data)
            if (key != null && decisions[key] == null && !state.hidden) state.hidden = true
            page = nextPageInCycle(page, itemCount)
        }
    }

    /**
     * Page playback should switch to, or null for "play the immediate next page" — used when the
     * filter is off, when nothing within the lookahead matches, or while a decision is pending.
     */
    suspend fun nextPlayablePage(from: Int): Int? {
        if (!filtering || itemCount <= 1) return null
        // The photo right after this one is waited for; the ones behind it use what preroll
        // already decided, so a slow source cannot hold up the switch.
        decide(nextPageInCycle(from, itemCount))
        val probe = firstPlayablePage(from, itemCount, LOOKAHEAD_PAGES) { page ->
            pageKeyOf(page)?.let(decisions::get)
        }
        if (probe.page != null) {
            rejectedStreak = 0
            return probe.page
        }
        rejectedStreak += probe.rejections
        if (rejectedStreak >= itemCount) standDown()
        return null
    }

    /**
     * Whether the page may be played, or null when it cannot be judged yet (its photo is not in
     * the pager's window). A rejection also hides the page before it is ever drawn.
     */
    suspend fun decide(page: Int): Boolean? {
        val state = stateAt(page)
        if (!filtering) {
            state.hidden = false
            return true
        }
        val key = pageKeyOf(page) ?: return null
        val accepted = decisions[key] ?: resolve(key, page)
        state.hidden = !accepted
        if (accepted) hiddenStates.remove(state) else hiddenStates.add(state)
        return accepted
    }

    private suspend fun resolve(key: Any, page: Int): Boolean {
        val image = try {
            withTimeoutOrNull(DECISION_TIMEOUT_MILLIS) { decode(dataAt(page)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        val orientation = image?.let { photoOrientation(it.width, it.height) }
        // An unknown or square photo is never filtered out: only a clear disagreement is skipped.
        val accepted = orientation == null || orientation == wanted
        decisions[key] = accepted
        return accepted
    }

    /** Nothing in the set agrees with the sensor: show everything rather than stay blank. */
    private fun standDown() {
        filtering = false
        rejectedStreak = 0
        hiddenStates.forEach { it.hidden = false }
        hiddenStates.clear()
    }

    /** Null for a photo that is not loaded yet — an unloaded page must not share a key with siblings. */
    private fun keyOf(data: Any): Any? =
        if (data === EMPTY_PLACEHOLDER) null else stableMediaKey(data)

    private fun pageKeyOf(page: Int): Any? = keyOf(dataAt(page))
}

/**
 * First page after [from] that [decision] accepts, wrapping through the whole cycle.
 *
 * An undecided page stops the scan instead of guessing: the caller keeps playing sequentially and
 * the page is judged when it settles. [PlayableProbe.rejections] counts the pages scanned whose
 * decision was negative, which is how a set with no usable photo is recognised.
 */
internal fun firstPlayablePage(
    from: Int,
    itemCount: Int,
    lookahead: Int,
    decision: (Int) -> Boolean?,
): PlayableProbe {
    if (itemCount <= 1 || lookahead <= 0) return PlayableProbe(null, 0)
    var page = nextPageInCycle(from, itemCount)
    var rejections = 0
    repeat((minOf(lookahead, itemCount - 1)).coerceAtLeast(0)) {
        when (decision(page)) {
            true -> return PlayableProbe(page, rejections)
            false -> rejections++
            null -> return PlayableProbe(null, rejections)
        }
        page = nextPageInCycle(page, itemCount)
    }
    return PlayableProbe(null, rejections)
}

/** Next page in the playback cycle; the pager wraps to the first photo after the last one. */
internal fun nextPageInCycle(page: Int, itemCount: Int): Int =
    if (itemCount <= 0) 0 else (page + 1) % itemCount

internal const val LOOKAHEAD_PAGES = 3
private const val DECISION_TIMEOUT_MILLIS = 4_000L
