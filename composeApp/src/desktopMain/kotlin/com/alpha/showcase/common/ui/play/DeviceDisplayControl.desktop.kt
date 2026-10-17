package com.alpha.showcase.common.ui.play

/*
 * Desktop has no gravity sensor: orientation filtering stays inert and the scheduled screen-off
 * window only shows the black playback surface.
 */

internal actual fun readDeviceOrientation(): DeviceOrientation? = null

internal actual fun applyScreenBlackout(enabled: Boolean) = Unit
