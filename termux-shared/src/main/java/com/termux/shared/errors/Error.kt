package com.termux.shared.errors

import android.content.Context
import androidx.annotation.NonNull
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable
import java.util.ArrayList
import java.util.Collections

class Error : Serializable {

    /** The optional error label. */
    var label: String? = null
    /** The error type. */
    var type: String = ""
    /** The error code. */
    var code: Int = 0
    /** The error message. */
    var message: String? = null
    /** The error exceptions. */
    var throwablesList: List<Throwable> = ArrayList()

    companion object {
        private const val LOG_TAG = "Error"

        /**
         * Log the [Error] and show a toast for the minimal [String] for the [Error].
         *
         * @param context The [Context] for operations.
         * @param logTag The log tag to use for logging.
         * @param error The [Error] to convert.
         */
        @JvmStatic
        fun logErrorAndShowToast(context: Context?, logTag: String?, error: Error?) {
            if (error == null) return
            error.logErrorAndShowToast(context, logTag)
        }

        /**
         * Get a log friendly [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getErrorLogString(error: Error?): String {
            if (error == null) return "null"
            return error.getErrorLogString()
        }

        /**
         * Get a minimal log friendly [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the log friendly [String].
         */
        @JvmStatic
        fun getMinimalErrorLogString(error: Error?): String {
            if (error == null) return "null"
            return error.getMinimalErrorLogString()
        }

        /**
         * Get a minimal [String] for [Error] error parameters.
         *
         * @param error The [Error] to convert.
         * @return Returns the [String].
         */
        @JvmStatic
        fun getMinimalErrorString(error: Error?): String {
            if (error == null) return "null"
            return error.getMinimalErrorString()
        }

        /**
         * Get a markdown [String] for [Error].
         *
         * @param error The [Error] to convert.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getErrorMarkdownString(error: Error?): String {
            if (error == null) return "null"
            return error.getErrorMarkdownString()
        }
    }

    constructor() {
        initError(null, null, null, null)
    }

    constructor(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(type, code, message, throwablesList)
    }

    constructor(type: String?, code: Int?, message: String?, throwable: Throwable?) {
        initError(type, code, message, if (throwable != null) listOf(throwable) else null)
    }

    constructor(type: String?, code: Int?, message: String?) {
        initError(type, code, message, null)
    }

    constructor(code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(null, code, message, throwablesList)
    }

    constructor(code: Int?, message: String?, throwable: Throwable?) {
        initError(null, code, message, if (throwable != null) listOf(throwable) else null)
    }

    constructor(code: Int?, message: String?) {
        initError(null, code, message, null)
    }

    constructor(message: String?, throwable: Throwable?) {
        initError(null, null, message, if (throwable != null) listOf(throwable) else null)
    }

    constructor(message: String?, throwablesList: List<Throwable>?) {
        initError(null, null, message, throwablesList)
    }

    constructor(message: String?) {
        initError(null, null, message, null)
    }

    private fun initError(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        this.type = if (!type.isNullOrEmpty()) type else Errno.TYPE
        this.code = if (code != null && code > Errno.ERRNO_SUCCESS.code) code else Errno.ERRNO_SUCCESS.code
        this.message = message
        this.throwablesList = throwablesList ?: ArrayList()
    }

    fun setLabel(label: String?): Error {
        this.label = label
        return this
    }

    fun getLabel(): String? {
        return label
    }

    fun getType(): String {
        return type
    }

    fun getCode(): Int {
        return code
    }

    fun getMessage(): String? {
        return message
    }

    fun prependMessage(message: String?) {
        if (message != null && isStateFailed())
            this.message = message + this.message
    }

    fun appendMessage(message: String?) {
        if (message != null && isStateFailed())
            this.message = this.message + message
    }

    fun getThrowablesList(): List<Throwable> {
        return Collections.unmodifiableList(throwablesList)
    }

    @Synchronized
    fun setStateFailed(error: Error): Boolean {
        return setStateFailed(error.type, error.code, error.message, null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwable: Throwable?): Boolean {
        return setStateFailed(error.type, error.code, error.message, if (throwable != null) listOf(throwable) else null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(error.type, error.code, error.message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?): Boolean {
        return setStateFailed(this.type, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable?): Boolean {
        return setStateFailed(this.type, code, message, if (throwable != null) listOf(throwable) else null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(this.type, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        this.message = message
        this.throwablesList = throwablesList ?: ArrayList()

        if (!type.isNullOrEmpty())
            this.type = type

        if (code > Errno.ERRNO_SUCCESS.code) {
            this.code = code
            return true
        } else {
            Logger.logWarn(LOG_TAG, "Ignoring invalid error code value \"$code\". Force setting it to RESULT_CODE_FAILED \"${Errno.ERRNO_FAILED.code}\"")
            this.code = Errno.ERRNO_FAILED.code
            return false
        }
    }

    fun isStateFailed(): Boolean {
        return code > Errno.ERRNO_SUCCESS.code
    }

    @NonNull
    override fun toString(): String {
        return getErrorLogString(this)
    }

    fun logErrorAndShowToast(context: Context?, logTag: String?) {
        Logger.logErrorExtended(logTag ?: "", getErrorLogString())
        Logger.showToast(context, getMinimalErrorLogString(), true)
    }

    fun getErrorLogString(): String {
        val logString = StringBuilder()

        logString.append(codeString)
        logString.append("\n").append(typeAndMessageLogString)
        if (throwablesList.isNotEmpty())
            logString.append("\n").append(stackTracesLogString)

        return logString.toString()
    }

    fun getMinimalErrorLogString(): String {
        val logString = StringBuilder()

        logString.append(codeString)
        logString.append(typeAndMessageLogString)

        return logString.toString()
    }

    fun getMinimalErrorString(): String {
        val logString = StringBuilder()

        logString.append("($code) ")
        logString.append(type).append(": ").append(message)

        return logString.toString()
    }

    fun getErrorMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Error Code", code, "-"))
        markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry(
            if (Errno.TYPE == type) "Error Message" else "Error Message ($type)", message, "-"))
        if (throwablesList.isNotEmpty())
            markdownString.append("\n\n").append(stackTracesMarkdownString)

        return markdownString.toString()
    }

    val errorLogString: String
        get() = getErrorLogString()

    val minimalErrorLogString: String
        get() = getMinimalErrorLogString()

    val minimalErrorString: String
        get() = getMinimalErrorString()

    val errorMarkdownString: String
        get() = getErrorMarkdownString()

    val codeString: String
        get() = Logger.getSingleLineLogStringEntry("Error Code", code, "-")

    val typeAndMessageLogString: String
        get() = Logger.getMultiLineLogStringEntry(if (Errno.TYPE == type) "Error Message" else "Error Message ($type)", message, "-")

    val stackTracesLogString: String
        get() = Logger.getStackTracesString("StackTraces:", Logger.getStackTracesStringArray(throwablesList))

    val stackTracesMarkdownString: String
        get() = Logger.getStackTracesMarkdownString("StackTraces", Logger.getStackTracesStringArray(throwablesList))
}
