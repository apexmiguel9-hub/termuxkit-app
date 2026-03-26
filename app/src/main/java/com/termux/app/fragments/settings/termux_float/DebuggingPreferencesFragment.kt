package com.termux.app.fragments.settings.termux_float

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.app.fragments.settings.termux.DebuggingPreferencesFragment.Companion.setLogLevelListPreferenceData
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences

@Keep
open class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_float_debugging_preferences, rootKey)

        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(context: Context) {
        val loggingCategory: PreferenceCategory? = findPreference("logging")
        if (loggingCategory == null) return

        val logLevelListPreference: ListPreference? = findPreference("log_level")
        if (logLevelListPreference != null) {
            val preferences = TermuxFloatAppSharedPreferences.build(context, true) ?: return

            setLogLevelListPreferenceData(
                logLevelListPreference,
                context,
                preferences.getLogLevel(true)
            )
            loggingCategory.addPreference(logLevelListPreference)
        }
    }
}

internal class DebuggingPreferencesDataStore private constructor(private val context: Context) : PreferenceDataStore() {

    private val preferences: TermuxFloatAppSharedPreferences? = TermuxFloatAppSharedPreferences.build(context, true)

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

    override fun getString(key: String?, defValue: String?): String? {
        if (preferences == null || key == null) return null

        return when (key) {
            "log_level" -> preferences.getLogLevel(true).toString()
            else -> null
        }
    }

    override fun putString(key: String?, value: String?) {
        if (preferences == null || key == null) return

        when (key) {
            "log_level" -> {
                if (value != null) {
                    preferences.setLogLevel(context, value.toInt(), true)
                }
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (preferences == null || key == null) return

        when (key) {
            "terminal_view_key_logging_enabled" ->
                preferences.setTerminalViewKeyLoggingEnabled(value, true)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (preferences == null) return false

        return when (key) {
            "terminal_view_key_logging_enabled" -> preferences.isTerminalViewKeyLoggingEnabled(true)
            else -> false
        }
    }
}
