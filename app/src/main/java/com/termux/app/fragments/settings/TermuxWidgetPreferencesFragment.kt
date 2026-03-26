package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences

@Keep
class TermuxWidgetPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TermuxWidgetPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_widget_preferences, rootKey)
    }

}

internal class TermuxWidgetPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val context: Context
    private val preferences: TermuxWidgetAppSharedPreferences

    init {
        context = context.applicationContext
        preferences = TermuxWidgetAppSharedPreferences.build(context, true)
    }

    companion object {
        private var instance: TermuxWidgetPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TermuxWidgetPreferencesDataStore {
            if (instance == null) {
                instance = TermuxWidgetPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }
}
