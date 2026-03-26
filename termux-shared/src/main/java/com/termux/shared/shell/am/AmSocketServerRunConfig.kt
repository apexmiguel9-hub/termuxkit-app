package com.termux.shared.shell.am
import java.util.*

import android.Manifest
import androidx.annotation.NonNull
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.net.socket.local.ILocalSocketManager
import com.termux.shared.net.socket.local.LocalSocketRunConfig
import java.io.Serializable

/**
 * Run config for [AmSocketServer].
 */
open class AmSocketServerRunConfig(
    title: String,
    path: String,
    localSocketManagerClient: ILocalSocketManager
) : LocalSocketRunConfig(title, path, localSocketManagerClient), Serializable {

    companion object {
        const val DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION = true

        /**
         * Get a log [String] for [AmSocketServerRunConfig].
         *
         * @param config The [AmSocketServerRunConfig] to get info of.
         * @return Returns the log [String].
         */
        @JvmStatic
        @NonNull
        fun getRunConfigLogString(config: AmSocketServerRunConfig?): String {
            if (config == null) return "null"
            return config.getLogString()
        }

        /**
         * Get a markdown [String] for [AmSocketServerRunConfig].
         *
         * @param config The [AmSocketServerRunConfig] to get info of.
         * @return Returns the markdown [String].
         */
        @JvmStatic
        fun getRunConfigMarkdownString(config: AmSocketServerRunConfig?): String {
            if (config == null) return "null"
            return config.getMarkdownString()
        }
    }

    /**
     * Check if [Manifest.permission.SYSTEM_ALERT_WINDOW] has been granted if running on Android `>= 10`
     * if starting activities. Will also check when starting services in case starting foreground
     * service is not allowed.
     *
     * https://developer.android.com/guide/components/activities/background-starts
     */
    var checkDisplayOverAppsPermission: Boolean? = null

    /** Get [checkDisplayOverAppsPermission] if set, otherwise [DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION]. */
    fun shouldCheckDisplayOverAppsPermission(): Boolean {
        return checkDisplayOverAppsPermission ?: DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION
    }

    /** Set [checkDisplayOverAppsPermission]. */
    fun setCheckDisplayOverAppsPermission(checkDisplayOverAppsPermission: Boolean?) {
        this.checkDisplayOverAppsPermission = checkDisplayOverAppsPermission
    }

    /** Get a log [String] for the [AmSocketServerRunConfig]. */
    @NonNull
    override fun getLogString(): String {
        val logString = StringBuilder()
        logString.append(super.getLogString()).append("\n\n\n")

        logString.append("Am Command:")
        logString.append("\n").append(Logger.getSingleLineLogStringEntry("CheckDisplayOverAppsPermission", shouldCheckDisplayOverAppsPermission(), "-"))

        return logString.toString()
    }

    /** Get a markdown [String] for the [AmSocketServerRunConfig]. */
    @NonNull
    override fun getMarkdownString(): String {
        val markdownString = StringBuilder()
        markdownString.append(super.getMarkdownString()).append("\n\n\n")

        markdownString.append("## ").append("Am Command")
        markdownString.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("CheckDisplayOverAppsPermission", shouldCheckDisplayOverAppsPermission(), "-"))

        return markdownString.toString()
    }

    @NonNull
    override fun toString(): String {
        return getLogString()
    }
}
