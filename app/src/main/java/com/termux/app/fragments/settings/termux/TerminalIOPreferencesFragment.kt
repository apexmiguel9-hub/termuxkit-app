package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.termux.R
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
class TerminalIOPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TerminalIOPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_terminal_io_preferences, rootKey)
    }

}

internal class TerminalIOPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val context: Context
    private val preferences: TermuxAppSharedPreferences

    init {
        context = context.applicationContext
        preferences = TermuxAppSharedPreferences.build(context, true)
    }

    companion object {
        private var instance: TerminalIOPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TerminalIOPreferencesDataStore {
            if (instance == null) {
                instance = TerminalIOPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (preferences == null) return
        if (key == null) return

        when (key) {
            "soft_keyboard_enabled" -> preferences.setSoftKeyboardEnabled(value)
            "soft_keyboard_enabled_only_if_no_hardware" -> preferences.setSoftKeyboardEnabledOnlyIfNoHardware(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (preferences == null) return false

        return when (key) {
            "soft_keyboard_enabled" -> preferences.isSoftKeyboardEnabled
            "soft_keyboard_enabled_only_if_no_hardware" -> preferences.isSoftKeyboardEnabledOnlyIfNoHardware
            else -> false
        }
    }
}
