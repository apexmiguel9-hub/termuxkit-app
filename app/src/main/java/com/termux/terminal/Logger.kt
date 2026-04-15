package com.termux.terminal

import android.util.Log

/**
 * Simple internal logger for terminal-emulator module.
 * This avoids circular dependency with termux-shared module.
 */
internal object Logger {
    private const val DEFAULT_LOG_TAG = "Terminal"

    fun logError(tag: String, message: String) = Log.e(tag, message)

    fun logWarn(tag: String, message: String) = Log.w(tag, message)

    fun logStackTraceWithMessage(tag: String, message: String?, throwable: Throwable?) {
        Log.e(tag, message ?: "null", throwable)
    }
}
