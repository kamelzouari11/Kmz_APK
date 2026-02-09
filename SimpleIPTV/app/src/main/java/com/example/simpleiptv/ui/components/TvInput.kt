package com.example.simpleiptv.ui.components

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TvInput(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        isPassword: Boolean = false,
        focusManager: androidx.compose.ui.focus.FocusManager,
        leadingIcon: ImageVector? = null,
        onFocusChanged: ((androidx.compose.ui.focus.FocusState) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    AndroidView(
            modifier =
                    modifier.fillMaxWidth()
                            .height(60.dp)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                onFocusChanged?.invoke(it)
                            }
                            .scale(if (isFocused) 1.02f else 1f)
                            .background(
                                    color =
                                            if (isFocused) Color.White.copy(alpha = 0.95f)
                                            else
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.3f
                                                    ),
                                    shape = MaterialTheme.shapes.medium
                            ),
            factory = { ctx ->
                EditText(ctx).apply {
                    setHint(label)
                    setSingleLine(true)
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(android.graphics.Color.GRAY)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(32, 16, 32, 16)
                    setGravity(Gravity.CENTER_VERTICAL)
                    textSize = 16f

                    inputType =
                            if (isPassword) {
                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            } else {
                                InputType.TYPE_CLASS_TEXT
                            }
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    // showSoftInputOnFocus = false // Removed to allow mobile keyboard on tap

                    setOnFocusChangeListener { _, hasFocus -> isFocused = hasFocus }

                    setOnKeyListener { v, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP &&
                                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                        ) {
                            v.requestFocus()
                            val imm =
                                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as
                                            InputMethodManager
                            @Suppress("DEPRECATION")
                            imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
                            return@setOnKeyListener true
                        }
                        false
                    }

                    addTextChangedListener(
                            object : android.text.TextWatcher {
                                override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int
                                ) {}
                                override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int
                                ) {
                                    if (s.toString() != value) {
                                        onValueChange(s.toString())
                                    }
                                }
                                override fun afterTextChanged(s: android.text.Editable?) {}
                            }
                    )
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    try {
                        editText.setSelection(editText.text.length)
                    } catch (e: Exception) {}
                }
                if (isFocused) {
                    editText.setTextColor(android.graphics.Color.BLACK)
                    editText.setHintTextColor(android.graphics.Color.DKGRAY)
                } else {
                    editText.setTextColor(android.graphics.Color.WHITE)
                    editText.setHintTextColor(android.graphics.Color.GRAY)
                }
            }
    )
}
