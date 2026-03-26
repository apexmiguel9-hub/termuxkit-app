package com.termux.shared.net.socket.local
import java.util.*

import android.content.Context
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.errors.Error
import com.termux.shared.jni.models.JniResult
import com.termux.shared.logger.Logger

/**
 * Manager for an AF_UNIX/SOCK_STREAM local server.
 *
 * Usage:
 * 1. Implement the [ILocalSocketManager] that will receive call backs from the server including
 *    when client connects via [ILocalSocketManager.onClientAccepted].
 *    Optionally extend the [LocalSocketManagerClientBase] class that provides base implementation.
 * 2. Create a [LocalSocketRunConfig] instance with the run config of the server.
 * 3. Create a [LocalSocketManager] instance and call [start].
 * 4. Stop server if needed with a call to [stop].
 */
class LocalSocketManager(
    @NonNull val context: Context,
    @NonNull val mLocalSocketRunConfig: LocalSocketRunConfig
) {

    companion object {
        const val LOG_TAG = "LocalSocketManager"

        /** The native JNI local socket library. */
        protected const val LOCAL_SOCKET_LIBRARY = "local-socket"

        /** Whether [LOCAL_SOCKET_LIBRARY] has been loaded or not. */
        protected var localSocketLibraryLoaded = false

        // Native methods
        @Nullable
        private external fun createServerSocketNative(
            @NonNull serverTitle: String,
            @NonNull path: ByteArray,
            backlog: Int
        ): JniResult?

        @Nullable
        private external fun closeSocketNative(@NonNull serverTitle: String, fd: Int): JniResult?

        @Nullable
        private external fun acceptNative(@NonNull serverTitle: String, fd: Int): JniResult?

        @Nullable
        private external fun readNative(
            @NonNull serverTitle: String,
            fd: Int,
            @NonNull data: ByteArray,
            deadline: Long
        ): JniResult?

        @Nullable
        private external fun sendNative(
            @NonNull serverTitle: String,
            fd: Int,
            @NonNull data: ByteArray,
            deadline: Long
        ): JniResult?

        @Nullable
        private external fun availableNative(@NonNull serverTitle: String, fd: Int): JniResult?

        private external fun setSocketReadTimeoutNative(
            @NonNull serverTitle: String,
            fd: Int,
            timeout: Int
        ): JniResult?

        @Nullable
        private external fun setSocketSendTimeoutNative(
            @NonNull serverTitle: String,
            fd: Int,
            timeout: Int
        ): JniResult?

        @Nullable
        private external fun getPeerCredNative(
            @NonNull serverTitle: String,
            fd: Int,
            peerCred: PeerCred
        ): JniResult?

        /*
         Note: Exceptions thrown from JNI must be caught with Throwable class instead of Exception,
         otherwise exception will be sent to UncaughtExceptionHandler of the thread.
        */

        /**
         * Creates an AF_UNIX/SOCK_STREAM local server socket at `path`, with the specified backlog.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param path The path at which to create the socket.
         *             For a filesystem socket, this must be an absolute path to the socket file.
         *             For an abstract namespace socket, the first byte must be a null `\0` character.
         *             Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
         * @param backlog The maximum length to which the queue of pending connections for the socket
         *                may grow. This value may be ignored or may not have one-to-one mapping
         *                in kernel implementation. Value must be greater than 0.
         * @return Returns the [JniResult]. If server creation was successful, then
         * [JniResult.retval] will be 0 and [JniResult.intData] will contain the server socket
         * fd.
         */
        @JvmStatic
        @Nullable
        fun createServerSocket(@NonNull serverTitle: String, @NonNull path: ByteArray, backlog: Int): JniResult? {
            return try {
                createServerSocketNative(serverTitle, path, backlog)
            } catch (t: Throwable) {
                val message = "Exception in createServerSocketNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Closes the socket with fd.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @return Returns the [JniResult]. If closing socket was successful, then
         * [JniResult.retval] will be 0.
         */
        @JvmStatic
        @Nullable
        fun closeSocket(@NonNull serverTitle: String, fd: Int): JniResult? {
            return try {
                closeSocketNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in closeSocketNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Accepts a connection on the supplied server socket fd.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The server socket fd.
         * @return Returns the [JniResult]. If accepting socket was successful, then
         * [JniResult.retval] will be 0 and [JniResult.intData] will contain the client socket
         * fd.
         */
        @JvmStatic
        @Nullable
        fun accept(@NonNull serverTitle: String, fd: Int): JniResult? {
            return try {
                acceptNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in acceptNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Attempts to read up to data buffer length bytes from file descriptor fd into the data buffer.
         * On success, the number of bytes read is returned (zero indicates end of file).
         * It is not an error if bytes read is smaller than the number of bytes requested; this may happen
         * for example because fewer bytes are actually available right now (maybe because we were close
         * to end-of-file, or because we are reading from a pipe), or because read() was interrupted by
         * a signal. On error, the [JniResult.errno] and [JniResult.errmsg] will be set.
         *
         * If while reading the deadline elapses but all the data has not been read, the call will fail.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @param data The data buffer to read bytes into.
         * @param deadline The deadline milliseconds since epoch.
         * @return Returns the [JniResult]. If reading was successful, then [JniResult.retval]
         * will be 0 and [JniResult.intData] will contain the bytes read.
         */
        @JvmStatic
        @Nullable
        fun read(@NonNull serverTitle: String, fd: Int, @NonNull data: ByteArray, deadline: Long): JniResult? {
            return try {
                readNative(serverTitle, fd, data, deadline)
            } catch (t: Throwable) {
                val message = "Exception in readNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Attempts to send data buffer to the file descriptor. On error, the [JniResult.errno] and
         * [JniResult.errmsg] will be set.
         *
         * If while sending the deadline elapses but all the data has not been sent, the call will fail.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @param data The data buffer containing bytes to send.
         * @param deadline The deadline milliseconds since epoch.
         * @return Returns the [JniResult]. If sending was successful, then [JniResult.retval]
         * will be 0.
         */
        @JvmStatic
        @Nullable
        fun send(@NonNull serverTitle: String, fd: Int, @NonNull data: ByteArray, deadline: Long): JniResult? {
            return try {
                sendNative(serverTitle, fd, data, deadline)
            } catch (t: Throwable) {
                val message = "Exception in sendNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Gets the number of bytes available to read on the socket.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @return Returns the [JniResult]. If checking availability was successful, then
         * [JniResult.retval] will be 0 and [JniResult.intData] will contain the bytes available.
         */
        @JvmStatic
        @Nullable
        fun available(@NonNull serverTitle: String, fd: Int): JniResult? {
            return try {
                availableNative(serverTitle, fd)
            } catch (t: Throwable) {
                val message = "Exception in availableNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Set receiving (SO_RCVTIMEO) timeout in milliseconds for socket.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @param timeout The timeout value in milliseconds.
         * @return Returns the [JniResult]. If setting timeout was successful, then
         * [JniResult.retval] will be 0.
         */
        @JvmStatic
        @Nullable
        fun setSocketReadTimeout(@NonNull serverTitle: String, fd: Int, timeout: Int): JniResult? {
            return try {
                setSocketReadTimeoutNative(serverTitle, fd, timeout)
            } catch (t: Throwable) {
                val message = "Exception in setSocketReadTimeoutNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Set sending (SO_SNDTIMEO) timeout in milliseconds for fd.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @param timeout The timeout value in milliseconds.
         * @return Returns the [JniResult]. If setting timeout was successful, then
         * [JniResult.retval] will be 0.
         */
        @JvmStatic
        @Nullable
        fun setSocketSendTimeout(@NonNull serverTitle: String, fd: Int, timeout: Int): JniResult? {
            return try {
                setSocketSendTimeoutNative(serverTitle, fd, timeout)
            } catch (t: Throwable) {
                val message = "Exception in setSocketSendTimeoutNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /**
         * Get the [PeerCred] for the socket.
         *
         * @param serverTitle The server title used for logging and errors.
         * @param fd The socket fd.
         * @param peerCred The [PeerCred] object that should be filled.
         * @return Returns the [JniResult]. If setting timeout was successful, then
         * [JniResult.retval] will be 0.
         */
        @JvmStatic
        @Nullable
        fun getPeerCred(@NonNull serverTitle: String, fd: Int, peerCred: PeerCred): JniResult? {
            return try {
                getPeerCredNative(serverTitle, fd, peerCred)
            } catch (t: Throwable) {
                val message = "Exception in getPeerCredNative()"
                Logger.logStackTraceWithMessage(LOG_TAG, message, t)
                JniResult(message, t)
            }
        }

        /** Get an error log [String] for the [LocalSocketManager]. */
        @JvmStatic
        fun getErrorLogString(
            @NonNull error: Error,
            @NonNull localSocketRunConfig: LocalSocketRunConfig,
            @Nullable clientSocket: LocalClientSocket?
        ): String {
            val logString = StringBuilder()

            logString.append(localSocketRunConfig.getTitle()).append(" Socket Server Error:\n")
            logString.append(error.getErrorLogString())
            logString.append("\n\n\n")

            logString.append(localSocketRunConfig.getLogString())

            if (clientSocket != null) {
                logString.append("\n\n\n")
                logString.append(clientSocket.getLogString())
            }

            return logString.toString()
        }

        /** Get an error markdown [String] for the [LocalSocketManager]. */
        @JvmStatic
        fun getErrorMarkdownString(
            @NonNull error: Error,
            @NonNull localSocketRunConfig: LocalSocketRunConfig,
            @Nullable clientSocket: LocalClientSocket?
        ): String {
            val markdownString = StringBuilder()

            markdownString.append(error.getErrorMarkdownString())
            markdownString.append("\n##\n\n\n")

            markdownString.append(localSocketRunConfig.getMarkdownString())

            return markdownString.toString()
        }
    }

    /** The [LocalServerSocket] for the [LocalSocketManager]. */
    @NonNull
    val serverSocket = LocalServerSocket(this)

    /** The [ILocalSocketManager] client for the [LocalSocketManager]. */
    @NonNull
    val mLocalSocketManagerClient: ILocalSocketManager = mLocalSocketRunConfig.getLocalSocketManagerClient()

    /** The [Thread.UncaughtExceptionHandler] used for client thread started by [LocalSocketManager]. */
    @NonNull
    val mLocalSocketManagerClientThreadUEH: Thread.UncaughtExceptionHandler

    /** Whether the [LocalServerSocket] managed by [LocalSocketManager] in running or not. */
    var isRunning = false
        private set

    init {
        mLocalSocketManagerClientThreadUEH = getLocalSocketManagerClientThreadUEHOrDefault()
    }

    /**
     * Create the [LocalServerSocket] and start listening for new [LocalClientSocket].
     */
    @Synchronized
    fun start(): Error? {
        Logger.logDebugExtended(LOG_TAG, "start\n$mLocalSocketRunConfig")

        if (!localSocketLibraryLoaded) {
            try {
                Logger.logDebug(LOG_TAG, "Loading \"$LOCAL_SOCKET_LIBRARY\" library")
                System.loadLibrary(LOCAL_SOCKET_LIBRARY)
                localSocketLibraryLoaded = true
            } catch (t: Throwable) {
                val error = LocalSocketErrno.ERRNO_START_LOCAL_SOCKET_LIB_LOAD_FAILED_WITH_EXCEPTION.getError(
                    t, LOCAL_SOCKET_LIBRARY, t.message
                )
                Logger.logErrorExtended(LOG_TAG, error.errorLogString)
                return error
            }
        }

        isRunning = true
        return serverSocket.start()
    }

    /**
     * Stop the [LocalServerSocket] and stop listening for new [LocalClientSocket].
     */
    @Synchronized
    fun stop(): Error? {
        return if (isRunning) {
            Logger.logDebugExtended(LOG_TAG, "stop\n$mLocalSocketRunConfig")
            isRunning = false
            serverSocket.stop()
        } else {
            null
        }
    }

    /** Wrapper for [onError] for `null` [LocalClientSocket]. */
    fun onError(@NonNull error: Error) {
        onError(null, error)
    }

    /** Wrapper to call [ILocalSocketManager.onError] in a new thread. */
    fun onError(@Nullable clientSocket: LocalClientSocket?, @NonNull error: Error) {
        startLocalSocketManagerClientThread {
            mLocalSocketManagerClient.onError(this, clientSocket, error)
        }
    }

    /** Wrapper to call [ILocalSocketManager.onDisallowedClientConnected] in a new thread. */
    fun onDisallowedClientConnected(@NonNull clientSocket: LocalClientSocket, @NonNull error: Error) {
        startLocalSocketManagerClientThread {
            mLocalSocketManagerClient.onDisallowedClientConnected(this, clientSocket, error)
        }
    }

    /** Wrapper to call [ILocalSocketManager.onClientAccepted] in a new thread. */
    fun onClientAccepted(@NonNull clientSocket: LocalClientSocket) {
        startLocalSocketManagerClientThread {
            mLocalSocketManagerClient.onClientAccepted(this, clientSocket)
        }
    }

    /** All client accept logic must be run on separate threads so that incoming client acceptance is not blocked. */
    fun startLocalSocketManagerClientThread(@NonNull runnable: Runnable) {
        val thread = Thread(runnable)
        thread.setUncaughtExceptionHandler(getLocalSocketManagerClientThreadUEH())
        try {
            thread.start()
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "LocalSocketManagerClientThread start failed", e)
        }
    }

    /** Get [context]. */
    fun getContext(): Context = context

    /** Get [mLocalSocketRunConfig]. */
    fun getLocalSocketRunConfig(): LocalSocketRunConfig = mLocalSocketRunConfig

    /** Get [mLocalSocketManagerClient]. */
    fun getLocalSocketManagerClient(): ILocalSocketManager = mLocalSocketManagerClient

    /** Get [serverSocket]. */
    fun getServerSocket(): LocalServerSocket = serverSocket

    /** Get [mLocalSocketManagerClientThreadUEH]. */
    fun getLocalSocketManagerClientThreadUEH(): Thread.UncaughtExceptionHandler = mLocalSocketManagerClientThreadUEH

    /**
     * Get [Thread.UncaughtExceptionHandler] returned by call to
     * [ILocalSocketManager.getLocalSocketManagerClientThreadUEH]
     * or the default handler that just logs the exception.
     */
    protected fun getLocalSocketManagerClientThreadUEHOrDefault(): Thread.UncaughtExceptionHandler {
        var uncaughtExceptionHandler: Thread.UncaughtExceptionHandler? =
            mLocalSocketManagerClient.getLocalSocketManagerClientThreadUEH(this)
        if (uncaughtExceptionHandler == null) {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Uncaught exception for $t in ${mLocalSocketRunConfig.getTitle()} server",
                    e
                )
            }
        }
        return uncaughtExceptionHandler
    }

    /** Get [isRunning]. */
    fun isRunning(): Boolean = isRunning
}
