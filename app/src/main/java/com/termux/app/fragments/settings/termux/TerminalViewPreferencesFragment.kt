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
class TerminalViewPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TerminalViewPreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey)
    }

}

internal class TerminalViewPreferencesDataStore private constructor(context: Context) : PreferenceDataStore() {

    private val context: Context
    private val preferences: TermuxAppSharedPreferences

    init {
        context = context.applicationContext
        preferences = TermuxAppSharedPreferences.build(context, true)
    }

    companion object {
        private var instance: TerminalViewPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TerminalViewPreferencesDataStore {
            if (instance == null) {
                instance = TerminalViewPreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (preferences == null) return
        if (key == null) return

        when (key) {
            "terminal_margin_adjustment" -> preferences.setTerminalMarginAdjustment(value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (preferences == null) return false

        return when (key) {
            "terminal_margin_adjustment" -> preferences.isTerminalMarginAdjustmentEnabled
            else -> false
        }
    }
}
