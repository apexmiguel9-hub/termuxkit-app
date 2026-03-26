package com.termux.shared.termux.settings.properties

import android.content.Context
import androidx.annotation.NonNull
import com.termux.shared.termux.TermuxConstants

open class TermuxAppSharedProperties private constructor(
    @NonNull context: Context
) : TermuxSharedProperties(
    context,
    TermuxConstants.TERMUX_APP_NAME,
    TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST,
    TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST,
    // Fixed: SharedPropertiesParserClient
    TermuxSharedProperties.SharedPropertiesParserClient()
) {

    companion object {
        private var properties: TermuxAppSharedProperties? = null

        /**
         * Initialize the [properties] and load properties from disk.
         *
         * @param context The [Context] for operations.
         * @return Returns the [TermuxAppSharedProperties].
         */
        @JvmStatic
        fun init(@NonNull context: Context): TermuxAppSharedProperties {
            if (properties == null)
                properties = TermuxAppSharedProperties(context)

            return properties ?: throw IllegalStateException("Properties not initialized")
        }

        /**
         * Get the [properties].
         *
         * @return Returns the [TermuxAppSharedProperties].
         */
        @JvmStatic
        fun getProperties(): TermuxAppSharedProperties? = properties
    }
}
