package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alpha.showcase.common.ui.ai.AiSummarySwitch
import com.alpha.showcase.common.ui.ai.AI_IMAGE_SUMMARY_KEY
import com.alpha.showcase.common.ui.play.fold.isDuoFoldAvailable
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.ui.WebStoriesRotateLeft
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.auto_play
import showcaseapp.composeapp.generated.resources.duo_fold_retreat
import showcaseapp.composeapp.generated.resources.display_mode
import showcaseapp.composeapp.generated.resources.interval_time_unit
import showcaseapp.composeapp.generated.resources.orientation
import showcaseapp.composeapp.generated.resources.show_time_progress_indicator
import showcaseapp.composeapp.generated.resources.slide_effect
import showcaseapp.composeapp.generated.resources.sort_rule
import showcaseapp.composeapp.generated.resources.sensor_based_orientation
import showcaseapp.composeapp.generated.resources.screen_off_time
import showcaseapp.composeapp.generated.resources.screen_off_start_time
import showcaseapp.composeapp.generated.resources.screen_off_end_time

internal const val DUO_FOLD_RETREAT_KEY = "DuoFoldRetreat"

/**
 *   - Slide
 *
 *      - Display (Full screen, Fit screen)
 *      - Orientation (Horizontal, Vertical)
 *      - Auto play (Interval)
 *      - Interval time
 *      - Interval time unit
 *      - Show time progress
 *      - Show content meta info
 *      - Sort rule
 *      - Gravity sensor based orientation
 *      - Screen off time settings
 */
@Composable
fun SlideModeView(slideMode: Settings.SlideMode, onSet: (String, Any) -> Unit){
    val duoFoldAvailable = isDuoFoldAvailable()
    val effect = effectiveSlideEffect(slideMode.effect, duoFoldAvailable)

    CheckItem(
        Icons.Outlined.AutoAwesomeMotion,
        effect.toPairWithResString(),
        stringResource(Res.string.slide_effect),
        availableSlideEffects(duoFoldAvailable).map { it.toPairWithResString() },
        onCheck = {
            onSet(SlideEffect.key, it.first)
        }
    )

    CheckItem(
        if (slideMode.displayMode == DisplayMode.FitScreen.value) Icons.Outlined.FitScreen else Icons.Outlined.FullscreenExit,
        DisplayMode.fromValue(slideMode.displayMode).toPairWithResString(),
        stringResource(Res.string.display_mode),
        listOf(DisplayMode.FitScreen.toPairWithResString(), DisplayMode.CenterCrop.toPairWithResString()),
        onCheck = {
            onSet(DisplayMode.key, it.first)
        }
    )

    if (effect == SlideEffect.Default || effect == SlideEffect.Flip) {
        CheckItem(
            if (slideMode.orientation == Orientation.Horizontal.value) Icons.Outlined.WebStories else WebStoriesRotateLeft,
            Orientation.fromValue(slideMode.orientation).toPairWithResString(),
            stringResource(Res.string.orientation),
            listOf(Orientation.Horizontal.toPairWithResString(), Orientation.Vertical.toPairWithResString()),
            onCheck = {
                onSet(Orientation.key, it.first)
            }
        )
    }

    if (effect == SlideEffect.DuoFold) {
        SwitchItem(
            Icons.Outlined.FullscreenExit,
            check = slideMode.duoFoldRetreat,
            desc = stringResource(Res.string.duo_fold_retreat),
            onCheck = { onSet(DUO_FOLD_RETREAT_KEY, it) },
        )
    }

    AiSummarySwitch(slideMode.enableAiImageSummary) { onSet(AI_IMAGE_SUMMARY_KEY, it) }

    SwitchItem(
        Icons.Outlined.ModelTraining,
        check = slideMode.showTimeProgressIndicator,
        desc = stringResource(Res.string.show_time_progress_indicator),
        onCheck = {
            onSet(ShowTimeProgressIndicator.key, it)
        }
    )

    val secondRange = 5f .. 60f
    val minuteRange = 1f .. 15f

    SlideItem(
        Icons.Outlined.Timer,
        desc = stringResource(Res.string.auto_play),
        value = if (slideMode.intervalTime == 0) {
            if (slideMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
        } else if ((slideMode.intervalTimeUnit == 0 && slideMode.intervalTime.toFloat() !in secondRange) || (slideMode.intervalTimeUnit == 1 && slideMode.intervalTime.toFloat() !in minuteRange))
            if (slideMode.intervalTimeUnit == 0) secondRange.start.toInt() else minuteRange.start.toInt()
        else
            slideMode.intervalTime,
        range = if (slideMode.intervalTimeUnit == 0) secondRange else minuteRange,
        step = if (slideMode.intervalTimeUnit == 0) ((secondRange.endInclusive - secondRange.start) / 5 - 1).toInt() else (minuteRange.endInclusive - minuteRange.start - 1).toInt(),
        unit = if (slideMode.intervalTimeUnit == 0) " s" else " m",
        onValueChanged = {
            onSet(AutoPlayDuration.key, it)
        }
    )

    CheckItem(
        Icons.Outlined.HistoryToggleOff,
        IntervalTimeUnit.fromValue(slideMode.intervalTimeUnit).toPairWithResString(),
        stringResource(Res.string.interval_time_unit),
        listOf(IntervalTimeUnit.S.toPairWithResString(), IntervalTimeUnit.M.toPairWithResString()),
        onCheck = {
            onSet(IntervalTimeUnit.key, it.first)
        }
    )

    // Gravity sensor orientation: only photos matching the way the device is held are played.
    SwitchItem(
        Icons.Outlined.PhoneAndroid,
        check = slideMode.sensorBasedOrientation,
        desc = stringResource(Res.string.sensor_based_orientation),
        onCheck = {
            onSet(SENSOR_BASED_ORIENTATION_KEY, it)
        }
    )

    // Screen off time: outside this window nothing is played and the screen is switched off.
    SwitchItem(
        Icons.Outlined.Bedtime,
        check = slideMode.screenOffEnabled,
        desc = stringResource(Res.string.screen_off_time),
        onCheck = {
            onSet(SCREEN_OFF_ENABLED_KEY, it)
        }
    )

    if (slideMode.screenOffEnabled) {
        var editingStart by remember { mutableStateOf<Boolean?>(null) }

        IconItem(
            Icons.Outlined.Schedule,
            stringResource(Res.string.screen_off_start_time),
            onClick = { editingStart = true }
        ) {
            Text(formatTimeOfDay(slideMode.screenOffStartHour, slideMode.screenOffStartMinute))
        }

        IconItem(
            Icons.Outlined.Schedule,
            stringResource(Res.string.screen_off_end_time),
            onClick = { editingStart = false }
        ) {
            Text(formatTimeOfDay(slideMode.screenOffEndHour, slideMode.screenOffEndMinute))
        }

        val editingStartTime = editingStart
        if (editingStartTime != null) {
            TimePickerDialog(
                label = stringResource(
                    if (editingStartTime) Res.string.screen_off_start_time
                    else Res.string.screen_off_end_time
                ),
                visible = true,
                onDismiss = { editingStart = null },
                initialHour = if (editingStartTime) slideMode.screenOffStartHour else slideMode.screenOffEndHour,
                initialMinute = if (editingStartTime) slideMode.screenOffStartMinute else slideMode.screenOffEndMinute,
                onTimeSet = { hour, minute ->
                    val minuteOfDay = hour * MINUTES_PER_HOUR + minute
                    onSet(if (editingStartTime) SCREEN_OFF_START_KEY else SCREEN_OFF_END_KEY, minuteOfDay)
                }
            )
        }
    }
}

internal const val SENSOR_BASED_ORIENTATION_KEY = "SensorBasedOrientation"
internal const val SCREEN_OFF_ENABLED_KEY = "ScreenOffEnabled"
internal const val SCREEN_OFF_START_KEY = "ScreenOffStart"
internal const val SCREEN_OFF_END_KEY = "ScreenOffEnd"
internal const val MINUTES_PER_HOUR = 60

/** `HH:mm` without JVM-only formatting so the label also works on web and native. */
internal fun formatTimeOfDay(hour: Int, minute: Int): String =
    "${hour.coerceIn(0, 23).toString().padStart(2, '0')}:${minute.coerceIn(0, 59).toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    label: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    initialHour: Int,
    initialMinute: Int,
    onTimeSet: (Int, Int) -> Unit
) {
    if (visible) {
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(label) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSet(timePickerState.hour, timePickerState.minute)
                    onDismiss()
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
