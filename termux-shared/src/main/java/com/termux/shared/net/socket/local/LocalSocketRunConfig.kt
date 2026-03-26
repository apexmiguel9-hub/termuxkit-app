package com.termux.shared.net.socket.local
import java.util.*

import androidx.annotation.NonNull
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable
import java.nio.charset.StandardCharsets

/**
 * Run config for [LocalSocketManager].
 */
open class LocalSocketRunConfig(
    /** The [title]. */
    @NonNull val title: String,
    /**
     * The [path].
     *
     * For a filesystem socket, this must be an absolute path to the socket file. Creation of a new
     * socket will fail if the server starter app process does not have write and search (execute)
     * permission on the directory in which the socket is created. The client process must have write
     * permission on the socket to connect to it. Other app will not be able to connect to socket
     * if its created in private app data directory.
     *
     * For an abstract namespace socket, the first byte must be a null `\0` character. Note that on
     * Android 9+, if server app is using `targetSdkVersion` `28`, then other apps will not be able
     * to connect to it due to selinux restrictions.
     * > Per-app SELinux domains
     * > Apps that target Android 9 or higher cannot share data with other apps using world-accessible
     * Unix permissions. This change improves the integrity of the Android Application Sandbox,
     * particularly the requirement that an app's private data is accessible only by that app.
     * https://developer.android.com/about/versions/pie/android-9.0-changes-28
     * https://github.com/android/ndk/issues/1469
     * https://stackoverflow.com/questions/63806516/avc-denied-connectto-when-using-uds-on-android-10
     *
     * Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     */
    @NonNull var path: String,
    /** The [mLocalSocketManagerClient]. */
    @NonNull val mLocalSocketManagerClient: ILocalSocketManager
) : Serializable {

    companion object {
        /**
         * The [LocalClientSocket] receiving (SO_RCVTIMEO) timeout in milliseconds.
         *
         * https://manpages.debian.org/testing/manpages/socket.7.en.html
         * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
         * Defaults to [DEFAULT_RECEIVE_TIMEOUT].
         */
        const val DEFAULT_RECEIVE_TIMEOUT = 10000

        /**
         * The [LocalClientSocket] sending (SO_SNDTIMEO) timeout in milliseconds.
         *
         * https://manpages.debian.org/testing/manpages/socket.7.en.html
         * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
         * Defaults to [DEFAULT_SEND_TIMEOUT].
         */
        const val DEFAULT_SEND_TIMEOUT = 10000

        /**
         * The [LocalClientSocket] deadline in milliseconds. When the deadline has elapsed after
         * creation time of client socket, all reads and writes will error out. Set to 0, for no
         * deadline.
         * Defaults to [DEFAULT_DEADLINE].
         */
        const val DEFAULT_DEADLINE = 0L

        /**
         * The [LocalServerSocket] backlog for the maximum length to which the queue of pending connections
         * for the socket may grow. This value may be ignored or may not have one-to-one mapping
         * in kernel implementation. Value must be greater than 0.
         *
         * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/net/LocalSocketManager.java;l=31
         * Defaults to [DEFAULT_BACKLOG].
         */
        const val DEFAULT_BACKLOG = 50

        /**
         * Get a log [String] for [LocalSocketRunConfig].
         *
         * @param config The [LocalSocketRunConfig] to get info of.
         * @return Returns the log [String].
         */
        @JvmStatic
        @NonNull
        fun getRunConfigLogString(config: LocalSocketRunConfig?): String {
            if (config == null) return "null"
            return config.getLogString()
        }

        /**
         * Get a markdown [String] for [LocalSocketRunConfig].
         *
         * @param config The [LocalSocketRunConfig] to get info of.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getRunConfigMarkdownString(config: LocalSocketRunConfig?): String {
            if (config == null) return "null"
            return config.getMarkdownString()
        }
    }

    /**
     * The [LocalServerSocket] path.
     */

    /** If abstract namespace [LocalServerSocket] instead of filesystem. */
    var abstractNamespaceSocket: Boolean

    /**
     * The [LocalServerSocket] file descriptor.
     * Value will be `>= 0` if socket has been created successfully and `-1` if not created or closed.
     */
    var fd: Int = -1

    /**
     * The [LocalClientSocket] receiving (SO_RCVTIMEO) timeout in milliseconds.
     *
     * https://manpages.debian.org/testing/manpages/socket.7.en.html
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
     * Defaults to [DEFAULT_RECEIVE_TIMEOUT].
     */
    var receiveTimeout: Int? = null

    /**
     * The [LocalClientSocket] sending (SO_SNDTIMEO) timeout in milliseconds.
     *
     * https://manpages.debian.org/testing/manpages/socket.7.en.html
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;l=55
     * Defaults to [DEFAULT_SEND_TIMEOUT].
     */
    var sendTimeout: Int? = null

    /**
     * The [LocalClientSocket] deadline in milliseconds. When the deadline has elapsed after
     * creation time of client socket, all reads and writes will error out. Set to 0, for no
     * deadline.
     * Defaults to [DEFAULT_DEADLINE].
     */
    var deadline: Long? = null

    /**
     * The [LocalServerSocket] backlog for the maximum length to which the queue of pending connections
     * for the socket may grow. This value may be ignored or may not have one-to-one mapping
     * in kernel implementation. Value must be greater than 0.
     *
     * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/net/LocalSocketManager.java;l=31
     * Defaults to [DEFAULT_BACKLOG].
     */
    var backlog: Int? = null

    init {
        abstractNamespaceSocket = path.toByteArray(StandardCharsets.UTF_8)[0] == 0.toByte()

        if (!abstractNamespaceSocket) {
            path = FileUtils.getCanonicalPath(path, null)
        }
    }

    /** Get [title]. */
    fun getTitle(): String = title

    /** Get log title that should be used for [LocalSocketManager]. */
    fun getLogTitle(): String = Logger.getDefaultLogTag() + "." + title

    /** Get [path]. */
    fun getPath(): String = path

    /** Get [abstractNamespaceSocket]. */
    fun isAbstractNamespaceSocket(): Boolean = abstractNamespaceSocket

    /** Get [mLocalSocketManagerClient]. */
    fun getLocalSocketManagerClient(): ILocalSocketManager = mLocalSocketManagerClient

    /** Get [fd]. */
    fun getFD(): Int? = fd

    /** Set [fd]. Value must be greater than 0 or -1. */
    fun setFD(fd: Int) {
        this.fd = if (fd >= 0) fd else -1
    }

    /** Get [receiveTimeout] if set, otherwise [DEFAULT_RECEIVE_TIMEOUT]. */
    fun getReceiveTimeout(): Int = (receiveTimeout ?: DEFAULT_RECEIVE_TIMEOUT).toInt()

    /** Set [receiveTimeout]. */
    fun setReceiveTimeout(receiveTimeout: Int?) {
        this.receiveTimeout = receiveTimeout
    }

    /** Get [sendTimeout] if set, otherwise [DEFAULT_SEND_TIMEOUT]. */
    fun getSendTimeout(): Int = (sendTimeout ?: DEFAULT_SEND_TIMEOUT).toInt()

    /** Set [sendTimeout]. */
    fun setSendTimeout(sendTimeout: Int?) {
        this.sendTimeout = sendTimeout
    }

    /** Get [deadline] if set, otherwise [DEFAULT_DEADLINE]. */
    fun getDeadline(): Long = deadline ?: DEFAULT_DEADLINE

    /** Set [deadline]. */
    fun setDeadline(deadline: Long?) {
        this.deadline = deadline
    }

    /** Get [backlog] if set, otherwise [DEFAULT_BACKLOG]. */
    fun getBacklog(): Int = backlog ?: DEFAULT_BACKLOG

    /** Set [backlog]. Value must be greater than 0. */
    fun setBacklog(backlog: Int?) {
        if (backlog != null && backlog > 0)
            this.backlog = backlog
    }

    /** Get a log [String] for the [LocalSocketRunConfig]. */
    @NonNull
    open fun getLogString(): String {
        val logString = StringBuilder()

        logString.append(title).append(" Socket Server Run Config:")
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Path", path, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("AbstractNamespaceSocket", abstractNamespaceSocket, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("LocalSocketManagerClient", mLocalSocketManagerClient.javaClass.name, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("FD", fd, "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("ReceiveTimeout", getReceiveTimeout(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("SendTimeout", getSendTimeout(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Deadline", getDeadline(), "-"))
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("Backlog", getBacklog(), "-"))

        return logString.toString()
    }

    /** Get a markdown [String] for the [LocalSocketRunConfig]. */
    @NonNull
    open fun getMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append("## ").append(title).append(" Socket Server Run Config")
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Path", path, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("AbstractNamespaceSocket", abstractNamespaceSocket, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("LocalSocketManagerClient", mLocalSocketManagerClient.javaClass.name, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("FD", fd, "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("ReceiveTimeout", getReceiveTimeout(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("SendTimeout", getSendTimeout(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Deadline", getDeadline(), "-"))
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Backlog", getBacklog(), "-"))

        return markdownString.toString()
    }

    @NonNull
    override fun toString(): String {
        return getLogString()
    }
}
