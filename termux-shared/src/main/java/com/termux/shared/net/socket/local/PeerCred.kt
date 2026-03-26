package com.termux.shared.net.socket.local
import java.util.*

import android.content.Context
import androidx.annotation.Keep
import androidx.annotation.NonNull
import com.termux.shared.android.ProcessUtils
import com.termux.shared.android.UserUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils

/** The [PeerCred] of the [LocalClientSocket] containing info of client/peer. */
@Keep
data class PeerCred(
    /** Process Id. */
    var pid: Int = -1,
    /** Process Name. */
    var pname: String? = null,
    /** User Id. */
    var uid: Int = -1,
    /** User name. */
    var uname: String? = null,
    /** Group Id. */
    var gid: Int = -1,
    /** Group name. */
    var gname: String? = null,
    /** Command line that started the process. */
    var cmdline: String? = null
) {

    companion object {
        const val LOG_TAG = "PeerCred"

        /**
         * Get a log [String] for [PeerCred].
         *
         * @param peerCred The [PeerCred] to get info of.
         * @return Returns the log [String].
         */
        @JvmStatic
        @NonNull
        fun getPeerCredLogString(peerCred: PeerCred?): String {
            if (peerCred == null) return "null"
            return peerCred.getLogString()
        }

        /**
         * Get a markdown [String] for [PeerCred].
         *
         * @param peerCred The [PeerCred] to get info of.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getPeerCredMarkdownString(peerCred: PeerCred?): String {
            if (peerCred == null) return "null"
            return peerCred.getMarkdownString()
        }
    }

    /** Set data that was not set by JNI. */
    fun fillPeerCred(@NonNull context: Context) {
        fillUnameAndGname(context)
        fillPname(context)
    }

    /** Set [uname] and [gname] if not set. */
    fun fillUnameAndGname(@NonNull context: Context) {
        uname = UserUtils.getNameForUid(context, uid)
        gname = if (gid != uid) UserUtils.getNameForUid(context, gid) else uname
    }

    /** Set [pname] if not set. */
    fun fillPname(@NonNull context: Context) {
        // If jni did not set process name since it wouldn't be able to access /proc/<pid> of other
        // users/apps, then try to see if any app has that pid, but this wouldn't check child
        // processes of the app.
        if (pid > 0 && pname == null)
            pname = ProcessUtils.getAppProcessNameForPid(context, pid)
    }

    /** Get a log [String] for the [PeerCred]. */
    @NonNull
    fun getLogString(): String {
        return buildString {
            append("Peer Cred:")
            append("\n").append(Logger.getSingleLineLogStringEntry("Process", getProcessString(), "-"))
            append("\n").append(Logger.getSingleLineLogStringEntry("User", getUserString(), "-"))
            append("\n").append(Logger.getSingleLineLogStringEntry("Group", getGroupString(), "-"))
            if (cmdline != null)
                append("\n").append(Logger.getMultiLineLogStringEntry("Cmdline", cmdline, "-"))
        }
    }

    /** Get a markdown [String] for the [PeerCred]. */
    @NonNull
    fun getMarkdownString(): String {
        return buildString {
            append("## ").append("Peer Cred")
            append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Process", getProcessString(), "-"))
            append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("User", getUserString(), "-"))
            append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Group", getGroupString(), "-"))
            if (cmdline != null)
                append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry("Cmdline", cmdline, "-"))
        }
    }

    @NonNull
    fun getMinimalString(): String {
        return "process=${getProcessString()}, user=${getUserString()}, group=${getGroupString()}"
    }

    val minimalString: String
        get() = getMinimalString()

    @NonNull
    fun getProcessString(): String = if (pname != null && (pname?.isNotEmpty() ?: false)) "$pid ($pname)" else "$pid"

    @NonNull
    fun getUserString(): String = if (uname != null) "$uid ($uname)" else "$uid"

    @NonNull
    fun getGroupString(): String = if (gname != null) "$gid ($gname)" else "$gid"
}
