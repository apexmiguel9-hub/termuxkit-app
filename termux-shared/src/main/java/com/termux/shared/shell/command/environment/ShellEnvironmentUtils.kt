package com.termux.shared.shell.command.environment

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.List
import java.util.Map

object ShellEnvironmentUtils {

    private const val LOG_TAG = "ShellEnvironmentUtils"

    /**
     * Convert environment [HashMap] to `environ` [List]<[String]>.
     *
     * The items in the environ will have the format `name=value`.
     *
     * Check [isValidEnvironmentVariableName] and [isValidEnvironmentVariableValue]
     * for valid variable names and values.
     *
     * https://manpages.debian.org/testing/manpages/environ.7.en.html
     * https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html
     */
    @JvmStatic
    @NonNull
    fun convertEnvironmentToEnviron(@NonNull environmentMap: HashMap<String, String>): java.util.List<String> {
        val environmentList = ArrayList<String>(environmentMap.size)
        for ((name, value) in environmentMap) {
            if (isValidEnvironmentVariableNameValuePair(name, value, true))
                environmentList.add("$name=$value")
        }
        return environmentList as java.util.List<String>
    }

    /**
     * Convert environment [HashMap] to [String] where each item equals "key=value".
     */
    @JvmStatic
    @NonNull
    fun convertEnvironmentToDotEnvFile(@NonNull environmentMap: HashMap<String, String>): String {
        return convertEnvironmentToDotEnvFile(convertEnvironmentMapToEnvironmentVariableList(environmentMap))
    }

    /**
     * Convert environment [HashMap] to `.env` file [String].
     *
     * The items in the `.env` file have the format `export name="value"`.
     *
     * If the [ShellEnvironmentVariable.escaped] is set to `true`, then
     * [ShellEnvironmentVariable.value] will be considered to be a literal value that has
     * already been escaped by the caller, otherwise all the `"`, `$`, `\` in the value will be escaped
     * with a backslash `\`, like `\"`. Note that if `$` is escaped and if its part of variable,
     * then variable expansion will not happen if `.env` file is sourced.
     *
     * The `\` at the end of a value line means line continuation. Value can contain newline characters.
     *
     * Check [isValidEnvironmentVariableName] and [isValidEnvironmentVariableValue]
     * for valid variable names and values.
     *
     * https://github.com/ko1nksm/shdotenv#env-file-syntax
     * https://github.com/ko1nksm/shdotenv/blob/main/docs/specification.md
     */
    @JvmStatic
    @NonNull
    fun convertEnvironmentToDotEnvFile(@NonNull environmentList: List<ShellEnvironmentVariable>): String {
        val environment = StringBuilder()
        Collections.sort(environmentList.toMutableList())
        for (variable in environmentList) {
            if (isValidEnvironmentVariableNameValuePair(variable.name, variable.value, true) && variable.value != null) {
                environment.append("export ").append(variable.name).append("=\"")
                    .append(if (variable.escaped) variable.value else variable.value.replace("([\"`\\\\$])".toRegex(), "\\\\$1"))
                    .append("\"\n")
            }
        }
        return environment.toString()
    }

    /**
     * Convert environment [HashMap] to [List]<[ShellEnvironmentVariable]>. Each item
     * will have its [ShellEnvironmentVariable.escaped] set to `false`.
     */
    @JvmStatic
    @NonNull
    fun convertEnvironmentMapToEnvironmentVariableList(@NonNull environmentMap: HashMap<String, String>): java.util.List<ShellEnvironmentVariable> {
        val environmentList = ArrayList<ShellEnvironmentVariable>()
        for ((name, value) in environmentMap) {
            environmentList.add(ShellEnvironmentVariable(name, value, false))
        }
        return environmentList as java.util.List<ShellEnvironmentVariable>
    }

    /**
     * Check if environment variable name and value pair is valid. Errors will be logged if
     * `logErrors` is `true`.
     *
     * Check [isValidEnvironmentVariableName] and [isValidEnvironmentVariableValue]
     * for valid variable names and values.
     */
    fun isValidEnvironmentVariableNameValuePair(@Nullable name: String?, @Nullable value: String?, logErrors: Boolean): Boolean {
        if (!isValidEnvironmentVariableName(name)) {
            if (logErrors)
                Logger.logErrorPrivate(LOG_TAG, "Invalid environment variable name. name=`$name`, value=`$value`")
            return false
        }

        if (!isValidEnvironmentVariableValue(value)) {
            if (logErrors)
                Logger.logErrorPrivate(LOG_TAG, "Invalid environment variable value. name=`$name`, value=`$value`")
            return false
        }

        return true
    }

    /**
     * Check if environment variable name is valid. It must not be `null` and must not contain
     * the null byte ('\u0000') and must only contain alphanumeric and underscore characters and must not
     * start with a digit.
     */
    fun isValidEnvironmentVariableName(@Nullable name: String?): Boolean {
        return name != null && !name.contains("\u0000") && name.matches("[a-zA-Z_][a-zA-Z0-9_]*".toRegex())
    }

    /**
     * Check if environment variable value is valid. It must not be `null` and must not contain
     * the null byte ('\u0000').
     */
    fun isValidEnvironmentVariableValue(@Nullable value: String?): Boolean {
        return value != null && !value.contains("\u0000")
    }

    /**
     * Put value in environment if variable exists in [System] environment.
     */
    fun putToEnvIfInSystemEnv(@NonNull environment: HashMap<String, String>, @NonNull name: String) {
        val value = System.getenv(name)
        if (value != null) {
            environment[name] = value
        }
    }

    /**
     * Put [String] value in environment if value set.
     */
    fun putToEnvIfSet(@NonNull environment: HashMap<String, String>, @NonNull name: String, @Nullable value: String?) {
        if (value != null) {
            environment[name] = value
        }
    }

    /**
     * Put [Boolean] value "true" or "false" in environment if value set.
     */
    fun putToEnvIfSet(@NonNull environment: HashMap<String, String>, @NonNull name: String, @Nullable value: Boolean?) {
        if (value != null) {
            environment[name] = value.toString()
        }
    }

    /**
     * Create HOME directory in environment [Map] if set.
     */
    fun createHomeDir(@NonNull environment: HashMap<String, String>) {
        val homeDirectory = environment[UnixShellEnvironment.ENV_HOME]
        if (homeDirectory != null && homeDirectory.isNotEmpty()) {
            val error = FileUtils.createDirectoryFile("shell home", homeDirectory)
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Failed to create shell home directory\n$error")
            }
        }
    }
}
