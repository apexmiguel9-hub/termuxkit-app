package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.logger.Logger
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
open class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_debugging_preferences, rootKey)

        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(@NonNull context: Context) {
        val loggingCategory: PreferenceCategory? = findPreference("logging")
        if (loggingCategory == null) return

        val logLevelListPreference: ListPreference? = findPreference("log_level")
        if (logLevelListPreference != null) {
            val preferences = TermuxAppSharedPreferences.build(context, true) ?: return

            setLogLevelListPreferenceData(logLevelListPreference, context, preferences.logLevel)
            loggingCategory.addPreference(logLevelListPreference)
        }
    }

    companion object {
        @JvmStatic
        fun setLogLevelListPreferenceData(
            logLevelListPreference: ListPreference?,
            context: Context?,
            logLevel: Int
        ): ListPreference {
            var preference = logLevelListPreference
            if (preference == null)
                preference = ListPreference(context)

            val logLevels = Logger.getLogLevelsArray()
            val logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true)

            preference.entryValues = logLevels
            preference.entries = logLevelLabels

            preference.value = logLevel.toString()
            preference.defaultValue = Logger.DEFAULT_LOG_LEVEL.toString()

            return preference
        }
    }
}

internal class DebuggingPreferencesDataStore private constructor(private val context: Context) : PreferenceDataStore() {

    private val preferences: TermuxAppSharedPreferences? = TermuxAppSharedPreferences.build(context, true)

    companion object {
        private var instance: DebuggingPreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): DebuggingPreferencesDataStore {
            if (instance == null) {
                instance = DebuggingPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

    @Nullable
    override fun getString(key: String, @Nullable defValue: String?): String? {
        if (preferences == null || key == null) return null

        return when (key) {
            "log_level" -> preferences.logLevel.toString()
            else -> null
        }
    }

    override fun putString(key: String?, @Nullable value: String?) {
        if (preferences == null || key == null) return

        when (key) {
            "log_level" -> {
                if (value != null) {
                    preferences.setLogLevel(context, value.toInt())
                }
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (preferences == null || key == null) return

        when (key) {
            "terminal_view_key_logging_enabled" ->
                preferences.isTerminalViewKeyLoggingEnabled = value
            "plugin_error_notifications_enabled" ->
                preferences.isPluginErrorNotificationsEnabled = value
            "crash_report_notifications_enabled" ->
                preferences.isCrashReportNotificationsEnabled = value
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (preferences == null) return false

        return when (key) {
            "terminal_view_key_logging_enabled" -> preferences.isTerminalViewKeyLoggingEnabled
            "plugin_error_notifications_enabled" -> preferences.arePluginErrorNotificationsEnabled(false)
            "crash_report_notifications_enabled" -> preferences.areCrashReportNotificationsEnabled(false)
            else -> false
        }
    }
}
