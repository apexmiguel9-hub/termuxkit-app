package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences

@Keep
class TermuxTaskerPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TermuxTaskerPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_tasker_preferences, rootKey)
    }

}

internal class TermuxTaskerPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val context: Context
    private val preferences: TermuxTaskerAppSharedPreferences

    init {
        context = context.applicationContext
        preferences = TermuxTaskerAppSharedPreferences.build(context, true)
    }

    companion object {
        private var instance: TermuxTaskerPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TermuxTaskerPreferencesDataStore {
            if (instance == null) {
                instance = TermuxTaskerPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }
}
