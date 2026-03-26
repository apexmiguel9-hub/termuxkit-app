package com.termux.app.fragments.settings.termux_widget

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences

@Keep
class DebuggingPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return
        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = DebuggingPreferencesDataStore.getInstance(context)
        setPreferencesFromResource(R.xml.termux_widget_debugging_preferences, rootKey)
        configureLoggingPreferences(context)
    }

    private fun configureLoggingPreferences(context: Context) {
        val loggingCategory = findPreference<androidx.preference.PreferenceCategory>("logging") ?: return
        val logLevelListPreference = findPreference<ListPreference>("log_level") ?: return
        val preferences = TermuxWidgetAppSharedPreferences.build(context, true) ?: return
        com.termux.app.fragments.settings.termux.DebuggingPreferencesFragment
            .setLogLevelListPreferenceData(logLevelListPreference, context, preferences.getLogLevel(true))
        loggingCategory.addPreference(logLevelListPreference)
    }
}

internal class DebuggingPreferencesDataStore private constructor(
    private val context: Context
) : PreferenceDataStore() {

    private val preferences: TermuxWidgetAppSharedPreferences? =
        TermuxWidgetAppSharedPreferences.build(context, true)

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
            "log_level" -> if (value != null) preferences.setLogLevel(context, value.toInt(), true)
        }
    }
}
