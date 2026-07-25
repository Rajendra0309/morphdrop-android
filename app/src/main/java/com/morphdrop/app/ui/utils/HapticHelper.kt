package com.morphdrop.app.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class HapticHelper(private val view: View) {
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun selection() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberHapticHelper(): HapticHelper {
    val view = LocalView.current
    return remember(view) { HapticHelper(view) }
}
