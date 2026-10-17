package com.alpha.showcase.common.ui.play

/*
 * Apple targets have no gravity-sensor binding in this app: orientation filtering stays inert and
 * the scheduled screen-off window only shows the black playback surface.
 */

internal actual fun readDeviceOrientation(): DeviceOrientation? = null

internal actual fun applyScreenBlackout(enabled: Boolean) = Unit
