package com.termux.app.fragments.settings.termux_api

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.*
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences

@Keep
class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_api_debugging_preferences, rootKey)

        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(context: Context) {
        val loggingCategory = findPreference<PreferenceCategory>("logging") ?: return

        val logLevelListPreference = findPreference<ListPreference>("log_level")
        if (logLevelListPreference != null) {
            val preferences = TermuxAPIAppSharedPreferences.build(context, true) ?: return

            com.termux.app.fragments.settings.termux.DebuggingPreferencesFragment.setLogLevelListPreferenceData(
                logLevelListPreference, context, preferences.getLogLevel(true)
            )
            loggingCategory.addPreference(logLevelListPreference)
        }
    }
}

class DebuggingPreferencesDataStore private constructor(private val context: Context) : PreferenceDataStore() {

    private val preferences: TermuxAPIAppSharedPreferences? = TermuxAPIAppSharedPreferences.build(context, true)

    companion object {
        private var instance: DebuggingPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): DebuggingPreferencesDataStore {
            if (instance == null) {
                instance = DebuggingPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (preferences == null) return null
        if (key == null) return null

        return when (key) {
            "log_level" -> preferences.getLogLevel(true).toString()
            else -> null
        }
    }

    override fun putString(key: String?, value: String?) {
        if (preferences == null) return
        if (key == null) return

        when (key) {
            "log_level" -> {
                if (value != null) {
                    preferences.setLogLevel(context, value.toInt(), true)
                }
            }
            else -> return
        }
    }
}
