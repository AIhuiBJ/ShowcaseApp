package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay

/** Coarse physical orientation of the device, as reported by its gravity sensor. */
enum class DeviceOrientation { Portrait, Landscape }

/**
 * Orientation read from the platform gravity sensor (accelerometer as a fallback).
 *
 * Returns null where the device has no such sensor — desktop, web and tvOS. Callers treat
 * null as "do not filter", so the photo filter setting is simply inert there instead of
 * silently hiding photos based on a guess.
 */
internal expect fun readDeviceOrientation(): DeviceOrientation?

/**
 * Blank the panel for a scheduled screen-off window.
 *
 * Android drops the display brightness to zero and releases the keep-screen-on flag so the
 * system puts the panel to sleep; other platforms have nothing to switch and only show the
 * black playback surface.
 */
internal expect fun applyScreenBlackout(enabled: Boolean)

/** Null disables orientation filtering; the playback screen sets it when the setting is on. */
internal val LocalImageOrientationFilter = staticCompositionLocalOf<DeviceOrientation?> { null }

/**
 * Polls the sensor instead of subscribing to it so rotation is picked up while the frame
 * keeps playing, without every pager owning a sensor listener.
 */
@Composable
internal fun rememberDeviceOrientation(pollMillis: Long = DEVICE_ORIENTATION_POLL_MILLIS): DeviceOrientation? {
    var orientation by remember { mutableStateOf(readDeviceOrientation()) }
    LaunchedEffect(pollMillis) {
        while (true) {
            orientation = readDeviceOrientation()
            delay(pollMillis)
        }
    }
    return orientation
}

/**
 * Orientation of a decoded photo. Square images carry no orientation, so they are never
 * filtered out — filtering that hid them would drop legitimate content.
 */
internal fun photoOrientation(width: Int, height: Int): DeviceOrientation? = when {
    width <= 0 || height <= 0 -> null
    width > height -> DeviceOrientation.Landscape
    height > width -> DeviceOrientation.Portrait
    else -> null
}

private const val DEVICE_ORIENTATION_POLL_MILLIS = 500L
