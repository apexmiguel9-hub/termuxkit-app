package com.termux.shared.shell.command.result
import java.util.*

import androidx.annotation.NonNull
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.errors.Errno
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable

class ResultData : Serializable {

    /** The stdout of command. */
    @JvmField
    val stdout = StringBuilder()

    /** The stderr of command. */
    @JvmField
    val stderr = StringBuilder()

    /** The exit code of command. */
    @JvmField
    var exitCode: Int? = null

    /** The internal errors list of command. */
    @JvmField
    var errorsList: MutableList<Error> = ArrayList()


    fun clearStdout() {
        stdout.setLength(0)
    }

    fun prependStdout(message: String): StringBuilder {
        return stdout.insert(0, message)
    }

    fun prependStdoutLn(message: String): StringBuilder {
        return stdout.insert(0, "$message\n")
    }

    fun appendStdout(message: String): StringBuilder {
        return stdout.append(message)
    }

    fun appendStdoutLn(message: String): StringBuilder {
        return stdout.append(message).append("\n")
    }


    fun clearStderr() {
        stderr.setLength(0)
    }

    fun prependStderr(message: String): StringBuilder {
        return stderr.insert(0, message)
    }

    fun prependStderrLn(message: String): StringBuilder {
        return stderr.insert(0, "$message\n")
    }

    fun appendStderr(message: String): StringBuilder {
        return stderr.append(message)
    }

    fun appendStderrLn(message: String): StringBuilder {
        return stderr.append(message).append("\n")
    }


    @Synchronized
    fun setStateFailed(error: Error): Boolean {
        return setStateFailed(error.type, error.code, error.message, null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwable: Throwable?): Boolean {
        return setStateFailed(error.type, error.code, error.message, listOfNotNull(throwable))
    }

    @Synchronized
    fun setStateFailed(error: Error, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(error.type, error.code, error.message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?): Boolean {
        return setStateFailed(null, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable?): Boolean {
        return setStateFailed(null, code, message, listOfNotNull(throwable))
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(null, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        if (errorsList == null)
            errorsList = ArrayList()

        val error = Error()
        errorsList.add(error)

        return error.setStateFailed(type, code, message, throwablesList)
    }

    fun isStateFailed(): Boolean {
        if (errorsList != null) {
            for (error in errorsList)
                if (error.isStateFailed())
                    return true
        }

        return false
    }

    fun getErrCode(): Int {
        return if (errorsList != null && errorsList.size > 0)
            errorsList[errorsList.size - 1].code
        else
            Errno.ERRNO_SUCCESS.code
    }


    @NonNull
    override fun toString(): String {
        return getResultDataLogString(this, true)
    }

    /**
     * Get a log friendly [String] for [ResultData] parameters.
     *
     * @param resultData The [ResultData] to convert.
     * @param logStdoutAndStderr Set to `true` if [stdout] and [stderr] should be logged.
     * @return Returns the log friendly [String].
     */
    fun getStdoutLogString(): String {
        return if (stdout.toString().isEmpty())
            Logger.getSingleLineLogStringEntry("Stdout", null, "-")
        else
            Logger.getMultiLineLogStringEntry("Stdout", DataUtils.getTruncatedCommandOutput(stdout.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, false, false, true), "-")
    }

    fun getStderrLogString(): String {
        return if (stderr.toString().isEmpty())
            Logger.getSingleLineLogStringEntry("Stderr", null, "-")
        else
            Logger.getMultiLineLogStringEntry("Stderr", DataUtils.getTruncatedCommandOutput(stderr.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, false, false, true), "-")
    }

    fun getExitCodeLogString(): String {
        return Logger.getSingleLineLogStringEntry("Exit Code", exitCode, "-")
    }

    /**
     * Get a markdown [String] for [ResultData].
     *
     * @return Returns the markdown [String].
     */
    fun getResultDataMarkdownString(): String {
        val markdownString = StringBuilder()

        if (stdout.toString().isEmpty())
            markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Stdout", null, "-"))
        else
            markdownString.append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stdout", stdout.toString(), "-"))

        if (stderr.toString().isEmpty())
            markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Stderr", null, "-"))
        else
            markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stderr", stderr.toString(), "-"))

        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Exit Code", exitCode, "-"))

        markdownString.append("\n\n").append(getErrorsListMarkdownString())

        return markdownString.toString()
    }

    fun getErrorsListMarkdownString(): String {
        val markdownString = StringBuilder()

        if (errorsList != null) {
            for (error in errorsList) {
                if (error.isStateFailed()) {
                    if (markdownString.toString().isNotEmpty())
                        markdownString.append("\n")
                    markdownString.append(Error.getErrorMarkdownString(error))
                }
            }
        }

        return markdownString.toString()
    }

    fun getErrorsListMinimalString(): String {
        val minimalString = StringBuilder()

        if (errorsList != null) {
            for (error in errorsList) {
                if (error.isStateFailed()) {
                    if (minimalString.toString().isNotEmpty())
                        minimalString.append("\n")
                    minimalString.append(Error.getMinimalErrorString(error))
                }
            }
        }

        return minimalString.toString()
    }

    companion object {
        @JvmStatic
        fun getResultDataLogString(resultData: ResultData?, logStdoutAndStderr: Boolean): String {
            if (resultData == null) return "null"

            val logString = StringBuilder()

            if (logStdoutAndStderr) {
                logString.append("\n").append(resultData.getStdoutLogString())
                logString.append("\n").append(resultData.getStderrLogString())
            }
            logString.append("\n").append(resultData.getExitCodeLogString())

            logString.append("\n\n").append(getErrorsListLogString(resultData))

            return logString.toString()
        }

        @JvmStatic
        fun getErrorsListLogString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val logString = StringBuilder()

            if (resultData.errorsList != null) {
                for (error in resultData.errorsList) {
                    if (error.isStateFailed()) {
                        if (logString.toString().isNotEmpty())
                            logString.append("\n")
                        logString.append(Error.getErrorLogString(error))
                    }
                }
            }

            return logString.toString()
        }

        @JvmStatic
        fun getResultDataMarkdownString(resultData: ResultData?): String {
            return resultData?.getResultDataMarkdownString() ?: "null"
        }

        @JvmStatic
        fun getErrorsListMarkdownString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val markdownString = StringBuilder()

            if (resultData.errorsList != null) {
                for (error in resultData.errorsList) {
                    if (error.isStateFailed()) {
                        if (markdownString.toString().isNotEmpty())
                            markdownString.append("\n")
                        markdownString.append(Error.getErrorMarkdownString(error))
                    }
                }
            }

            return markdownString.toString()
        }

        @JvmStatic
        fun getErrorsListMinimalString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            val minimalString = StringBuilder()

            if (resultData.errorsList != null) {
                for (error in resultData.errorsList) {
                    if (error.isStateFailed()) {
                        if (minimalString.toString().isNotEmpty())
                            minimalString.append("\n")
                        minimalString.append(Error.getMinimalErrorString(error))
                    }
                }
            }

            return minimalString.toString()
        }
    }
}
