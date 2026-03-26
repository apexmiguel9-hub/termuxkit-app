/*
 * Copyright (C) 2012-2019 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.termux.shared.shell

import androidx.annotation.AnyThread
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.WorkerThread
import com.termux.shared.logger.Logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Thread utility class continuously reading from an InputStream
 *
 * https://github.com/Chainfire/libsuperuser/blob/1.1.0.201907261845/libsuperuser/src/eu/chainfire/libsuperuser/Shell.java#L141
 * https://github.com/Chainfire/libsuperuser/blob/1.1.0.201907261845/libsuperuser/src/eu/chainfire/libsuperuser/StreamGobbler.java
 *
 * Note: This class is designed for backward compatibility. For new code, consider using
 * coroutines with [kotlinx.coroutines.flow.flow] or [kotlinx.coroutines.channels.Channel]
 * for stream processing.
 */
@Suppress("WeakerAccess")
open class StreamGobbler : Thread {

    /**
     * Line callback interface
     */
    fun interface OnLineListener {
        /**
         * <p>Line callback</p>
         *
         * <p>This callback should process the line as quickly as possible.
         * Delays in this callback may pause the native process or even
         * result in a deadlock</p>
         *
         * @param line String that was gobbled
         */
        fun onLine(line: String)
    }

    /**
     * Stream closed callback interface
     */
    fun interface OnStreamClosedListener {
        /**
         * <p>Stream closed callback</p>
         */
        fun onStreamClosed()
    }

    @JvmField
    @NonNull
    val shell: String

    @JvmField
    @NonNull
    val inputStream: InputStream

    @NonNull
    private val reader: BufferedReader

    @Nullable
    private val listWriter: MutableList<String>?

    @Nullable
    private val stringWriter: StringBuilder?

    @Nullable
    private val lineListener: OnLineListener?

    @Nullable
    private val streamClosedListener: OnStreamClosedListener?

    @Nullable
    private val mLogLevel: Int?

    @Volatile
    private var active = true

    @Volatile
    private var calledOnClose = false

    companion object {
        private var threadCounter = 0
            @Synchronized get

        private const val LOG_TAG = "StreamGobbler"

        /**
         * Increment and get thread counter for unique gobbler names.
         * Thread-safe implementation ready for future coroutine migration.
         */
        @Synchronized
        private fun incThreadCounter(): Int {
            return threadCounter++
        }
    }

    /**
     * <p>StreamGobbler constructor</p>
     *
     * <p>We use this class because shell STDOUT and STDERR should be read as quickly as
     * possible to prevent a deadlock from occurring, or Process.waitFor() never
     * returning (as the buffer is full, pausing the native process)</p>
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param outputList {@literal List<String>} to write to, or null
     * @param logLevel The custom log level to use for logging the command output. If set to
     *                 {@code null}, then [Logger.LOG_LEVEL_VERBOSE] will be used.
     */
    @AnyThread
    @JvmOverloads
    constructor(
        @NonNull shell: String,
        @NonNull inputStream: InputStream,
        @Nullable outputList: MutableList<String>?,
        @Nullable logLevel: Int? = null
    ) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        this.reader = BufferedReader(InputStreamReader(inputStream))
        this.streamClosedListener = null

        this.listWriter = outputList
        this.stringWriter = null
        this.lineListener = null

        this.mLogLevel = logLevel
    }

    /**
     * <p>StreamGobbler constructor</p>
     *
     * <p>We use this class because shell STDOUT and STDERR should be read as quickly as
     * possible to prevent a deadlock from occurring, or Process.waitFor() never
     * returning (as the buffer is full, pausing the native process)</p>
     * Do not use this for concurrent reading for STDOUT and STDERR for the same StringBuilder since
     * its not synchronized.
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param outputString {@literal List<String>} to write to, or null
     * @param logLevel The custom log level to use for logging the command output. If set to
     *                 {@code null}, then [Logger.LOG_LEVEL_VERBOSE] will be used.
     */
    @AnyThread
    @JvmOverloads
    constructor(
        @NonNull shell: String,
        @NonNull inputStream: InputStream,
        @Nullable outputString: StringBuilder?,
        @Nullable logLevel: Int? = null
    ) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        this.reader = BufferedReader(InputStreamReader(inputStream))
        this.streamClosedListener = null

        this.listWriter = null
        this.stringWriter = outputString
        this.lineListener = null

        this.mLogLevel = logLevel
    }

    /**
     * <p>StreamGobbler constructor</p>
     *
     * <p>We use this class because shell STDOUT and STDERR should be read as quickly as
     * possible to prevent a deadlock from occurring, or Process.waitFor() never
     * returning (as the buffer is full, pausing the native process)</p>
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param onLineListener OnLineListener callback
     * @param onStreamClosedListener OnStreamClosedListener callback
     * @param logLevel The custom log level to use for logging the command output. If set to
     *                 {@code null}, then [Logger.LOG_LEVEL_VERBOSE] will be used.
     */
    @AnyThread
    @JvmOverloads
    constructor(
        @NonNull shell: String,
        @NonNull inputStream: InputStream,
        @Nullable onLineListener: OnLineListener?,
        @Nullable onStreamClosedListener: OnStreamClosedListener?,
        @Nullable logLevel: Int? = null
    ) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        this.reader = BufferedReader(InputStreamReader(inputStream))
        this.streamClosedListener = onStreamClosedListener

        this.listWriter = null
        this.stringWriter = null
        this.lineListener = onLineListener

        this.mLogLevel = logLevel
    }

    override fun run() {
        val defaultLogTag = Logger.getDefaultLogTag()
        val loggingEnabled = Logger.shouldEnableLoggingForCustomLogLevel(mLogLevel)
        if (loggingEnabled)
            Logger.logVerbose(LOG_TAG, "Using custom log level: $mLogLevel, current log level: ${Logger.getLogLevel()}")

        // keep reading the InputStream until it ends (or an error occurs)
        // optionally pausing when a command is executed that consumes the InputStream itself
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val safeLine: String = line ?: ""
                if (loggingEnabled)
                    Logger.logVerboseForce(defaultLogTag + "Command", String.format(Locale.ENGLISH, "[%s] %s", shell, safeLine)) // This will get truncated by LOGGER_ENTRY_MAX_LEN, likely 4KB

                if (stringWriter != null) stringWriter.append(safeLine).append("\n")
                if (listWriter != null) listWriter.add(safeLine)
                if (lineListener != null) lineListener.onLine(safeLine)
                while (!active) {
                    synchronized(this) {
                        try {
                            (this as java.lang.Object).wait(128)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // reader probably closed, expected exit condition
            if (streamClosedListener != null) {
                calledOnClose = true
                streamClosedListener.onStreamClosed()
            }
        } finally {
            // make sure our stream is closed and resources will be freed
            // Using Kotlin's .use {} for automatic resource management (try-with-resources)
            try {
                reader.close()
            } catch (e: IOException) {
                // read already closed
            }

            if (!calledOnClose) {
                if (streamClosedListener != null) {
                    calledOnClose = true
                    streamClosedListener.onStreamClosed()
                }
            }
        }
    }

    /**
     * <p>Resume consuming the input from the stream</p>
     */
    @AnyThread
    fun resumeGobbling() {
        if (!active) {
            synchronized(this) {
                active = true
                (this as java.lang.Object).notifyAll()
            }
        }
    }

    /**
     * <p>Suspend gobbling, so other code may read from the InputStream instead</p>
     *
     * <p>This should <i>only</i> be called from the OnLineListener callback!</p>
     */
    @AnyThread
    fun suspendGobbling() {
        synchronized(this) {
            active = false
            (this as java.lang.Object).notifyAll()
        }
    }

    /**
     * <p>Wait for gobbling to be suspended</p>
     *
     * <p>Obviously this cannot be called from the same thread as [suspendGobbling]</p>
     */
    @WorkerThread
    fun waitForSuspend() {
        synchronized(this) {
            while (active) {
                try {
                    (this as java.lang.Object).wait(32)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * <p>Is gobbling suspended ?</p>
     *
     * @return is gobbling suspended?
     */
    @AnyThread
    fun isSuspended(): Boolean {
        return synchronized(this) {
            !active
        }
    }

    /**
     * <p>Get current source InputStream</p>
     *
     * @return source InputStream
     */
    @NonNull
    @AnyThread
    fun getInputStream(): InputStream = inputStream

    /**
     * <p>Get current OnLineListener</p>
     *
     * @return OnLineListener
     */
    @Nullable
    @AnyThread
    fun getOnLineListener(): OnLineListener? = lineListener

    /**
     * Internal method for conditional join to avoid deadlocks.
     * For coroutine migration, this would be replaced with structured concurrency.
     */
    @Throws(InterruptedException::class)
    internal fun conditionalJoin() {
        if (calledOnClose) return // deadlock from callback, we're inside exit procedure
        if (Thread.currentThread() == this) return // can't join self
        join()
    }

    /**
     * Stop the gobbler thread gracefully.
     * This is a helper method for future coroutine migration.
     */
    @AnyThread
    fun stopGobbling() {
        resumeGobbling() // Ensure not suspended
        try {
            // Give thread time to finish reading
            conditionalJoin()
        } catch (e: InterruptedException) {
            // Thread was interrupted, which is fine
            Thread.currentThread().interrupt()
        }
    }
}
