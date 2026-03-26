package com.termux.terminal
import java.util.*

import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BREAK
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_ESCAPE
import android.view.KeyEvent.KEYCODE_F1
import android.view.KeyEvent.KEYCODE_F10
import android.view.KeyEvent.KEYCODE_F11
import android.view.KeyEvent.KEYCODE_F12
import android.view.KeyEvent.KEYCODE_F2
import android.view.KeyEvent.KEYCODE_F3
import android.view.KeyEvent.KEYCODE_F4
import android.view.KeyEvent.KEYCODE_F5
import android.view.KeyEvent.KEYCODE_F6
import android.view.KeyEvent.KEYCODE_F7
import android.view.KeyEvent.KEYCODE_F8
import android.view.KeyEvent.KEYCODE_F9
import android.view.KeyEvent.KEYCODE_FORWARD_DEL
import android.view.KeyEvent.KEYCODE_INSERT
import android.view.KeyEvent.KEYCODE_MOVE_END
import android.view.KeyEvent.KEYCODE_MOVE_HOME
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import android.view.KeyEvent.KEYCODE_NUMPAD_1
import android.view.KeyEvent.KEYCODE_NUMPAD_2
import android.view.KeyEvent.KEYCODE_NUMPAD_3
import android.view.KeyEvent.KEYCODE_NUMPAD_4
import android.view.KeyEvent.KEYCODE_NUMPAD_5
import android.view.KeyEvent.KEYCODE_NUMPAD_6
import android.view.KeyEvent.KEYCODE_NUMPAD_7
import android.view.KeyEvent.KEYCODE_NUMPAD_8
import android.view.KeyEvent.KEYCODE_NUMPAD_9
import android.view.KeyEvent.KEYCODE_NUMPAD_ADD
import android.view.KeyEvent.KEYCODE_NUMPAD_COMMA
import android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE
import android.view.KeyEvent.KEYCODE_NUMPAD_DOT
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS
import android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY
import android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT
import android.view.KeyEvent.KEYCODE_NUM_LOCK
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_SYSRQ
import android.view.KeyEvent.KEYCODE_TAB

object KeyHandler {

    const val KEYMOD_ALT = -0x80000000 // 0x80000000 as signed int
    const val KEYMOD_CTRL = -0x40000000 // 0x40000000 as signed int
    const val KEYMOD_SHIFT = -0x20000000 // 0x20000000 as signed int
    const val KEYMOD_NUM_LOCK = 0x10000000

    private val TERMCAP_TO_KEYCODE: Map<String, Int> = mapOf(
        "%i" to (KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT),
        "#2" to (KEYMOD_SHIFT or KEYCODE_MOVE_HOME), // Shifted home
        "*7" to (KEYMOD_SHIFT or KEYCODE_MOVE_END), // Shifted end key

        "k1" to KEYCODE_F1,
        "k2" to KEYCODE_F2,
        "k3" to KEYCODE_F3,
        "k4" to KEYCODE_F4,
        "k5" to KEYCODE_F5,
        "k6" to KEYCODE_F6,
        "k7" to KEYCODE_F7,
        "k8" to KEYCODE_F8,
        "k9" to KEYCODE_F9,
        "k;" to KEYCODE_F10,
        "F1" to KEYCODE_F11,
        "F2" to KEYCODE_F12,
        "F3" to (KEYMOD_SHIFT or KEYCODE_F1),
        "F4" to (KEYMOD_SHIFT or KEYCODE_F2),
        "F5" to (KEYMOD_SHIFT or KEYCODE_F3),
        "F6" to (KEYMOD_SHIFT or KEYCODE_F4),
        "F7" to (KEYMOD_SHIFT or KEYCODE_F5),
        "F8" to (KEYMOD_SHIFT or KEYCODE_F6),
        "F9" to (KEYMOD_SHIFT or KEYCODE_F7),
        "FA" to (KEYMOD_SHIFT or KEYCODE_F8),
        "FB" to (KEYMOD_SHIFT or KEYCODE_F9),
        "FC" to (KEYMOD_SHIFT or KEYCODE_F10),
        "FD" to (KEYMOD_SHIFT or KEYCODE_F11),
        "FE" to (KEYMOD_SHIFT or KEYCODE_F12),

        "kb" to KEYCODE_DEL, // backspace key

        "kd" to KEYCODE_DPAD_DOWN, // terminfo=kcud1, down-arrow key
        "kh" to KEYCODE_MOVE_HOME,
        "kl" to KEYCODE_DPAD_LEFT,
        "kr" to KEYCODE_DPAD_RIGHT,

        // K1=Upper left of keypad:
        // t_K1 <kHome> keypad home key
        // t_K3 <kPageUp> keypad page-up key
        // t_K4 <kEnd> keypad end key
        // t_K5 <kPageDown> keypad page-down key
        "K1" to KEYCODE_MOVE_HOME,
        "K3" to KEYCODE_PAGE_UP,
        "K4" to KEYCODE_MOVE_END,
        "K5" to KEYCODE_PAGE_DOWN,

        "ku" to KEYCODE_DPAD_UP,

        "kB" to (KEYMOD_SHIFT or KEYCODE_TAB), // termcap=kB, terminfo=kcbt: Back-tab
        "kD" to KEYCODE_FORWARD_DEL, // terminfo=kdch1, delete-character key
        "kDN" to (KEYMOD_SHIFT or KEYCODE_DPAD_DOWN), // non-standard shifted arrow down
        "kF" to (KEYMOD_SHIFT or KEYCODE_DPAD_DOWN), // terminfo=kind, scroll-forward key
        "kI" to KEYCODE_INSERT,
        "kN" to KEYCODE_PAGE_UP,
        "kP" to KEYCODE_PAGE_DOWN,
        "kR" to (KEYMOD_SHIFT or KEYCODE_DPAD_UP), // terminfo=kri, scroll-backward key
        "kUP" to (KEYMOD_SHIFT or KEYCODE_DPAD_UP), // non-standard shifted up

        "@7" to KEYCODE_MOVE_END,
        "@8" to KEYCODE_NUMPAD_ENTER
    )

    fun getCodeFromTermcap(termcap: String?, cursorKeysApplication: Boolean, keypadApplication: Boolean): String? {
        val keyCodeAndMod = TERMCAP_TO_KEYCODE[termcap] ?: return null
        var keyCode = keyCodeAndMod.toInt()
        var keyMod = 0
        if ((keyCode and KEYMOD_SHIFT) != 0) {
            keyMod = keyMod or KEYMOD_SHIFT
            keyCode = keyCode and KEYMOD_SHIFT.inv()
        }
        if ((keyCode and KEYMOD_CTRL) != 0) {
            keyMod = keyMod or KEYMOD_CTRL
            keyCode = keyCode and KEYMOD_CTRL.inv()
        }
        if ((keyCode and KEYMOD_ALT) != 0) {
            keyMod = keyMod or KEYMOD_ALT
            keyCode = keyCode and KEYMOD_ALT.inv()
        }
        if ((keyCode and KEYMOD_NUM_LOCK) != 0) {
            keyMod = keyMod or KEYMOD_NUM_LOCK
            keyCode = keyCode and KEYMOD_NUM_LOCK.inv()
        }
        return getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication)
    }

    fun getCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApplication: Boolean): String? {
        val numLockOn = (keyMode and KEYMOD_NUM_LOCK) != 0
        val keyMod = keyMode and KEYMOD_NUM_LOCK.inv()

        return when (keyCode) {
            KEYCODE_DPAD_CENTER -> "\u000D"

            KEYCODE_DPAD_UP ->
                if (keyMod == 0) (if (cursorApp) "\u001BOA" else "\u001B[A")
                else transformForModifiers("\u001B[1", keyMod, 'A')
            KEYCODE_DPAD_DOWN ->
                if (keyMod == 0) (if (cursorApp) "\u001BOB" else "\u001B[B")
                else transformForModifiers("\u001B[1", keyMod, 'B')
            KEYCODE_DPAD_RIGHT ->
                if (keyMod == 0) (if (cursorApp) "\u001BOC" else "\u001B[C")
                else transformForModifiers("\u001B[1", keyMod, 'C')
            KEYCODE_DPAD_LEFT ->
                if (keyMod == 0) (if (cursorApp) "\u001BOD" else "\u001B[D")
                else transformForModifiers("\u001B[1", keyMod, 'D')

            KEYCODE_MOVE_HOME ->
                // Note that KEYCODE_HOME is handled by the system and never delivered to applications.
                // On a Logitech k810 keyboard KEYCODE_MOVE_HOME is sent by FN+LeftArrow.
                if (keyMod == 0) (if (cursorApp) "\u001BOH" else "\u001B[H")
                else transformForModifiers("\u001B[1", keyMod, 'H')
            KEYCODE_MOVE_END ->
                if (keyMod == 0) (if (cursorApp) "\u001BOF" else "\u001B[F")
                else transformForModifiers("\u001B[1", keyMod, 'F')

            // An xterm can send function keys F1 to F4 in two modes: vt100 compatible or
            // not. Because Vim may not know what the xterm is sending, both types of keys
            // are recognized. The same happens for the <Home> and <End> keys.
            // normal vt100 ~
            // <F1> t_k1 <Esc>[11~ <xF1> <Esc>OP *<xF1>-xterm*
            // <F2> t_k2 <Esc>[12~ <xF2> <Esc>OQ *<xF2>-xterm*
            // <F3> t_k3 <Esc>[13~ <xF3> <Esc>OR *<xF3>-xterm*
            // <F4> t_k4 <Esc>[14~ <xF4> <Esc>OS *<xF4>-xterm*
            // <Home> t_kh <Esc>[7~ <xHome> <Esc>OH *<xHome>-xterm*
            // <End> t_@7 <Esc>[4~ <xEnd> <Esc>OF *<xEnd>-xterm*
            KEYCODE_F1 ->
                if (keyMod == 0) "\u001BOP" else transformForModifiers("\u001B[1", keyMod, 'P')
            KEYCODE_F2 ->
                if (keyMod == 0) "\u001BOQ" else transformForModifiers("\u001B[1", keyMod, 'Q')
            KEYCODE_F3 ->
                if (keyMod == 0) "\u001BOR" else transformForModifiers("\u001B[1", keyMod, 'R')
            KEYCODE_F4 ->
                if (keyMod == 0) "\u001BOS" else transformForModifiers("\u001B[1", keyMod, 'S')
            KEYCODE_F5 -> transformForModifiers("\u001B[15", keyMod, '~')
            KEYCODE_F6 -> transformForModifiers("\u001B[17", keyMod, '~')
            KEYCODE_F7 -> transformForModifiers("\u001B[18", keyMod, '~')
            KEYCODE_F8 -> transformForModifiers("\u001B[19", keyMod, '~')
            KEYCODE_F9 -> transformForModifiers("\u001B[20", keyMod, '~')
            KEYCODE_F10 -> transformForModifiers("\u001B[21", keyMod, '~')
            KEYCODE_F11 -> transformForModifiers("\u001B[23", keyMod, '~')
            KEYCODE_F12 -> transformForModifiers("\u001B[24", keyMod, '~')

            KEYCODE_SYSRQ -> "\u001B[32~" // Sys Request / Print
            // Is this Scroll lock? case Cancel: return "\u001B[33~";
            KEYCODE_BREAK -> "\u001B[34~" // Pause/Break

            KEYCODE_ESCAPE, KEYCODE_BACK -> "\u001B"

            KEYCODE_INSERT -> transformForModifiers("\u001B[2", keyMod, '~')
            KEYCODE_FORWARD_DEL -> transformForModifiers("\u001B[3", keyMod, '~')

            KEYCODE_PAGE_UP -> transformForModifiers("\u001B[5", keyMod, '~')
            KEYCODE_PAGE_DOWN -> transformForModifiers("\u001B[6", keyMod, '~')
            KEYCODE_DEL -> {
                val prefix = if ((keyMod and KEYMOD_ALT) == 0) "" else "\u001B"
                // Just do what xterm and gnome-terminal does:
                prefix + (if ((keyMod and KEYMOD_CTRL) == 0) "\u007F" else "\u0008")
            }
            KEYCODE_NUM_LOCK ->
                if (keypadApplication) "\u001BOP" else null
            KEYCODE_SPACE ->
                // If ctrl is not down, return null so that it goes through normal input processing (which may e.g. cause a
                // combining accent to be written):
                if ((keyMod and KEYMOD_CTRL) == 0) null else "\u0000"
            KEYCODE_TAB ->
                // This is back-tab when shifted:
                if ((keyMod and KEYMOD_SHIFT) == 0) "\u0009" else "\u001B[Z"
            KEYCODE_ENTER ->
                if ((keyMod and KEYMOD_ALT) == 0) "\r" else "\u001B\r"

            KEYCODE_NUMPAD_ENTER ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'M') else "\n"
            KEYCODE_NUMPAD_MULTIPLY ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'j') else "*"
            KEYCODE_NUMPAD_ADD ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'k') else "+"
            KEYCODE_NUMPAD_COMMA -> ","
            KEYCODE_NUMPAD_DOT ->
                if (numLockOn) {
                    if (keypadApplication) "\u001Bon" else "."
                } else {
                    // DELETE
                    transformForModifiers("\u001B[3", keyMod, '~')
                }
            KEYCODE_NUMPAD_SUBTRACT ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'm') else "-"
            KEYCODE_NUMPAD_DIVIDE ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'o') else "/"
            KEYCODE_NUMPAD_0 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'p') else "0"
                } else {
                    // INSERT
                    transformForModifiers("\u001B[2", keyMod, '~')
                }
            KEYCODE_NUMPAD_1 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'q') else "1"
                } else {
                    // END
                    if (keyMod == 0) (if (cursorApp) "\u001BOF" else "\u001B[F")
                    else transformForModifiers("\u001B[1", keyMod, 'F')
                }
            KEYCODE_NUMPAD_2 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'r') else "2"
                } else {
                    // DOWN
                    if (keyMod == 0) (if (cursorApp) "\u001BOB" else "\u001B[B")
                    else transformForModifiers("\u001B[1", keyMod, 'B')
                }
            KEYCODE_NUMPAD_3 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 's') else "3"
                } else {
                    // PGDN
                    "\u001B[6~"
                }
            KEYCODE_NUMPAD_4 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 't') else "4"
                } else {
                    // LEFT
                    if (keyMod == 0) (if (cursorApp) "\u001BOD" else "\u001B[D")
                    else transformForModifiers("\u001B[1", keyMod, 'D')
                }
            KEYCODE_NUMPAD_5 ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'u') else "5"
            KEYCODE_NUMPAD_6 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'v') else "6"
                } else {
                    // RIGHT
                    if (keyMod == 0) (if (cursorApp) "\u001BOC" else "\u001B[C")
                    else transformForModifiers("\u001B[1", keyMod, 'C')
                }
            KEYCODE_NUMPAD_7 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'w') else "7"
                } else {
                    // HOME
                    if (keyMod == 0) (if (cursorApp) "\u001BOH" else "\u001B[H")
                    else transformForModifiers("\u001B[1", keyMod, 'H')
                }
            KEYCODE_NUMPAD_8 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'x') else "8"
                } else {
                    // UP
                    if (keyMod == 0) (if (cursorApp) "\u001BOA" else "\u001B[A")
                    else transformForModifiers("\u001B[1", keyMod, 'A')
                }
            KEYCODE_NUMPAD_9 ->
                if (numLockOn) {
                    if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'y') else "9"
                } else {
                    // PGUP
                    "\u001B[5~"
                }
            KEYCODE_NUMPAD_EQUALS ->
                if (keypadApplication) transformForModifiers("\u001BO", keyMod, 'X') else "="

            else -> null
        }
    }

    private fun transformForModifiers(start: String, keymod: Int, lastChar: Char): String {
        val modifier = when (keymod) {
            KEYMOD_SHIFT -> 2
            KEYMOD_ALT -> 3
            (KEYMOD_SHIFT or KEYMOD_ALT) -> 4
            KEYMOD_CTRL -> 5
            (KEYMOD_SHIFT or KEYMOD_CTRL) -> 6
            (KEYMOD_ALT or KEYMOD_CTRL) -> 7
            (KEYMOD_SHIFT or KEYMOD_ALT or KEYMOD_CTRL) -> 8
            else -> return start + lastChar
        }
        return start + ";$modifier$lastChar"
    }
}
