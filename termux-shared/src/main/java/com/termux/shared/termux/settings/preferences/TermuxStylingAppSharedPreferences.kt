package com.termux.shared.termux.settings.preferences

import android.content.Context
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.android.PackageUtils
import com.termux.shared.logger.Logger
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_STYLING_APP

open class TermuxStylingAppSharedPreferences private constructor(
    @NonNull context: Context
) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {

    companion object {
        private const val LOG_TAG = "TermuxStylingAppSharedPreferences"

        /**
         * Get [TermuxStylingAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_STYLING_PACKAGE_NAME].
         * @return Returns the [TermuxStylingAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context): TermuxStylingAppSharedPreferences? {
            val termuxStylingPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME)
            return if (termuxStylingPackageContext == null) null
            else TermuxStylingAppSharedPreferences(termuxStylingPackageContext)
        }

        /**
         * Get [TermuxStylingAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_STYLING_PACKAGE_NAME].
         * @param exitAppOnError If `true` and failed to get package context, then a dialog will
         *                       be shown which when dismissed will exit the app.
         * @return Returns the [TermuxStylingAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context, exitAppOnError: Boolean): TermuxStylingAppSharedPreferences? {
            val termuxStylingPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, exitAppOnError)
            return if (termuxStylingPackageContext == null) null
            else TermuxStylingAppSharedPreferences(termuxStylingPackageContext)
        }
    }

    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile)
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        else
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_STYLING_APP.KEY_LOG_LEVEL, level, commitToFile)
    }
}
