package com.termux.shared.activity.media
import java.util.*

import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.termux.shared.logger.Logger
import com.termux.shared.theme.NightMode

object AppCompatActivityUtils {

    private const val LOG_TAG = "AppCompatActivityUtils"

    /** Set activity night mode.
     *
     * @param activity The host [AppCompatActivity].
     * @param name The [String] representing the name for a [NightMode].
     * @param local If set to `true`, then a call to [AppCompatDelegate.setLocalNightMode]
     *              will be made, otherwise to [AppCompatDelegate.setDefaultNightMode].
     */
    fun setNightMode(activity: AppCompatActivity?, name: String?, local: Boolean) {
        if (name == null) return
        val nightMode = NightMode.modeOf(name)
        if (nightMode != null) {
            if (local) {
                activity?.delegate?.setLocalNightMode(nightMode.mode)
            } else {
                AppCompatDelegate.setDefaultNightMode(nightMode.mode)
            }
        }
    }

    /** Set activity toolbar.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     */
    fun setToolbar(activity: AppCompatActivity, @IdRes id: Int) {
        val toolbar: Toolbar? = activity.findViewById(id)
        toolbar?.let { activity.setSupportActionBar(it) }
    }

    /** Set activity toolbar title.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     * @param title The toolbar title [String].
     * @param titleAppearance The toolbar title TextAppearance resource id.
     */
    fun setToolbarTitle(
        activity: AppCompatActivity,
        @IdRes id: Int,
        title: String?,
        @StyleRes titleAppearance: Int
    ) {
        val toolbar: Toolbar? = activity.findViewById(id)
        if (toolbar != null) {
            //toolbar.setTitle(title) // Does not work
            val actionBar: ActionBar? = activity.supportActionBar
            actionBar?.title = title

            try {
                if (titleAppearance != 0)
                    toolbar.setTitleTextAppearance(activity, titleAppearance)
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Failed to set toolbar title appearance to style resource id $titleAppearance",
                    e
                )
            }
        }
    }

    /** Set activity toolbar subtitle.
     *
     * @param activity The host [AppCompatActivity].
     * @param id The toolbar resource id.
     * @param subtitle The toolbar subtitle [String].
     * @param subtitleAppearance The toolbar subtitle TextAppearance resource id.
     */
    fun setToolbarSubtitle(
        activity: AppCompatActivity,
        @IdRes id: Int,
        subtitle: String?,
        @StyleRes subtitleAppearance: Int
    ) {
        val toolbar: Toolbar? = activity.findViewById(id)
        if (toolbar != null) {
            toolbar.subtitle = subtitle
            try {
                if (subtitleAppearance != 0)
                    toolbar.setSubtitleTextAppearance(activity, subtitleAppearance)
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Failed to set toolbar subtitle appearance to style resource id $subtitleAppearance",
                    e
                )
            }
        }
    }

    /** Set whether back button should be shown in activity toolbar.
     *
     * @param activity The host [AppCompatActivity].
     * @param showBackButtonInActionBar Set to `true` to enable and `false` to disable.
     */
    fun setShowBackButtonInActionBar(activity: AppCompatActivity, showBackButtonInActionBar: Boolean) {
        val actionBar: ActionBar? = activity.supportActionBar
        if (actionBar != null) {
            if (showBackButtonInActionBar) {
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.setDisplayShowHomeEnabled(true)
            } else {
                actionBar.setDisplayHomeAsUpEnabled(false)
                actionBar.setDisplayShowHomeEnabled(false)
            }
        }
    }
}
