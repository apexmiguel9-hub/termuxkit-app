package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
class TermuxPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context
        if (context == null) return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TermuxPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_preferences, rootKey)
    }

}

internal class TermuxPreferencesDataStore(context: Context) : PreferenceDataStore() {

    private val context: Context = context
    private val preferences: TermuxAppSharedPreferences = TermuxAppSharedPreferences.build(context, true)

    companion object {
        private var instance: TermuxPreferencesDataStore? = null

        @Synchronized
        fun getInstance(context: Context): TermuxPreferencesDataStore {
            if (instance == null) {
                instance = TermuxPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

}
