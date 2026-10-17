package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.SuccessResult
import coil3.size.Scale
import com.alpha.showcase.common.ui.ext.buildImageRequest
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
    val wanted = LocalImageOrientationFilter.current
    val gate = if (wanted != null && itemCount > 1) {
        rememberOrientationPlaybackGate(wanted, itemCount, pagerState, stateForPage)
    } else {
        null
    }
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
        val playable = gate?.nextPlayablePage(pagerState.currentPage)
        if (playable != null && playable != next) {
            // Jump instead of scrolling: an animated pass would render the skipped photos.
            pagerState.scrollToPage(playable)
        } else {
            pagerState.animateScrollToPage(next, animationSpec = tween(animationMillis))
        }
    }
}

/**
 * Reads the photos ahead of the switch and hides the ones that disagree with the gravity sensor,
 * so the pager never draws a photo it is about to step over.
 */
@Composable
private fun rememberOrientationPlaybackGate(
    wanted: DeviceOrientation,
    itemCount: Int,
    pagerState: PagerState,
    stateForPage: (Int) -> MediaItemState,
): OrientationPlaybackGate {
    val context = LocalPlatformContext.current
    val imageLoader = LocalImageLoader.current ?: coil3.SingletonImageLoader.get(context)
    val currentPage = pagerState.currentPage
    val gate = remember(wanted, itemCount, imageLoader, context) {
        OrientationPlaybackGate(
            wanted = wanted,
            itemCount = itemCount,
            dataAt = { page -> stateForPage(page).data },
            stateAt = stateForPage,
        ) { data -> probePhoto(imageLoader, context, data) }
    }
    // Read ahead of the timer so the decision exists by the time playback switches pages.
    LaunchedEffect(gate, currentPage) { gate.preroll(currentPage) }
    // Before the pager composes its pages: nothing undecided may be drawn, not even once.
    gate.hideUndecided(currentPage)
    // Manual swipes, a first photo that disagrees, or a slow source: hide it, then step over it.
    LaunchedEffect(gate, pagerState) {
        snapshotFlow { if (pagerState.isScrollInProgress) NO_SETTLED_PAGE else pagerState.currentPage }
            .collect { page ->
                if (page != NO_SETTLED_PAGE && gate.decide(page) == false) {
                    // Nothing judged playable yet: move one page on rather than sit on a blank one.
                    val target = gate.nextPlayablePage(page) ?: nextPageInCycle(page, itemCount)
                    if (target != page) pagerState.scrollToPage(target)
                }
            }
    }
    return gate
}

/**
 * Decodes a small copy of a photo just far enough to learn its aspect. The probe keeps the memory
 * cache clean — the pager's own request stays the one that fills it, at display size.
 */
private suspend fun probePhoto(
    imageLoader: ImageLoader,
    context: PlatformContext,
    data: Any,
): Image? {
    val request = buildImageRequest(context, data).newBuilder()
        .memoryCachePolicy(CachePolicy.DISABLED)
        .size(PROBE_PHOTO_SIZE, PROBE_PHOTO_SIZE)
        .scale(Scale.FIT)
        .build()
    return (imageLoader.execute(request) as? SuccessResult)?.image
}

private const val PROBE_PHOTO_SIZE = 480
private const val NO_SETTLED_PAGE = -1
