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
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_BOOT_APP

open class TermuxBootAppSharedPreferences private constructor(
    @NonNull context: Context
) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {

    companion object {
        private const val LOG_TAG = "TermuxBootAppSharedPreferences"

        /**
         * Get [TermuxBootAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_BOOT_PACKAGE_NAME].
         * @return Returns the [TermuxBootAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context): TermuxBootAppSharedPreferences? {
            val termuxBootPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_BOOT_PACKAGE_NAME)
            return if (termuxBootPackageContext == null) null
            else TermuxBootAppSharedPreferences(termuxBootPackageContext)
        }

        /**
         * Get [TermuxBootAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_BOOT_PACKAGE_NAME].
         * @param exitAppOnError If `true` and failed to get package context, then a dialog will
         *                       be shown which when dismissed will exit the app.
         * @return Returns the [TermuxBootAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context, exitAppOnError: Boolean): TermuxBootAppSharedPreferences? {
            val termuxBootPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_BOOT_PACKAGE_NAME, exitAppOnError)
            return if (termuxBootPackageContext == null) null
            else TermuxBootAppSharedPreferences(termuxBootPackageContext)
        }
    }

    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile)
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_BOOT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        else
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_BOOT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_BOOT_APP.KEY_LOG_LEVEL, level, commitToFile)
    }
}
