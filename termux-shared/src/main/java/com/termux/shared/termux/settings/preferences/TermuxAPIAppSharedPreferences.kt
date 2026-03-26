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
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_API_APP

open class TermuxAPIAppSharedPreferences private constructor(
    @NonNull context: Context
) : AppSharedPreferences(
    context,
    SharedPreferenceUtils.getPrivateSharedPreferences(
        context,
        TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    ),
    SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(
        context,
        TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
    )
) {

    companion object {
        private const val LOG_TAG = "TermuxAPIAppSharedPreferences"

        /**
         * Get [TermuxAPIAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_API_PACKAGE_NAME].
         * @return Returns the [TermuxAPIAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context): TermuxAPIAppSharedPreferences? {
            val termuxAPIPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_API_PACKAGE_NAME)
            return if (termuxAPIPackageContext == null) null
            else TermuxAPIAppSharedPreferences(termuxAPIPackageContext)
        }

        /**
         * Get [TermuxAPIAppSharedPreferences].
         *
         * @param context The [Context] to use to get the [Context] of the
         *                [TermuxConstants.TERMUX_API_PACKAGE_NAME].
         * @param exitAppOnError If `true` and failed to get package context, then a dialog will
         *                       be shown which when dismissed will exit the app.
         * @return Returns the [TermuxAPIAppSharedPreferences]. This will `null` if an exception is raised.
         */
        @JvmStatic
        @Nullable
        fun build(@NonNull context: Context, exitAppOnError: Boolean): TermuxAPIAppSharedPreferences? {
            val termuxAPIPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_API_PACKAGE_NAME, exitAppOnError)
            return if (termuxAPIPackageContext == null) null
            else TermuxAPIAppSharedPreferences(termuxAPIPackageContext)
        }
    }

    fun getLogLevel(readFromFile: Boolean): Int {
        return if (readFromFile)
            SharedPreferenceUtils.getInt(mMultiProcessSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
        else
            SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL)
    }

    fun setLogLevel(context: Context?, logLevel: Int, commitToFile: Boolean) {
        val level = Logger.setLogLevel(context, logLevel)
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_API_APP.KEY_LOG_LEVEL, level, commitToFile)
    }

    fun getLastPendingIntentRequestCode(): Int {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE)
    }

    fun setLastPendingIntentRequestCode(lastPendingIntentRequestCode: Int) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, lastPendingIntentRequestCode, true)
    }
}
