package com.termux.app.fragments.settings.termux

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.termux.R
import com.termux.app.style.TermuxBackgroundManager
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Keep
open class TermuxStylePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = context ?: return

        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = TermuxStylePreferencesDataStore.getInstance(context)

        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey)

        configureBackgroundPreferences(context)
    }

    /**
     * Configure background preferences and make appropriate changes in the state of components.
     *
     * @param context The context for operations.
     */
    private fun configureBackgroundPreferences(context: Context) {
        val backgroundImagePreference: SwitchPreferenceCompat? = findPreference("background_image_enabled")

        if (backgroundImagePreference != null) {
            val preferences = TermuxAppSharedPreferences.build(context, true)

            if (preferences == null) return

            // If background image preference is disabled and background images are
            // missing, then don't allow user to enable it from setting.
            if (!preferences.isBackgroundImageEnabled && !TermuxBackgroundManager.isImageFilesExist(context, false)) {
                backgroundImagePreference.isEnabled = false
            }
        }
    }
}

internal class TermuxStylePreferencesDataStore private constructor(
    private val context: Context,
    private val preferences: TermuxAppSharedPreferences?
) : PreferenceDataStore() {

    companion object {
        private var instance: TermuxStylePreferencesDataStore? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TermuxStylePreferencesDataStore {
            if (instance == null) {
                instance = TermuxStylePreferencesDataStore(context)
            }
            return instance ?: throw IllegalStateException("Instance not initialized")
        }
    }

    init {
        preferences = TermuxAppSharedPreferences.build(context, true)
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (preferences == null || key == null) return

        when (key) {
            "background_image_enabled" -> preferences.isBackgroundImageEnabled = value
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (preferences == null) return false

        return when (key) {
            "background_image_enabled" -> preferences.isBackgroundImageEnabled
            else -> false
        }
    }
}
