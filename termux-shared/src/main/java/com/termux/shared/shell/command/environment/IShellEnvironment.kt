package com.termux.shared.shell.command.environment

import android.content.Context
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.shell.command.ExecutionCommand
import java.util.HashMap

/**
 * Interface for shell environment implementations.
 *
 * https://manpages.debian.org/testing/manpages/environ.7.en.html
 * https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html
 */
interface IShellEnvironment {

    /**
     * Get shell environment for the given context.
     *
     * @param currentPackageContext The [Context] for operations.
     * @param isFailSafe If `true`, then fail-safe mode is enabled.
     * @return Returns the [HashMap] containing the environment variables.
     */
    @NonNull
    fun getEnvironment(@NonNull currentPackageContext: Context, isFailSafe: Boolean): HashMap<String, String>

    /**
     * Get the default working directory path.
     *
     * @return Returns the default working directory path.
     */
    @NonNull
    fun getDefaultWorkingDirectoryPath(): String

    /**
     * Get the default bin path.
     *
     * @return Returns the default bin path.
     */
    @NonNull
    fun getDefaultBinPath(): String

    /**
     * Setup shell command arguments for the executable.
     *
     * @param executable The executable to run.
     * @param arguments The arguments to pass to the executable.
     * @return Returns the final process arguments.
     */
    @NonNull
    fun setupShellCommandArguments(@NonNull executable: String, @Nullable arguments: Array<String>?): Array<String>

    /**
     * Setup shell command environment to be used for commands.
     *
     * @param currentPackageContext The [Context] for operations.
     * @param executionCommand The [ExecutionCommand] for which to set environment.
     * @return Returns the shell environment.
     */
    @NonNull
    fun setupShellCommandEnvironment(@NonNull currentPackageContext: Context, @NonNull executionCommand: ExecutionCommand): HashMap<String, String>
}
