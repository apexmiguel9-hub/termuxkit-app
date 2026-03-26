package com.termux.shared.shell.command.environment

/**
 * Represents a shell environment variable.
 *
 * @param name The name for environment variable.
 * @param value The value for environment variable.
 * @param escaped If environment variable [value] is already escaped.
 */
data class ShellEnvironmentVariable(
    var name: String,
    var value: String,
    var escaped: Boolean = false
) : Comparable<ShellEnvironmentVariable> {

    override fun compareTo(other: ShellEnvironmentVariable): Int {
        return this.name.compareTo(other.name)
    }
}
