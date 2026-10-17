package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alpha.showcase.common.ui.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Whether the scheduled screen-off window covers [minuteOfDay].
 *
 * The window runs [startMinuteOfDay, endMinuteOfDay) and wraps past midnight, so 23:00 → 07:00
 * is off at 02:00 and on at 12:00. A window whose ends are equal is empty (the setting is
 * effectively disabled) rather than a full day.
 */
internal fun isScreenOffActive(minuteOfDay: Int, startMinuteOfDay: Int, endMinuteOfDay: Int): Boolean {
    if (startMinuteOfDay == endMinuteOfDay) return false
    return if (startMinuteOfDay < endMinuteOfDay) {
        minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
    } else {
        minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
    }
}

internal fun Settings.SlideMode.screenOffStartMinuteOfDay(): Int = screenOffStartHour * 60 + screenOffStartMinute

internal fun Settings.SlideMode.screenOffEndMinuteOfDay(): Int = screenOffEndHour * 60 + screenOffEndMinute

/**
 * Tracks the screen-off window while playback runs. Photos are not played and the panel goes
 * dark inside the window; both resume once it ends.
 */
@Composable
internal fun rememberScreenOffActive(slideMode: Settings.SlideMode): Boolean {
    val enabled = slideMode.screenOffEnabled
    val start = slideMode.screenOffStartMinuteOfDay()
    val end = slideMode.screenOffEndMinuteOfDay()
    var active by remember(enabled, start, end) {
        mutableStateOf(enabled && isScreenOffActive(nowMinuteOfDay(), start, end))
    }
    LaunchedEffect(enabled, start, end) {
        while (enabled) {
            active = isScreenOffActive(nowMinuteOfDay(), start, end)
            delay(SCREEN_OFF_POLL_MILLIS)
        }
        active = false
    }
    return active
}

private fun nowMinuteOfDay(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return now.hour * 60 + now.minute
}

private const val SCREEN_OFF_POLL_MILLIS = 15_000L
