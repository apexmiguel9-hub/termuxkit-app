package com.termux.shared.termux.theme
import java.util.*

import android.content.Context
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.settings.properties.TermuxSharedProperties
import com.termux.shared.theme.NightMode

object TermuxThemeUtils {

    /**
     * Get the [TermuxPropertyConstants.KEY_NIGHT_MODE] value from the properties file on disk
     * and set it to app wide night mode value.
     */
    fun setAppNightMode(@NonNull context: Context) {
        // Fixed: getTermuxInternalPropertyValue
        NightMode.setAppNightMode(TermuxSharedProperties.getNightModeInternalPropertyValueFromValue(TermuxSharedProperties.getTermuxInternalPropertyValue(context, TermuxPropertyConstants.KEY_NIGHT_MODE)))
    }

    /**
     * Set name as app wide night mode value.
     */
    fun setAppNightMode(@Nullable name: String?) {
        NightMode.setAppNightMode(name)
    }
}
