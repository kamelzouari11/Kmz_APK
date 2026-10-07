package com.example.myiptv.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myiptv.ui.theme.MyIptvPalette

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
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MyIptvPalette.TextFieldBackground,
        unfocusedContainerColor = MyIptvPalette.TextFieldBackground,
        disabledContainerColor = MyIptvPalette.TextFieldBackground,
    ),
) {
    val television = isAndroidTv()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val clearFocusRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var clearFocused by remember { mutableStateOf(false) }
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
            if (confirm && clearFocused) {
                if (key.action == KeyEvent.ACTION_UP) onValueChange("")
                return@onPreviewKeyEvent true
            }
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
            val direction = when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                else -> null
            }
            if (direction != null && (!editing || !imeVisible)) {
                if (key.action == KeyEvent.ACTION_DOWN) {
                    if (direction == FocusDirection.Right && value.isNotEmpty()) {
                        clearFocusRequester.requestFocus()
                    } else {
                        focusManager.moveFocus(direction)
                    }
                }
                return@onPreviewKeyEvent true
            }
            false
        }

    Row(tvModifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MyIptvPalette.TextFieldBackground)
                .focusProperties {
                    if (television && value.isNotEmpty()) right = clearFocusRequester
                },
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
        if (value.isNotEmpty()) {
            MenuButton(
                text = "✕",
                onClick = { onValueChange("") },
                modifier = Modifier
                    .size(64.dp)
                    .focusRequester(clearFocusRequester)
                    .onFocusChanged { clearFocused = it.isFocused },
            )
        }
    }
}
