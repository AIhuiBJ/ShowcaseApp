package com.alpha.showcase.common.ui.play

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import kotlin.math.abs
import AndroidApp
import currentActivity

/**
 * One process-wide gravity listener shared by every caller.
 *
 * The photo filter only needs the coarse portrait/landscape split, so a normal-rate listener is
 * plenty and it keeps running while the frame is visible — no re-registration per screen.
 */
private object GravityOrientationSource : SensorEventListener {

    @Volatile
    private var current: DeviceOrientation? = null

    private var registeredManager: SensorManager? = null

    fun orientation(): DeviceOrientation? {
        ensureRegistered()
        return current
    }

    private fun ensureRegistered() {
        if (registeredManager != null) return
        val manager = runCatching {
            AndroidApp.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }.getOrNull() ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        if (!manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) return
        registeredManager = manager
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        // Gravity is measured in device coordinates: the dominant in-plane axis says which way
        // up the panel is being held. Below the threshold the device is flat on a table and the
        // last reading is the best answer.
        if (abs(x) > TILT_AXIS_THRESHOLD || abs(y) > TILT_AXIS_THRESHOLD) {
            current = if (abs(x) > abs(y)) DeviceOrientation.Landscape else DeviceOrientation.Portrait
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

internal actual fun readDeviceOrientation(): DeviceOrientation? = GravityOrientationSource.orientation()

internal actual fun applyScreenBlackout(enabled: Boolean) {
    val activity = currentActivity ?: return
    activity.runOnUiThread {
        val window = activity.window
        val attributes = window.attributes
        if (enabled) {
            // Zero brightness blanks the panel; releasing keep-screen-on is what actually lets the
            // system suspend it, so the frame is dark *and* asleep inside the window.
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            attributes.screenBrightness = 0f
        } else {
            attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attributes
    }
}

private const val TILT_AXIS_THRESHOLD = 1.5f
