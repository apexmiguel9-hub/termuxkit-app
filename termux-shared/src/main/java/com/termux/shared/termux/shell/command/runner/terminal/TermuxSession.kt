package com.termux.shared.termux.shell.command.runner.terminal
import java.util.*

import android.content.Context
import android.system.OsConstants
import androidx.annotation.NonNull
import com.google.common.base.Joiner
import com.termux.shared.R
import com.termux.shared.errors.Errno
import com.termux.shared.logger.Logger
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.IShellEnvironment
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.shell.command.environment.UnixShellEnvironment
import com.termux.shared.shell.command.result.ResultData
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * A class that maintains info for foreground Termux sessions.
 * It also provides a way to link each [TerminalSession] with the [ExecutionCommand]
 * that started it.
 */
class TermuxSession private constructor(
    val terminalSession: TerminalSession,
    val executionCommand: ExecutionCommand,
    val termuxSessionClient: TermuxSessionClient?,
    val setStdoutOnExit: Boolean
) {

    companion object {
        private const val LOG_TAG = "TermuxSession"

        /**
         * Start execution of an [ExecutionCommand] with `Runtime.exec(String[], String[], File)`.
         *
         * The [ExecutionCommand.executable], must be set, [ExecutionCommand.commandLabel],
         * [ExecutionCommand.arguments] and [ExecutionCommand.workingDirectory] may optionally
         * be set.
         *
         * If [ExecutionCommand.executable] is `null`, then a default shell is automatically
         * chosen.
         *
         * @param currentPackageContext The [Context] for operations. This must be the context for
         *                              the current package and not the context of a `sharedUserId` package,
         *                              since environment setup may be dependent on current package.
         * @param executionCommand The [ExecutionCommand] containing the information for execution command.
         * @param terminalSessionClient The [TerminalSessionClient] interface implementation.
         * @param termuxSessionClient The [TermuxSessionClient] interface implementation.
         * @param shellEnvironmentClient The [IShellEnvironment] interface implementation.
         * @param additionalEnvironment The additional shell environment variables to export. Existing
         *                              variables will be overridden.
         * @param setStdoutOnExit If set to `true`, then the [ResultData.stdout]
         *                        available in the [TermuxSessionClient.onTermuxSessionExited]
         *                        callback will be set to the [TerminalSession] transcript. The session
         *                        transcript will contain both stdout and stderr combined, basically
         *                        anything sent to the the pseudo terminal /dev/pts, including PS1 prefixes.
         *                        Set this to `true` only if the session transcript is required,
         *                        since this requires extra processing to get it.
         * @return Returns the [TermuxSession]. This will be `null` if failed to start the execution command.
         */
        @JvmStatic
        fun execute(
            @NonNull currentPackageContext: Context,
            @NonNull executionCommand: ExecutionCommand,
            @NonNull terminalSessionClient: TerminalSessionClient,
            termuxSessionClient: TermuxSessionClient?,
            @NonNull shellEnvironmentClient: IShellEnvironment,
            additionalEnvironment: HashMap<String, String>?,
            setStdoutOnExit: Boolean
        ): TermuxSession? {
            if (executionCommand.executable.isNullOrEmpty())
                executionCommand.executable = null
            if (executionCommand.workingDirectory.isNullOrEmpty())
                executionCommand.workingDirectory = shellEnvironmentClient.getDefaultWorkingDirectoryPath()
            if (executionCommand.workingDirectory.isNullOrEmpty())
                executionCommand.workingDirectory = "/"

            var defaultBinPath = shellEnvironmentClient.getDefaultBinPath()
            if (defaultBinPath.isEmpty())
                defaultBinPath = "/system/bin"

            var isLoginShell = false
            if (executionCommand.executable == null) {
                if (!executionCommand.isFailsafe) {
                    for (shellBinary in UnixShellEnvironment.LOGIN_SHELL_BINARIES) {
                        val shellFile = File(defaultBinPath.ifEmpty { "/system/bin" }, shellBinary)
                        if (shellFile.canExecute()) {
                            executionCommand.executable = shellFile.absolutePath
                            break
                        }
                    }
                }

                if (executionCommand.executable == null) {
                    // Fall back to system shell as last resort:
                    // Do not start a login shell since ~/.profile may cause startup failure if its invalid.
                    // /system/bin/sh is provided by mksh (not toybox) and does load .mkshrc but for android its set
                    // to /system/etc/mkshrc even though its default is ~/.mkshrc.
                    // So /system/etc/mkshrc must still be valid for failsafe session to start properly.
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=663
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=41
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/Android.bp;l=114
                    executionCommand.executable = "/system/bin/sh"
                } else {
                    isLoginShell = true
                }
            }

            // Setup command args
            val executable = executionCommand.executable ?: return null
            val commandArgs = shellEnvironmentClient.setupShellCommandArguments(executable, executionCommand.arguments)

            executionCommand.executable = commandArgs[0]
            val processName = (if (isLoginShell) "-" else "") + ShellUtils.getExecutableBasename(executionCommand.executable)

            val arguments = arrayOfNulls<String>(commandArgs.size)
            arguments[0] = processName
            if (commandArgs.size > 1) System.arraycopy(commandArgs, 1, arguments, 1, commandArgs.size - 1)

            executionCommand.arguments = arguments.filterNotNull().toTypedArray()

            if (executionCommand.commandLabel == null)
                executionCommand.commandLabel = processName

            // Setup command environment
            var environment = shellEnvironmentClient.setupShellCommandEnvironment(currentPackageContext, executionCommand)
            if (additionalEnvironment != null)
                environment.putAll(additionalEnvironment)
            val environmentList = ShellEnvironmentUtils.convertEnvironmentToEnviron(environment)
            if (environmentList == null) {
                executionCommand.setStateFailed(Errno.ERRNO_FAILED.code, currentPackageContext.getString(R.string.error_failed_to_execute_termux_session_command, executionCommand.getCommandIdAndLabelLogString()))
                processTermuxSessionResult(null, executionCommand)
                return null
            }
            val sortedList: List<String> = environmentList.sorted()
            val environmentArray = sortedList.toTypedArray()

            if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTING)) {
                executionCommand.setStateFailed(Errno.ERRNO_FAILED.code, currentPackageContext.getString(R.string.error_failed_to_execute_termux_session_command, executionCommand.getCommandIdAndLabelLogString()))
                processTermuxSessionResult(null, executionCommand)
                return null
            }

            Logger.logDebugExtended(LOG_TAG, executionCommand.toString())
            Logger.logVerboseExtended(LOG_TAG, "\"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession Environment:\n" + Joiner.on("\n").join(environmentArray))

            Logger.logDebug(LOG_TAG, "Running \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession")
            val sessionExecutable = executionCommand.executable ?: ""
            val workingDir = executionCommand.workingDirectory ?: ""
            val args = executionCommand.arguments ?: emptyArray()
            val terminalSession = TerminalSession(sessionExecutable,
                workingDir, args, environmentArray,
                executionCommand.terminalTranscriptRows, terminalSessionClient)

            executionCommand.shellName?.let {
                terminalSession.sessionName = it
            }

            return TermuxSession(terminalSession, executionCommand, termuxSessionClient, setStdoutOnExit)
        }

        /**
         * Process the results of [TermuxSession] or [ExecutionCommand].
         *
         * Only one of `termuxSession` and `executionCommand` must be set.
         *
         * If the `termuxSession` and its [termuxSessionClient] are not `null`,
         * then the [TermuxSessionClient.onTermuxSessionExited]
         * callback will be called.
         *
         * @param termuxSession The [TermuxSession], which should be set if
         *                   [execute] successfully started the process.
         * @param executionCommand The [ExecutionCommand], which should be set if
         *                          [execute] failed to start the process.
         */
        private fun processTermuxSessionResult(termuxSession: TermuxSession?, executionCommand: ExecutionCommand?) {
            var command = executionCommand
            if (termuxSession != null)
                command = termuxSession.executionCommand

            if (command == null) return

            if (command.shouldNotProcessResults()) {
                Logger.logDebug(LOG_TAG, "Ignoring duplicate call to process \"${command.getCommandIdAndLabelLogString()}\" TermuxSession result")
                return
            }

            Logger.logDebug(LOG_TAG, "Processing \"${command.getCommandIdAndLabelLogString()}\" TermuxSession result")

            if (termuxSession != null && termuxSession.termuxSessionClient != null) {
                termuxSession.termuxSessionClient.onTermuxSessionExited(termuxSession)
            } else {
                // If a callback is not set and execution command didn't fail, then we set success state now
                // Otherwise, the callback host can set it himself when its done with the termuxSession
                if (!command.isStateFailed())
                    command.setState(ExecutionCommand.ExecutionState.SUCCESS)
            }
        }
    }

    /**
     * Signal that this [TermuxSession] has finished.  This should be called when
     * [TerminalSessionClient.onSessionFinished] callback is received by the caller.
     *
     * If the processes has finished, then sets [ResultData.stdout], [ResultData.stderr]
     * and [ResultData.exitCode] for the [executionCommand] of the `termuxTask`
     * and then calls [processTermuxSessionResult] to process the result}.
     *
     */
    fun finish() {
        // If process is still running, then ignore the call
        if (terminalSession.isRunning) return

        val exitCode = terminalSession.exitStatus

        if (exitCode == 0)
            Logger.logDebug(LOG_TAG, "The \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession exited normally")
        else
            Logger.logDebug(LOG_TAG, "The \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession exited with code: $exitCode")

        // If the execution command has already failed, like SIGKILL was sent, then don't continue
        if (executionCommand.isStateFailed()) {
            Logger.logDebug(LOG_TAG, "Ignoring setting \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession state to ExecutionState.EXECUTED and processing results since it has already failed")
            return
        }

        executionCommand.resultData.exitCode = exitCode

        if (setStdoutOnExit)
            executionCommand.resultData.stdout.append(ShellUtils.getTerminalSessionTranscriptText(terminalSession, true, false))

        if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTED))
            return

        processTermuxSessionResult(this, null)
    }

    /**
     * Kill this [TermuxSession] by sending a [OsConstants.SIGILL] to its [terminalSession]
     * if its still executing.
     *
     * @param context The [Context] for operations.
     * @param processResult If set to `true`, then the [processTermuxSessionResult]
     *                      will be called to process the failure.
     */
    fun killIfExecuting(@NonNull context: Context, processResult: Boolean) {
        // If execution command has already finished executing, then no need to process results or send SIGKILL
        if (executionCommand.hasExecuted()) {
            Logger.logDebug(LOG_TAG, "Ignoring sending SIGKILL to \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession since it has already finished executing")
            return
        }

        Logger.logDebug(LOG_TAG, "Send SIGKILL to \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession")
        if (executionCommand.setStateFailed(Errno.ERRNO_FAILED.code, context.getString(R.string.error_sending_sigkill_to_process))) {
            if (processResult) {
                executionCommand.resultData.exitCode = 137 // SIGKILL

                // Get whatever output has been set till now in case its needed
                if (setStdoutOnExit)
                    executionCommand.resultData.stdout.append(ShellUtils.getTerminalSessionTranscriptText(terminalSession, true, false))

                processTermuxSessionResult(this, null)
            }
        }

        // Send SIGKILL to process
        terminalSession.finishIfRunning()
    }
}

/**
 * Client interface for [TermuxSession] callbacks.
 */
interface TermuxSessionClient {

    /**
     * Callback function for when [TermuxSession] exits.
     *
     * @param termuxSession The [TermuxSession] that exited.
     */
    fun onTermuxSessionExited(termuxSession: TermuxSession)
}
