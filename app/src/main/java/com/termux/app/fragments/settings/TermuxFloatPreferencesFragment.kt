package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences

@Keep
class TermuxFloatPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TermuxFloatPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_float_preferences, rootKey)
    }

}

internal class TermuxFloatPreferencesDataStore private constructor(private val context: Context) : PreferenceDataStore() {

    private val preferences: TermuxFloatAppSharedPreferences = TermuxFloatAppSharedPreferences.build(context, true)

    companion object {
        private var instance: TermuxFloatPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TermuxFloatPreferencesDataStore {
            if (instance == null) {
                instance = TermuxFloatPreferencesDataStore(context.applicationContext)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }
}
