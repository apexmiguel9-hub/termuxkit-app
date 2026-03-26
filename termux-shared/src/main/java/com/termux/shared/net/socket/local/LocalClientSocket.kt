package com.termux.shared.net.socket.local
import java.util.*

import androidx.annotation.NonNull
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.jni.models.JniResult
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/** The client socket for [LocalSocketManager]. */
class LocalClientSocket internal constructor(
    @NonNull val mLocalSocketManager: LocalSocketManager,
    fd: Int,
    @NonNull val peerCred: PeerCred
) : Closeable {

    companion object {
        const val LOG_TAG = "LocalClientSocket"

        /** Close client socket that exists at fd. */
        @JvmStatic
        fun closeClientSocket(localSocketManager: LocalSocketManager, fd: Int) {
            LocalClientSocket(localSocketManager, fd, PeerCred()).closeClientSocket(true)
        }
    }

    /** The [LocalSocketRunConfig] containing run config for the [LocalClientSocket]. */
    @NonNull
    val mLocalSocketRunConfig: LocalSocketRunConfig = mLocalSocketManager.getLocalSocketRunConfig()

    /**
     * The [LocalClientSocket] file descriptor.
     * Value will be `>= 0` if socket has been connected and `-1` if closed.
     */
    private var fd: Int = -1

    /** The creation time of [LocalClientSocket]. This is also used for getDeadline(). */
    val mCreationTime: Long = System.currentTimeMillis()

    /** The [OutputStream] implementation for the [LocalClientSocket]. */
    @NonNull
    private val outputStream = SocketOutputStream()

    /** The [InputStream] implementation for the [LocalClientSocket]. */
    @NonNull
    private val inputStream = SocketInputStream()

    init {
        setFD(fd)
        peerCred.fillPeerCred(mLocalSocketManager.context)
    }

    /** Close client socket. */
    @Synchronized
    fun closeClientSocket(logErrorMessage: Boolean): Error? {
        return try {
            null
            close()
            null
        } catch (e: IOException) {
            val error = LocalSocketErrno.ERRNO_CLOSE_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.getTitle(), e.message
            )
            if (logErrorMessage)
                Logger.logErrorExtended(LOG_TAG, error.errorLogString)
            error
        }
    }

    /** Implementation for [Closeable.close] to close client socket. */
    override fun close() {
        if (fd >= 0) {
            Logger.logVerbose(
                LOG_TAG,
                "Client socket close for \"${mLocalSocketRunConfig.getTitle()}\" server: ${peerCred.minimalString}"
            )
            val result = LocalSocketManager.closeSocket(mLocalSocketRunConfig.getLogTitle() + " (client)", fd)
            if (result == null || result.retval != 0) {
                throw IOException(JniResult.getErrorString(result))
            }
            // Update getFD() to signify that client socket has been closed
            setFD(-1)
        }
    }

    /**
     * Attempts to read up to data buffer length bytes from file descriptor into the data buffer.
     * On success, the number of bytes read is returned (zero indicates end of file) in bytesRead.
     * It is not an error if bytesRead is smaller than the number of bytes requested; this may happen
     * for example because fewer bytes are actually available right now (maybe because we were close
     * to end-of-file, or because we are reading from a pipe), or because read() was interrupted by
     * a signal.
     *
     * If while reading the [mCreationTime] + the milliseconds returned by
     * [LocalSocketRunConfig.getDeadline()] elapses but all the data has not been read, an
     * error would be returned.
     *
     * This is a wrapper for [LocalSocketManager.read], which can
     * be called instead if you want to get access to errno int value instead of [JniResult]
     * error [String].
     *
     * @param data The data buffer to read bytes into.
     * @param bytesRead The actual bytes read.
     * @return Returns the `error` if reading was not successful containing [JniResult]
     * error [String], otherwise `null`.
     */
    fun read(data: ByteArray, bytesRead: MutableInt): Error? {
        bytesRead.value = 0

        if (fd < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                fd, mLocalSocketRunConfig.getTitle()
            )
        }

        val result = LocalSocketManager.read(
            mLocalSocketRunConfig.getLogTitle() + " (client)",
            fd,
            data,
            if (mLocalSocketRunConfig.getDeadline() > 0) mCreationTime + mLocalSocketRunConfig.getDeadline() else 0
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_READ_DATA_FROM_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result)
            )
        }

        bytesRead.value = result.intData
        return null
    }

    /**
     * Attempts to send data buffer to the file descriptor.
     *
     * If while sending the [mCreationTime] + the milliseconds returned by
     * [LocalSocketRunConfig.getDeadline()] elapses but all the data has not been sent, an
     * error would be returned.
     *
     * This is a wrapper for [LocalSocketManager.send], which can
     * be called instead if you want to get access to errno int value instead of [JniResult]
     * error [String].
     *
     * @param data The data buffer containing bytes to send.
     * @return Returns the `error` if sending was not successful containing [JniResult]
     * error [String], otherwise `null`.
     */
    fun send(data: ByteArray): Error? {
        if (fd < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                fd, mLocalSocketRunConfig.getTitle()
            )
        }

        val result = LocalSocketManager.send(
            mLocalSocketRunConfig.getLogTitle() + " (client)",
            fd,
            data,
            if (mLocalSocketRunConfig.getDeadline() > 0) mCreationTime + mLocalSocketRunConfig.getDeadline() else 0
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_SEND_DATA_TO_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result)
            )
        }

        return null
    }

    /**
     * Attempts to read all the bytes available on [SocketInputStream] and appends them to
     * `data` [StringBuilder].
     *
     * This is a wrapper for [read] called via [SocketInputStream.read].
     *
     * @param data The data [StringBuilder] to append the bytes read into.
     * @param closeStreamOnFinish If set to `true`, then underlying input stream will closed
     *                            and further attempts to read from socket will fail.
     * @return Returns the `error` if reading was not successful containing [JniResult]
     * error [String], otherwise `null`.
     */
    fun readDataOnInputStream(data: StringBuilder, closeStreamOnFinish: Boolean): Error? {
        return try {
            null
            var c: Int
            while (true) {
                val result = getInputStreamReader().read()
                if (result <= 0) break
                c = result
                data.append(c.toChar())
            }
            null
        } catch (e: IOException) {
            // The SocketInputStream.read() throws the Error message in an IOException,
            // so just read the exception message and not the stack trace, otherwise it would result
            // in a messy nested error message.
            LocalSocketErrno.ERRNO_READ_DATA_FROM_INPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                mLocalSocketRunConfig.getTitle(), DataUtils.getSpaceIndentedString(e.message, 1)
            )
        } catch (e: Exception) {
            LocalSocketErrno.ERRNO_READ_DATA_FROM_INPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.getTitle(), e.message
            )
        } finally {
            if (closeStreamOnFinish) {
                try {
                    getInputStreamReader().close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Attempts to send all the bytes passed to [SocketOutputStream].
     *
     * This is a wrapper for [send] called via [SocketOutputStream.write].
     *
     * @param data The [String] bytes to send.
     * @param closeStreamOnFinish If set to `true`, then underlying output stream will closed
     *                            and further attempts to send to socket will fail.
     * @return Returns the `error` if sending was not successful containing [JniResult]
     * error [String], otherwise `null`.
     */
    fun sendDataToOutputStream(data: String, closeStreamOnFinish: Boolean): Error? {
        return try {
            BufferedWriter(getOutputStreamWriter()).use { byteStreamWriter ->
                byteStreamWriter.write(data)
                byteStreamWriter.flush()
            }
            null
        } catch (e: IOException) {
            // The SocketOutputStream.write() throws the Error message in an IOException,
            // so just read the exception message and not the stack trace, otherwise it would result
            // in a messy nested error message.
            LocalSocketErrno.ERRNO_SEND_DATA_TO_OUTPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                mLocalSocketRunConfig.getTitle(), DataUtils.getSpaceIndentedString(e.message, 1)
            )
        } catch (e: Exception) {
            LocalSocketErrno.ERRNO_SEND_DATA_TO_OUTPUT_STREAM_OF_CLIENT_SOCKET_FAILED_WITH_EXCEPTION.getError(
                e, mLocalSocketRunConfig.getTitle(), e.message
            )
        } finally {
            if (closeStreamOnFinish) {
                try {
                    getOutputStreamWriter().close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }

    /** Wrapper for [available] that checks getDeadline(). The
     * [SocketInputStream] calls this. */
    fun available(available: MutableInt): Error? {
        return available(available, true)
    }

    /**
     * Get available bytes on [inputStream] and optionally check if value returned by
     * [LocalSocketRunConfig.getDeadline()] has passed.
     */
    fun available(available: MutableInt, checkDeadline: Boolean): Error? {
        available.value = 0

        if (fd < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError(
                fd, mLocalSocketRunConfig.getTitle()
            )
        }

        if (checkDeadline && mLocalSocketRunConfig.getDeadline() > 0 &&
            System.currentTimeMillis() > (mCreationTime + mLocalSocketRunConfig.getDeadline())
        ) {
            return null
        }

        val result = LocalSocketManager.available(
            mLocalSocketRunConfig.getLogTitle() + " (client)",
            mLocalSocketRunConfig.getFD() ?: -1
        )
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_CHECK_AVAILABLE_DATA_ON_CLIENT_SOCKET_FAILED.getError(
                mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result)
            )
        }

        available.value = result.intData ?: 0
        return null
    }

    /** Set [LocalClientSocket] receiving (SO_RCVTIMEO) timeout to value returned by [LocalSocketRunConfig.getReceiveTimeout()]. */
    fun setReadTimeout(): Error? {
        if (fd >= 0) {
            val result = LocalSocketManager.setSocketReadTimeout(
                mLocalSocketRunConfig.getLogTitle() + " (client)",
                fd, mLocalSocketRunConfig.getReceiveTimeout()
            )
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED.getError(
                    mLocalSocketRunConfig.getTitle(), mLocalSocketRunConfig.getReceiveTimeout(), JniResult.getErrorString(result)
                )
            }
        }
        return null
    }

    /** Set [LocalClientSocket] sending (SO_SNDTIMEO) timeout to value returned by [LocalSocketRunConfig.getSendTimeout()]. */
    fun setWriteTimeout(): Error? {
        if (fd >= 0) {
            val result = LocalSocketManager.setSocketSendTimeout(
                mLocalSocketRunConfig.getLogTitle() + " (client)",
                fd, mLocalSocketRunConfig.getSendTimeout()
            )
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED.getError(
                    mLocalSocketRunConfig.getTitle(), mLocalSocketRunConfig.getSendTimeout(), JniResult.getErrorString(result)
                )
            }
        }
        return null
    }

    /** Get [fd] for the client socket. */
    fun getFD(): Int = fd

    /** Set [fd]. Value must be greater than 0 or -1. */
    private fun setFD(fd: Int) {
        this.fd = if (fd >= 0) fd else -1
    }

    /** Get [peerCred] for the client socket. */
    fun getPeerCred(): PeerCred = peerCred

    /** Get [mCreationTime] for the client socket. */
    fun getCreationTime(): Long = mCreationTime

    /** Get [outputStream] for the client socket. The stream will automatically close when client socket is closed. */
    fun getOutputStream(): OutputStream = outputStream

    /** Get [OutputStreamWriter] for [outputStream] for the client socket. The stream will automatically close when client socket is closed. */
    @NonNull
    fun getOutputStreamWriter(): OutputStreamWriter = OutputStreamWriter(getOutputStream())

    /** Get [inputStream] for the client socket. The stream will automatically close when client socket is closed. */
    fun getInputStream(): InputStream = inputStream

    /** Get [InputStreamReader] for [inputStream] for the client socket. The stream will automatically close when client socket is closed. */
    @NonNull
    fun getInputStreamReader(): InputStreamReader = InputStreamReader(getInputStream())

    /** Get a log [String] for the [LocalClientSocket]. */
    @NonNull
    fun getLogString(): String {
        val logString = StringBuilder()

        logString.append("Client Socket:")
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("FD", fd, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Creation Time", mCreationTime, "-"))
        logString.append("\n\n\n")

        logString.append(peerCred.getLogString())

        return logString.toString()
    }

    /** Get a markdown [String] for the [LocalClientSocket]. */
    @NonNull
    fun getMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append("## ").append("Client Socket")
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("FD", fd, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Creation Time", mCreationTime, "-"))
        markdownString.append("\n\n\n")

        markdownString.append(peerCred.getMarkdownString())

        return markdownString.toString()
    }

    /** Wrapper class to allow pass by reference of int values. */
    class MutableInt(var value: Int) {
        constructor() : this(0)
    }

    /** The [InputStream] implementation for the [LocalClientSocket]. */
    protected inner class SocketInputStream : InputStream() {
        private val mBytes = ByteArray(1)

        override fun read(): Int {
            val bytesRead = MutableInt(0)
            val error = this@LocalClientSocket.read(mBytes, bytesRead)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }

            if (bytesRead.value == 0) {
                return -1
            }

            return mBytes[0].toInt() and 0xFF
        }

        override fun read(bytes: ByteArray): Int {
            if (bytes == null) {
                throw NullPointerException("Read buffer can't be null")
            }

            val bytesRead = MutableInt(0)
            val error = this@LocalClientSocket.read(bytes, bytesRead)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }

            if (bytesRead.value == 0) {
                return -1
            }

            return bytesRead.value
        }

        override fun available(): Int {
            val available = MutableInt(0)
            val error = this@LocalClientSocket.available(available)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
            return available.value
        }
    }

    /** The [OutputStream] implementation for the [LocalClientSocket]. */
    protected inner class SocketOutputStream : OutputStream() {
        private val mBytes = ByteArray(1)

        override fun write(b: Int) {
            mBytes[0] = b.toByte()

            val error = this@LocalClientSocket.send(mBytes)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
        }

        override fun write(bytes: ByteArray) {
            val error = this@LocalClientSocket.send(bytes)
            if (error != null) {
                throw IOException(error.errorMarkdownString)
            }
        }
    }
}
