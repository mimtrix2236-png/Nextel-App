package com.nextelapp

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

fun Modifier.pointerInputPtt(onPress: () -> Unit, onRelease: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures(onPress = {
            onPress()
            tryAwaitRelease()
            onRelease()
        })
    }
