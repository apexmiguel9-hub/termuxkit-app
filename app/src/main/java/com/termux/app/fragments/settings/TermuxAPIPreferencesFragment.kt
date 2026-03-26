package com.termux.app.fragments.settings

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences

@Keep
class TermuxAPIPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        preferenceManager.preferenceDataStore = TermuxAPIPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_api_preferences, rootKey)
    }

}

class TermuxAPIPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val context: Context = context
    private val preferences: TermuxAPIAppSharedPreferences = TermuxAPIAppSharedPreferences.build(context, true)

    companion object {
        @Volatile
        private var instance: TermuxAPIPreferencesDataStore? = null

        fun getInstance(context: Context): TermuxAPIPreferencesDataStore {
            return instance ?: synchronized(this) {
                instance ?: TermuxAPIPreferencesDataStore(context).also { instance = it }
            }
        }
    }

}
