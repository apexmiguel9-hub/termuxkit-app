package com.termux.shared.shell

import com.termux.shared.file.FileUtils
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import java.lang.reflect.Field

object ShellUtils {

    /** Get process id of [Process]. */
    fun getPid(p: Process): Int {
        return try {
            val f: Field = p.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            try {
                f.getInt(p)
            } finally {
                f.isAccessible = false
            }
        } catch (e: Throwable) {
            -1
        }
    }

    /** Setup shell command arguments for the execute. */
    fun setupShellCommandArguments(executable: String, arguments: Array<String>? = null): Array<String> {
        return arrayOf(executable) + (arguments ?: emptyArray())
    }

    /** Get basename for executable. */
    fun getExecutableBasename(executable: String?): String? {
        return FileUtils.getFileBasename(executable)
    }

    /** Get transcript for [TerminalSession]. */
    fun getTerminalSessionTranscriptText(
        terminalSession: TerminalSession?,
        linesJoined: Boolean = false,
        trim: Boolean = true
    ): String? {
        val terminalEmulator = terminalSession?.emulator ?: return null
        val terminalBuffer = terminalEmulator.getScreen() ?: return null

        val transcriptText = if (linesJoined)
            terminalBuffer.getTranscriptTextWithFullLinesJoined()
        else
            terminalBuffer.getTranscriptTextWithoutJoinedLines()

        return transcriptText?.let { if (trim) it.trim() else it }
    }

}
