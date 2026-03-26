package com.termux.shared.termux.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.shared.logger.Logger
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

open class TermuxTerminalViewClientBase : TerminalViewClient {

    override fun onScale(scale: Float): Float = 1.0f

    override fun onSingleTapUp(e: MotionEvent) {}

    open override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    open override fun shouldEnforceCharBasedInput(): Boolean = false

    open override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {}

    override fun logError(tag: String, message: String) = Logger.logError(tag, message)

    override fun logWarn(tag: String, message: String) = Logger.logWarn(tag, message)

    override fun logInfo(tag: String, message: String) = Logger.logInfo(tag, message)

    override fun logDebug(tag: String, message: String) = Logger.logDebug(tag, message)

    override fun logVerbose(tag: String, message: String) = Logger.logVerbose(tag, message)

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
        Logger.logStackTraceWithMessage(tag, message, e)

    override fun logStackTrace(tag: String, e: Exception) = Logger.logStackTrace(tag, e)

}
