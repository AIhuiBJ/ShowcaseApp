package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** One policy for every timed image replacement. Can be overridden for a whole playback tree. */
data class ImagePlaybackConfig(val loadTimeoutMillis: Long = 30_000L) {
    init { require(loadTimeoutMillis > 0) }
}

val LocalImagePlaybackConfig = staticCompositionLocalOf { ImagePlaybackConfig() }

internal data class ImagePlaybackFrame(
    val key: Any,
    val ready: Boolean,
    val paused: Boolean = false,
    val enabled: Boolean = true,
)

/** Counts loading and successful display separately; all timestamps must be monotonic. */
internal class ImagePlaybackTimer(
    displayDurationMillis: Long,
    private val config: ImagePlaybackConfig,
) {
    private val duration = displayDurationMillis.takeIf { it > 0 } ?: DEFAULT_PERIOD
    private var previous: ImagePlaybackFrame? = null
    private var previousTime = 0L
    private var loadingMillis = 0L
    private var displayMillis = 0L
    private var finished = false
    val progress: Float get() = (displayMillis.toFloat() / duration).coerceIn(0f, 1f)

    fun update(frame: ImagePlaybackFrame, nowMillis: Long): Boolean {
        val old = previous
        val changed = old?.key != frame.key
        val elapsed = if (old == null || changed || old.paused || frame.paused || !old.enabled) 0L
            else (nowMillis - previousTime).coerceAtLeast(0)
        previous = frame
        previousTime = nowMillis
        if (changed || !frame.enabled) {
            loadingMillis = 0
            displayMillis = 0
            finished = false
        }
        if (!frame.ready && old?.ready == true) {
            displayMillis = 0
            loadingMillis = 0
        }
        if (!frame.enabled || frame.paused || finished) return false
        if (frame.ready) {
            // The first success observation starts a fresh, full display interval.
            if (old?.ready == true && !changed) displayMillis += elapsed
            loadingMillis = 0
        } else {
            displayMillis = 0
            if (old?.ready == true) loadingMillis = 0 else loadingMillis += elapsed
        }
        finished = if (frame.ready) displayMillis >= duration else loadingMillis >= config.loadTimeoutMillis
        return finished
    }
}

/** Timers follow actual render state; toggling a progress indicator never changes playback. */
@Composable
internal fun rememberImagePlaybackProgress(
    displayDurationMillis: Long,
    owner: Any = Unit,
    current: () -> ImagePlaybackFrame,
    onAdvance: suspend () -> Unit,
): State<Float> {
    val config = LocalImagePlaybackConfig.current
    val latestCurrent by rememberUpdatedState(current)
    val latestAdvance by rememberUpdatedState(onAdvance)
    val progress = remember { mutableFloatStateOf(0f) }
    var progressKey by remember { mutableStateOf<Any?>(null) }
    PlaybackEffect(owner, displayDurationMillis, config) {
        var timer = ImagePlaybackTimer(displayDurationMillis, config)
        progressKey = null
        progress.floatValue = 0f
        while (isActive) {
            val now = withFrameNanos { it / 1_000_000L }
            val frame = latestCurrent()
            val advance = timer.update(frame, now)
            progressKey = frame.key
            progress.floatValue = timer.progress
            if (advance) {
                try { latestAdvance() }
                catch (cancelled: CancellationException) {
                    // A gesture can cancel the pager's animation without cancelling this
                    // playback effect. Start a fresh interval for the page it settles on.
                    currentCoroutineContext().ensureActive()
                    timer = ImagePlaybackTimer(displayDurationMillis, config)
                    progress.floatValue = 0f
                }
            }
            delay(50)
        }
    }
    // A manual switch can happen between timer ticks. Never expose the previous
    // image's progress while the next frame is waiting for its first timer update.
    return remember {
        derivedStateOf { if (progressKey == latestCurrent().key) progress.floatValue else 0f }
    }
}

@Composable
internal fun rememberPagerImagePlaybackProgress(
    pagerState: PagerState,
    displayDurationMillis: Long,
    itemCount: Int,
    animationMillis: Int,
    stateForPage: (Int) -> MediaItemState,
): State<Float> {
    SkipMismatchedOrientation(pagerState, itemCount, stateForPage)
    return rememberImagePlaybackProgress(displayDurationMillis, owner = pagerState, current = {
        if (itemCount <= 1) ImagePlaybackFrame(Unit, ready = false, enabled = false)
        else {
            val page = pagerState.currentPage
            val media = stateForPage(page)
            ImagePlaybackFrame(page to media, media.ready, paused = pagerState.isScrollInProgress,
                enabled = !media.data.isVideo())
        }
    }) {
        val next = if (pagerState.canScrollForward) pagerState.currentPage + 1 else 0
        pagerState.animateScrollToPage(next, animationSpec = tween(animationMillis))
    }
}

/**
 * Steps over photos whose own orientation disagrees with the device orientation read from the
 * gravity sensor, so a portrait frame only plays portrait photos and vice versa.
 *
 * Only the decoded photo can tell us its orientation, so a mismatched photo is stepped over as
 * soon as it resolves rather than never being fetched at all. The streak is capped at one full
 * cycle: when nothing in the set matches, every photo is played so the frame never goes blank.
 */
@Composable
private fun SkipMismatchedOrientation(
    pagerState: PagerState,
    itemCount: Int,
    stateForPage: (Int) -> MediaItemState,
) {
    val wanted = LocalImageOrientationFilter.current
    if (wanted != null && itemCount > 1) {
        MismatchSkipEffect(pagerState, itemCount, wanted, stateForPage)
    }
}

@Composable
private fun MismatchSkipEffect(
    pagerState: PagerState,
    itemCount: Int,
    wanted: DeviceOrientation,
    stateForPage: (Int) -> MediaItemState,
) {
    val guard = remember(pagerState, wanted, itemCount) { MismatchSkipGuard(itemCount) }
    val page = pagerState.currentPage
    val image = stateForPage(page).displayedImage
    val scrolling = pagerState.isScrollInProgress
    LaunchedEffect(wanted, page, image, scrolling) {
        if (image != null && !scrolling) {
            val actual = photoOrientation(image.width, image.height)
            if (actual == null || actual == wanted) {
                guard.reset()
            } else if (guard.allowSkip()) {
                delay(MISMATCH_SKIP_DELAY_MILLIS)
                if (!pagerState.isScrollInProgress) {
                    val next = if (pagerState.canScrollForward) page + 1 else 0
                    if (next != page) pagerState.scrollToPage(next)
                }
            }
        }
    }
}

private class MismatchSkipGuard(private val itemCount: Int) {
    private var consecutive = 0

    fun reset() {
        consecutive = 0
    }

    fun allowSkip(): Boolean {
        if (consecutive >= itemCount) return false
        consecutive++
        return true
    }
}

private const val MISMATCH_SKIP_DELAY_MILLIS = 250L
