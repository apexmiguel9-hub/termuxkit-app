package com.termux.terminal

/**
 * Stub minimal de TerminalSession — solo para satisfacer la firma de TerminalSessionClient.
 * En alpha_termuxkit, el PTY lo maneja Rust directamente, no una TerminalSession Java.
 */
class TerminalSession(
    val terminalEmulator: TerminalEmulator,
    val shellPath: String = "/system/bin/sh",
    val cols: Int = 80,
    val rows: Int = 24
) {
    var pid: Int = -1
    var title: String = shellPath
        private set

    fun setTitle(newTitle: String) {
        title = newTitle
    }
}
