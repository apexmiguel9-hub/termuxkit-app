package com.termux.alpa_termuxkit.ui.component

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.shared.jni.TerminalManager

private const val TAG = "TerminalInputView"

// Key modifiers — same bitmask as Termux KeyHandler
private const val KEYMOD_SHIFT = 1
private const val KEYMOD_ALT = 2
private const val KEYMOD_CTRL = 4
private const val KEYMOD_NUM_LOCK = 8

class TerminalInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Combining accent state (same as Termux TerminalView.mCombiningAccent)
    private var combiningAccent: Int = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        alpha = 0f
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                              EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            // Multiple characters at once (e.g. paste from IME)
            event.characters?.let { chars ->
                val bytes = chars.toByteArray(Charsets.UTF_8)
                TerminalManager.writeInput(bytes)
            }
            return true
        }

        // Handle special key codes first (arrows, function keys, etc.)
        val metaState = event.metaState
        val controlDown = event.isCtrlPressed
        val leftAltDown = (metaState and KeyEvent.META_ALT_LEFT_ON) != 0
        val shiftDown = event.isShiftPressed
        val rightAltDownFromEvent = (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0

        var keyMod = 0
        if (controlDown) keyMod = keyMod or KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KEYMOD_NUM_LOCK

        // Try to handle as special key (arrows, page up/down, home, end, etc.)
        if (handleKeyCode(keyCode, keyMod)) {
            return true
        }

        // Clear Ctrl/meta from the event since we handle it ourselves
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDownFromEvent) {
            // Right Alt (AltGr) used for composing characters, don't clear
            bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        val effectiveMetaState = event.metaState and bitsToClear.inv()
        if (shiftDown) {
            bitsToClear or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON).also { _ -> }
        }

        // Get unicode character from key event
        val result = event.getUnicodeChar(effectiveMetaState)
        if (result == 0) {
            return false
        }

        // Handle combining accents (same as Termux)
        val oldCombiningAccent = combiningAccent
        if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // Combining accent - write previous accent if pending, store new one
            if (combiningAccent != 0) {
                inputCodePoint(combiningAccent, controlDown, leftAltDown)
            }
            combiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (combiningAccent != 0) {
                val combinedChar = KeyCharacterMap.getDeadChar(combiningAccent, result)
                if (combinedChar > 0) {
                    inputCodePoint(combinedChar, controlDown, leftAltDown)
                }
                combiningAccent = 0
            } else {
                inputCodePoint(result, controlDown, leftAltDown)
            }
        }

        if (combiningAccent != oldCombiningAccent) {
            invalidate()
        }

        return true
    }

    /**
     * Handle special key codes — ANSI escape sequences (copied from Termux KeyHandler)
     */
    private fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        val sendAltPrefix = (keyMod and KEYMOD_ALT) != 0

        fun esc(bytes: ByteArray) {
            if (sendAltPrefix) TerminalManager.writeInput(byteArrayOf(0x1B))
            TerminalManager.writeInput(bytes)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { esc(byteArrayOf(0x1B, 0x5B, 0x41)); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { esc(byteArrayOf(0x1B, 0x5B, 0x42)); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { esc(byteArrayOf(0x1B, 0x5B, 0x44)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { esc(byteArrayOf(0x1B, 0x5B, 0x43)); return true }
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_DPAD_CENTER -> { esc(byteArrayOf(0x1B, 0x5B, 0x48)); return true }
            KeyEvent.KEYCODE_MOVE_END -> { esc(byteArrayOf(0x1B, 0x5B, 0x46)); return true }
            KeyEvent.KEYCODE_PAGE_UP -> { esc(byteArrayOf(0x1B, 0x5B, 0x35, 0x7E)); return true }
            KeyEvent.KEYCODE_PAGE_DOWN -> { esc(byteArrayOf(0x1B, 0x5B, 0x36, 0x7E)); return true }
            KeyEvent.KEYCODE_HOME -> { esc(byteArrayOf(0x1B, 0x5B, 0x48)); return true }
            KeyEvent.KEYCODE_INSERT -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x7E)); return true }
            KeyEvent.KEYCODE_FORWARD_DEL -> { esc(byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)); return true }
            KeyEvent.KEYCODE_SYSRQ -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x34, 0x7E)); return true }
            KeyEvent.KEYCODE_BREAK -> { esc(byteArrayOf(0x1B, 0x5B, 0x33, 0x32, 0x7E)); return true }
            KeyEvent.KEYCODE_F1 -> { esc(byteArrayOf(0x1B, 0x4F, 0x50)); return true }
            KeyEvent.KEYCODE_F2 -> { esc(byteArrayOf(0x1B, 0x4F, 0x51)); return true }
            KeyEvent.KEYCODE_F3 -> { esc(byteArrayOf(0x1B, 0x4F, 0x52)); return true }
            KeyEvent.KEYCODE_F4 -> { esc(byteArrayOf(0x1B, 0x4F, 0x53)); return true }
            KeyEvent.KEYCODE_F5 -> { esc(byteArrayOf(0x1B, 0x5B, 0x31, 0x35, 0x7E)); return true }
            KeyEvent.KEYCODE_F6 -> { esc(byteArrayOf(0x1B, 0x5B, 0x31, 0x37, 0x7E)); return true }
            KeyEvent.KEYCODE_F7 -> { esc(byteArrayOf(0x1B, 0x5B, 0x31, 0x38, 0x7E)); return true }
            KeyEvent.KEYCODE_F8 -> { esc(byteArrayOf(0x1B, 0x5B, 0x31, 0x39, 0x7E)); return true }
            KeyEvent.KEYCODE_F9 -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x30, 0x7E)); return true }
            KeyEvent.KEYCODE_F10 -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x31, 0x7E)); return true }
            KeyEvent.KEYCODE_F11 -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x33, 0x7E)); return true }
            KeyEvent.KEYCODE_F12 -> { esc(byteArrayOf(0x1B, 0x5B, 0x32, 0x34, 0x7E)); return true }
        }
        return false
    }

    /**
     * Send a unicode code point to the terminal.
     * Same logic as Termux TerminalView.inputCodePoint():
     * - If Ctrl is held, map a-z -> 1-26, and handle special mappings (space, 2-8)
     */
    private fun inputCodePoint(codePoint: Int, controlDown: Boolean, leftAltDown: Boolean) {
        // Ctrl is held — map to control character
        var cp = codePoint
        if (controlDown) {
            when (cp) {
                in 'a'.code..'z'.code -> cp = cp - 'a'.code + 1
                in 'A'.code..'Z'.code -> cp = cp - 'A'.code + 1
                ' '.code, '2'.code -> cp = 0
                '['.code, '3'.code -> cp = 27
                '\\'.code, '4'.code -> cp = 28
                ']'.code, '5'.code -> cp = 29
                '^'.code, '6'.code -> cp = 30
                '_'.code, '7'.code -> cp = 31
                '8'.code -> cp = 127
                '/'.code -> cp = 31  // Ctrl+/ = Ctrl+_
            }
        }

        val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
        if (leftAltDown && !controlDown) {
            // Alt prefix (ESC)
            TerminalManager.writeInput(byteArrayOf(0x1B))
        }
        TerminalManager.writeInput(bytes)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
        }
    }
}

class TerminalInputConnection(private val view: TerminalInputView) : BaseInputConnection(view, false) {

    /**
     * Called when the IME commits text (soft keyboard).
     * Same logic as Termux sendTextToTerminal():
     * - \n -> \r (terminal enter behavior)
     * - Characters <= 31 (except ESC) get mapped to their terminal equivalents with ctrlHeld
     * - Proper surrogate pair handling
     */
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        text?.let { sendTextToTerminal(it) }
        return true
    }

    private fun sendTextToTerminal(text: CharSequence) {
        val textLength = text.length
        var i = 0
        while (i < textLength) {
            val firstChar = text[i]
            val codePoint: Int
            if (Character.isHighSurrogate(firstChar)) {
                i++
                if (i < textLength) {
                    codePoint = Character.toCodePoint(firstChar, text[i])
                } else {
                    // Dangling high surrogate
                    codePoint = 0xFFFD // Unicode replacement char
                }
            } else {
                codePoint = firstChar.code
            }
            i++

            // Same as Termux: if shift is read, uppercase
            // (not applicable for soft keyboard typically)

            var cp = codePoint
            var ctrlHeld = false

            // Control character mapping (same as Termux)
            if (cp <= 31 && cp != 27) {
                if (cp == '\n'.code) {
                    // AOSP keyboard sends \n for enter — terminal needs \r
                    cp = '\r'.code
                }
                ctrlHeld = true
                when (cp) {
                    31 -> cp = '_'.code
                    30 -> cp = '^'.code
                    29 -> cp = ']'.code
                    28 -> cp = '\\'.code
                    else -> cp += 96  // 1-26 -> a-z (control sequences)
                }
            }

            // Also handle \r directly
            if (cp == '\r'.code && !ctrlHeld) {
                TerminalManager.writeInput(byteArrayOf('\r'.code.toByte()))
                continue
            }

            // Encode and send
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            TerminalManager.writeInput(bytes)
        }
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    TerminalManager.writeInput("\r".toByteArray(Charsets.UTF_8))
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    TerminalManager.writeInput(byteArrayOf(0x7F))
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    TerminalManager.writeInput(byteArrayOf(0x09))
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    TerminalManager.writeInput(byteArrayOf(0x1B))
                    return true
                }
                KeyEvent.KEYCODE_SPACE -> {
                    TerminalManager.writeInput(byteArrayOf(0x20))
                    return true
                }
                // Let onKeyDown handle the rest (arrows, function keys, unicode)
                else -> {
                    return view.onKeyDown(event.keyCode, event)
                }
            }
        }
        return super.sendKeyEvent(event)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength) {
            TerminalManager.writeInput(byteArrayOf(0x7F))
        }
        return true
    }

    override fun finishComposingText(): Boolean {
        return super.finishComposingText()
    }
}
