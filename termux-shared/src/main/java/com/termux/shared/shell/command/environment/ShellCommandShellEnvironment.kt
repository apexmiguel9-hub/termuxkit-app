package com.termux.shared.shell.command.environment

import android.content.Context
import androidx.annotation.NonNull
import com.termux.shared.shell.command.ExecutionCommand
import java.util.HashMap

/**
 * Environment for [ExecutionCommand].
 */
open class ShellCommandShellEnvironment {

    /** Environment variable prefix for the [ExecutionCommand]. */
    @JvmField
    val SHELL_CMD_ENV_PREFIX = "SHELL_CMD__"

    /** Environment variable for the [ExecutionCommand.Runner] name. */
    @JvmField
    val ENV_SHELL_CMD__RUNNER_NAME = "${SHELL_CMD_ENV_PREFIX}RUNNER_NAME"

    /** Environment variable for the package name running the [ExecutionCommand]. */
    @JvmField
    val ENV_SHELL_CMD__PACKAGE_NAME = "${SHELL_CMD_ENV_PREFIX}PACKAGE_NAME"

    /** Environment variable for the [ExecutionCommand.id]/TermuxShellManager.SHELL_ID name.
     * This will be common for all runners. */
    @JvmField
    val ENV_SHELL_CMD__SHELL_ID = "${SHELL_CMD_ENV_PREFIX}SHELL_ID"

    /** Environment variable for the [ExecutionCommand.shellName] name. */
    @JvmField
    val ENV_SHELL_CMD__SHELL_NAME = "${SHELL_CMD_ENV_PREFIX}SHELL_NAME"

    /** Environment variable for the [ExecutionCommand.Runner.APP_SHELL] number since boot. */
    @JvmField
    val ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_BOOT = "${SHELL_CMD_ENV_PREFIX}APP_SHELL_NUMBER_SINCE_BOOT"

    /** Environment variable for the [ExecutionCommand.Runner.TERMINAL_SESSION] number since boot. */
    @JvmField
    val ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_BOOT = "${SHELL_CMD_ENV_PREFIX}TERMINAL_SESSION_NUMBER_SINCE_BOOT"

    /** Environment variable for the [ExecutionCommand.Runner.APP_SHELL] number since app start. */
    @JvmField
    val ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START = "${SHELL_CMD_ENV_PREFIX}APP_SHELL_NUMBER_SINCE_APP_START"

    /** Environment variable for the [ExecutionCommand.Runner.TERMINAL_SESSION] number since app start. */
    @JvmField
    val ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START = "${SHELL_CMD_ENV_PREFIX}TERMINAL_SESSION_NUMBER_SINCE_APP_START"

    /**
     * Get shell environment containing info for [ExecutionCommand].
     *
     * @param currentPackageContext The [Context] for operations.
     * @param executionCommand The [ExecutionCommand] for which to get environment.
     * @return Returns the [HashMap] containing the environment variables.
     */
    @NonNull
    open fun getEnvironment(@NonNull currentPackageContext: Context, @NonNull executionCommand: ExecutionCommand): HashMap<String, String> {
        val environment = HashMap<String, String>()

        val runner = ExecutionCommand.runnerOf(executionCommand.runner) ?: return environment

        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_SHELL_CMD__RUNNER_NAME, runner.name)
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_SHELL_CMD__PACKAGE_NAME, currentPackageContext.packageName)
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_SHELL_CMD__SHELL_ID, executionCommand.id.toString())
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_SHELL_CMD__SHELL_NAME, executionCommand.shellName)

        return environment
    }
}
