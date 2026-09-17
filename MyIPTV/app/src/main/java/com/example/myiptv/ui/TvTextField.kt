package com.example.myiptv.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation

/** On TV, navigating to an input never starts an IME session; OK explicitly starts editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val television = isAndroidTv()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var keyboardWasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(editing, focused, enabled) {
        if (television && editing && focused && enabled) {
            // Wait for readOnly=false to create the text input session before showing the IME.
            withFrameNanos { }
            keyboard?.show()
        }
        if (!enabled) editing = false
    }
    LaunchedEffect(imeVisible, editing) {
        if (television && editing) {
            if (imeVisible) keyboardWasVisible = true
            else if (keyboardWasVisible) editing = false
        }
        if (!editing) keyboardWasVisible = false
    }

    val tvModifier = if (!television) modifier else modifier
        .onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) {
                if (editing) keyboard?.hide()
                editing = false
            }
        }
        .onPreviewKeyEvent { event ->
            if (!enabled) return@onPreviewKeyEvent false
            val key = event.nativeKeyEvent
            val confirm = key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                key.keyCode == KeyEvent.KEYCODE_ENTER || key.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (confirm && (!editing || !imeVisible)) {
                if (key.action == KeyEvent.ACTION_UP) editing = true
                return@onPreviewKeyEvent true
            }
            if (editing && key.keyCode == KeyEvent.KEYCODE_BACK) {
                if (key.action == KeyEvent.ACTION_UP) {
                    keyboard?.hide()
                    editing = false
                }
                return@onPreviewKeyEvent true
            }
            if (!editing) {
                val direction = when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                    KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                    KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                    KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                    else -> null
                }
                if (direction != null) {
                    if (key.action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(direction)
                    return@onPreviewKeyEvent true
                }
            }
            false
        }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = tvModifier,
        enabled = enabled,
        readOnly = television && !editing,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = colors,
    )
}
