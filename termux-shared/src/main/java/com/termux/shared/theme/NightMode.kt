package com.termux.shared.theme
import java.util.*

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatDelegate
import com.termux.shared.logger.Logger

/** The modes used by to decide night mode for themes. */
enum class NightMode(
    val modeName: String,
    @AppCompatDelegate.NightMode val mode: Int
) {

    /** Night theme should be enabled. */
    TRUE("true", AppCompatDelegate.MODE_NIGHT_YES),

    /** Dark theme should be enabled. */
    FALSE("false", AppCompatDelegate.MODE_NIGHT_NO),

    /**
     * Use night or dark theme depending on system night mode.
     * https://developer.android.com/guide/topics/resources/providing-resources#NightQualifier
     */
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        /** The current app wide night mode used by various libraries. Defaults to [SYSTEM]. */
        private var APP_NIGHT_MODE: NightMode? = null

        private const val LOG_TAG = "NightMode"

        /** Get [NightMode] for `name` if found, otherwise `null`. */
        @Nullable
        @JvmStatic
        fun modeOf(name: String?): NightMode? {
            if (name == null) return null
            return values().find { it.modeName == name }
        }

        /** Get [NightMode] for `name` if found, otherwise `def`. */
        @NonNull
        @JvmStatic
        fun modeOf(@Nullable name: String?, @NonNull def: NightMode): NightMode {
            return modeOf(name) ?: def
        }

        /** Set [APP_NIGHT_MODE]. */
        @JvmStatic
        fun setAppNightMode(@Nullable name: String?) {
            if (name == null || name.isEmpty()) {
                APP_NIGHT_MODE = SYSTEM
            } else {
                val nightMode = modeOf(name)
                if (nightMode == null) {
                    Logger.logError(LOG_TAG, "Invalid APP_NIGHT_MODE \"$name\"")
                    return
                }
                APP_NIGHT_MODE = nightMode
            }

            Logger.logVerbose(LOG_TAG, "Set APP_NIGHT_MODE to \"${APP_NIGHT_MODE?.modeName}\"")
        }

        /** Get [APP_NIGHT_MODE]. */
        @NonNull
        @JvmStatic
        fun getAppNightMode(): NightMode {
            if (APP_NIGHT_MODE == null)
                APP_NIGHT_MODE = SYSTEM

            return APP_NIGHT_MODE ?: FALSE
        }
    }
}
