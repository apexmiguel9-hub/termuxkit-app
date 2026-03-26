package com.termux.app.terminal

import androidx.annotation.NonNull
import com.termux.app.TermuxService
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession

/** The [TerminalSessionClient] implementation that may require a [Service] for its interface methods. */
open class TermuxTerminalSessionServiceClient(service: TermuxService) : TermuxTerminalSessionClientBase() {

    companion object {
        private const val LOG_TAG = "TermuxTerminalSessionServiceClient"
    }

    private val service: TermuxService = service

    override fun setTerminalShellPid(@NonNull terminalSession: TerminalSession, pid: Int) {
        val termuxSession = service.getTermuxSessionForTerminalSession(terminalSession)
        if (termuxSession != null) {
            termuxSession.executionCommand.mPid = pid
        }
    }

}
