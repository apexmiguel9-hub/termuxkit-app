package com.termux.shared.termux.settings.properties

import android.content.Context
import androidx.annotation.Keep
import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import com.termux.shared.settings.properties.SharedProperties
import com.termux.shared.settings.properties.SharedPropertiesParser
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.Properties

abstract class TermuxSharedProperties protected constructor(
    protected var context: Context,
    protected val label: String,
    protected val propertiesFilePaths: List<String>?,
    protected val propertiesList: Set<String>,
    protected val sharedPropertiesParser: SharedPropertiesParser
) {
    protected var propertiesFile: File? = null
    protected var sharedProperties: SharedProperties? = null

    companion object {
        const val LOG_TAG = "TermuxSharedProperties"

        /**
         * Get the internal [Object] value for the key passed from the first file found in
         * [TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST]. The [Properties] object is
         * read directly from the file and internal value is returned for the property value against the key.
         *
         * @param context The context for operations.
         * @param key The key for which the internal object is required.
         * @return Returns the [Object] object. This will be `null` if key is not found or
         * the object stored against the key is `null`.
         */
        fun getTermuxInternalPropertyValue(context: Context, key: String): Any? {
            return SharedProperties.getInternalProperty(context,
                SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG),
                key, SharedPropertiesParserClient())
        }

        /**
         * The class that implements the [SharedPropertiesParser] interface.
         */
        @Keep
        class SharedPropertiesParserClient : SharedPropertiesParser {
            @Keep
            override fun preProcessPropertiesOnReadFromDisk(context: Context, properties: Properties): Properties {
                return replaceUseBlackUIProperty(properties)
            }

            /**
             * Override the
             * [SharedPropertiesParser.getInternalPropertyValueFromValue]
             * interface function.
             */
            @Keep
            override fun getInternalPropertyValueFromValue(context: Context, key: String?, value: String?): Any? {
                return getInternalTermuxPropertyValueFromValue(context, key ?: "", value)
            }
        }

        @Keep
        fun replaceUseBlackUIProperty(properties: Properties): Properties {
            val useBlackUIStringValue = properties.getProperty(TermuxPropertyConstants.KEY_USE_BLACK_UI) ?: return properties

            Logger.logWarn(LOG_TAG, "Removing deprecated property ${TermuxPropertyConstants.KEY_USE_BLACK_UI}=$useBlackUIStringValue")
            properties.remove(TermuxPropertyConstants.KEY_USE_BLACK_UI)

            // If KEY_NIGHT_MODE is not set
            if (properties.getProperty(TermuxPropertyConstants.KEY_NIGHT_MODE) == null) {
                val useBlackUI = SharedProperties.getBooleanValueForStringValue(useBlackUIStringValue)
                if (useBlackUI != null) {
                    val termuxAppTheme = if (useBlackUI) TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE else TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE
                    Logger.logWarn(LOG_TAG, "Replacing deprecated property ${TermuxPropertyConstants.KEY_USE_BLACK_UI}=$useBlackUI with ${TermuxPropertyConstants.KEY_NIGHT_MODE}=$termuxAppTheme")
                    properties[TermuxPropertyConstants.KEY_NIGHT_MODE] = termuxAppTheme
                }
            }

            return properties
        }

        /**
         * A static function that should return the internal termux [Object] for a key/value pair
         * read from properties file.
         *
         * @param context The context for operations.
         * @param key The key for which the internal object is required.
         * @param value The literal value for the property found is the properties file.
         * @return Returns the internal termux [Object] object.
         */
        fun getInternalTermuxPropertyValueFromValue(context: Context, key: String, value: String?): Any? {
            if (key.isEmpty()) return null
            /*
              For keys where a MAP_* is checked by respective functions. Note that value to this function
              would actually be the key for the MAP_*:
              - If the value is currently null, then searching MAP_* should also return null and internal default value will be used.
              - If the value is not null and does not exist in MAP_*, then internal default value will be used.
              - If the value is not null and does exist in MAP_*, then internal value returned by map will be used.
             */
            return when (key) {
                /* int */
                TermuxPropertyConstants.KEY_BELL_BEHAVIOUR -> getBellBehaviourInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT -> getDeleteTMPDIRFilesOlderThanXDaysOnExitInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE -> getTerminalCursorBlinkRateInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE -> getTerminalCursorStyleInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_TERMINAL_MARGIN_HORIZONTAL -> getTerminalMarginHorizontalInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_TERMINAL_MARGIN_VERTICAL -> getTerminalMarginVerticalInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS -> getTerminalTranscriptRowsInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR -> getBackgroundOverlayInternalPropertyValueFromValue(value)

                /* float */
                TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR -> getTerminalToolbarHeightScaleFactorInternalPropertyValueFromValue(value)

                /* Integer (may be null) */
                TermuxPropertyConstants.KEY_SHORTCUT_CREATE_SESSION,
                TermuxPropertyConstants.KEY_SHORTCUT_NEXT_SESSION,
                TermuxPropertyConstants.KEY_SHORTCUT_PREVIOUS_SESSION,
                TermuxPropertyConstants.KEY_SHORTCUT_RENAME_SESSION -> getCodePointForSessionShortcuts(key, value)

                /* String (may be null) */
                TermuxPropertyConstants.KEY_BACK_KEY_BEHAVIOUR -> getBackKeyBehaviourInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY -> getDefaultWorkingDirectoryInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_EXTRA_KEYS -> getExtraKeysInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE -> getExtraKeysStyleInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_NIGHT_MODE -> getNightModeInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR -> getSoftKeyboardToggleBehaviourInternalPropertyValueFromValue(value)
                TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR -> getVolumeKeysBehaviourInternalPropertyValueFromValue(value)

                else -> {
                    // default false boolean behaviour
                    if (TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(key))
                        SharedProperties.getBooleanValueForStringValue(key, value, false, true, LOG_TAG)
                    // default true boolean behaviour
                    else if (TermuxPropertyConstants.TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(key))
                        SharedProperties.getBooleanValueForStringValue(key, value, true, true, LOG_TAG)
                    // just use String object as is (may be null)
                    else value
                }
            }
        }

        /**
         * Returns the internal value after mapping it based on
         * `TermuxPropertyConstants.MAP_BELL_BEHAVIOUR` if the value is not `null`
         * and is valid, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_BELL_BEHAVIOUR].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getBellBehaviourInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_BELL_BEHAVIOUR, TermuxPropertyConstants.MAP_BELL_BEHAVIOUR, SharedProperties.toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_BELL_BEHAVIOUR, true, LOG_TAG) as Int

        /**
         * Returns the int for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MIN] and
         * [TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getDeleteTMPDIRFilesOlderThanXDaysOnExitInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT,
                DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT),
                TermuxPropertyConstants.DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT,
                TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MIN,
                TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the int for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN] and
         * [TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalCursorBlinkRateInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE,
                DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE),
                TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE,
                TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the internal value after mapping it based on
         * [TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE] if the value is not `null`
         * and is valid, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalCursorStyleInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE, TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE, SharedProperties.toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE, true, LOG_TAG) as Int

        /**
         * Returns the int for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MIN] and
         * [TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalMarginHorizontalInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_HORIZONTAL,
                DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL),
                TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL,
                TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the int for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MIN] and
         * [TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalMarginVerticalInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_VERTICAL,
                DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL),
                TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL,
                TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the int for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN] and
         * [TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalTranscriptRowsInternalPropertyValueFromValue(value: String?): Int =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS,
                DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS),
                TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS,
                TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the int for the color value if its not null and is in form of `#AARRGGBB`.
         * If the value does not contain alpha value then it will borrow it from [TermuxPropertyConstants.DEFAULT_IVALUE_BACKGROUND_OVERLAY_COLOR]
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getBackgroundOverlayInternalPropertyValueFromValue(value: String?): Int =
            DataUtils.getIntColorFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_BACKGROUND_OVERLAY_COLOR, true)

        /**
         * Returns the float for the value if its not null and is between
         * [TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN] and
         * [TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX],
         * otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getTerminalToolbarHeightScaleFactorInternalPropertyValueFromValue(value: String?): Float =
            SharedProperties.getDefaultIfNotInRange(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
                DataUtils.getFloatFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR),
                TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
                TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX,
                true, true, LOG_TAG)

        /**
         * Returns the code point for the value if key is not `null` and value is not `null` and is valid,
         * otherwise returns `null`.
         *
         * @param key The key for session shortcut.
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getCodePointForSessionShortcuts(key: String, value: String?): Int? {
            if (value == null) return null
            val parts = value.lowercase().trim().split("\\+".toRegex())
            val input = if (parts.size == 2) parts[1].trim() else null
            if (!(parts.size == 2 && parts[0].trim() == "ctrl") || input.isNullOrEmpty() || input.length > 2) {
                Logger.logError(LOG_TAG, "Keyboard shortcut '$key' is not Ctrl+<something>")
                return null
            }

            val c = input[0]
            var codePoint = c.code
            if (Character.isLowSurrogate(c)) {
                if (input.length != 2 || Character.isHighSurrogate(input[1])) {
                    Logger.logError(LOG_TAG, "Keyboard shortcut '$key' is not Ctrl+<something>")
                    return null
                } else {
                    codePoint = Character.toCodePoint(input[1], c)
                }
            }

            return codePoint
        }

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_BACK_KEY_BEHAVIOUR].
         *
         * @param value [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getBackKeyBehaviourInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_BACK_KEY_BEHAVIOUR, TermuxPropertyConstants.MAP_BACK_KEY_BEHAVIOUR, SharedProperties.toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_BACK_KEY_BEHAVIOUR, true, LOG_TAG) as String?

        /**
         * Returns the path itself if a directory exists at it and is readable, otherwise returns
         *  [TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY].
         *
         * @param path The [String] path to check.
         * @return Returns the internal value for value.
         */
        fun getDefaultWorkingDirectoryInternalPropertyValueFromValue(path: String?): String {
            if (path.isNullOrEmpty()) return TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY
            val workDir = File(path)
            if (!workDir.exists() || !workDir.isDirectory || !workDir.canRead()) {
                // Fallback to default directory if user configured working directory does not exist,
                // is not a directory or is not readable.
                Logger.logError(LOG_TAG, "The path \"$path\" for the key \"${TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY}\" does not exist, is not a directory or is not readable. Using default value \"${TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY}\" instead.")
                return TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY
            }
            return path
        }

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS].
         *
         * @param value The [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getExtraKeysInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNullOrEmpty(value, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS)

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE].
         *
         * @param value [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getExtraKeysStyleInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNullOrEmpty(value, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE)

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_NIGHT_MODE].
         *
         * @param value [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getNightModeInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_NIGHT_MODE,
                TermuxPropertyConstants.MAP_NIGHT_MODE, SharedProperties.toLowerCase(value),
                TermuxPropertyConstants.DEFAULT_IVALUE_NIGHT_MODE, true, LOG_TAG) as String?

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR].
         *
         * @param value [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getSoftKeyboardToggleBehaviourInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, TermuxPropertyConstants.MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, SharedProperties.toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, true, LOG_TAG) as String?

        /**
         * Returns the value itself if it is not `null`, otherwise returns [TermuxPropertyConstants.DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR].
         *
         * @param value [String] value to convert.
         * @return Returns the internal value for value.
         */
        fun getVolumeKeysBehaviourInternalPropertyValueFromValue(value: String?): String? =
            SharedProperties.getDefaultIfNotInMap(TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR, TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR, SharedProperties.toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR, true, LOG_TAG) as String?
    }

    init {
        context = context.applicationContext
        loadTermuxPropertiesFromDisk()
    }

    /**
     * Reload the termux properties from disk into an in-memory cache.
     */
    @Synchronized
    fun loadTermuxPropertiesFromDisk() {
        // Properties files must be searched everytime since no file may exist when constructor is
        // called or a higher priority file may have been created afterward. Otherwise, if no file
        // was found, then default props would keep loading, since sharedProperties would be null. #2836
        propertiesFile = SharedProperties.getPropertiesFileFromList(propertiesFilePaths, LOG_TAG)
        sharedProperties = null
        sharedProperties = SharedProperties(context, propertiesFile, propertiesList, sharedPropertiesParser)

        sharedProperties?.loadPropertiesFromDisk()
        dumpPropertiesToLog()
        dumpInternalPropertiesToLog()
    }

    /**
     * Get the [Properties] from the [propertiesFile] file.
     *
     * @param cached If `true`, then the [Properties] in-memory cache is returned.
     *               Otherwise the [Properties] object is read directly from the
     *               [propertiesFile] file.
     * @return Returns the [Properties] object. It will be `null` if an exception is
     * raised while reading the file.
     */
    fun getProperties(cached: Boolean): Properties? = sharedProperties?.getProperties(cached)

    /**
     * Get the [String] value for the key passed from the [propertiesFile] file.
     *
     * @param key The key to read.
     * @param def The default value.
     * @param cached If `true`, then the value is returned from the the [Properties] in-memory cache.
     *               Otherwise the [Properties] object is read directly from the file
     *               and value is returned from it against the key.
     * @return Returns the [String] object. This will be `null` if key is not found.
     */
    fun getPropertyValue(key: String, def: String?, cached: Boolean): String? =
        SharedProperties.getDefaultIfNull(sharedProperties?.getProperty(key, cached), def)

    /**
     * A function to check if the value is `true` for [Properties] key read from
     * the [propertiesFile] file.
     *
     * @param key The key to read.
     * @param cached If `true`, then the value is checked from the the [Properties] in-memory cache.
     *               Otherwise the [Properties] object is read directly from the file
     *               and value is checked from it.
     * @param logErrorOnInvalidValue If `true`, then an error will be logged if key value
     *                               was found in [Properties] but was invalid.
     * @return Returns the `true` if the [Properties] key [String] value equals "true",
     * regardless of case. If the key does not exist in the file or does not equal "true", then
     * `false` will be returned.
     */
    fun isPropertyValueTrue(key: String, cached: Boolean, logErrorOnInvalidValue: Boolean): Boolean =
        SharedProperties.getBooleanValueForStringValue(key, getPropertyValue(key, null, cached), false, logErrorOnInvalidValue, LOG_TAG)

    /**
     * A function to check if the value is `false` for [Properties] key read from
     * the [propertiesFile] file.
     *
     * @param key The key to read.
     * @param cached If `true`, then the value is checked from the the [Properties] in-memory cache.
     *               Otherwise the [Properties] object is read directly from the file
     *               and value is checked from it.
     * @param logErrorOnInvalidValue If `true`, then an error will be logged if key value
     *                               was found in [Properties] but was invalid.
     * @return Returns `true` if the [Properties] key [String] value equals "false",
     * regardless of case. If the key does not exist in the file or does not equal "false", then
     * `true` will be returned.
     */
    fun isPropertyValueFalse(key: String, cached: Boolean, logErrorOnInvalidValue: Boolean): Boolean =
        SharedProperties.getInvertedBooleanValueForStringValue(key, getPropertyValue(key, null, cached), true, logErrorOnInvalidValue, LOG_TAG)

    /**
     * Get the internal value [Object] [HashMap] in-memory cache for the
     * [propertiesFile] file. A call to [loadTermuxPropertiesFromDisk] must be made
     * before this.
     *
     * @return Returns a copy of [Map] object.
     */
    fun getInternalProperties(): Map<String, Any?>? = sharedProperties?.getInternalProperties()

    /**
     * Get the internal [Object] value for the key passed from the [propertiesFile] file.
     * If cache is `true`, then value is returned from the [HashMap] in-memory cache,
     * so a call to [loadTermuxPropertiesFromDisk] must be made before this.
     *
     * @param key The key to read from the [HashMap] in-memory cache.
     * @param cached If `true`, then the value is returned from the the [HashMap] in-memory cache,
     *               but if the value is null, then an attempt is made to return the default value.
     *               If `false`, then the [Properties] object is read directly from the file
     *               and internal value is returned for the property value against the key.
     * @return Returns the [Object] object. This will be `null` if key is not found or
     * the object stored against the key is `null`.
     */
    fun getInternalPropertyValue(key: String, cached: Boolean): Any? {
        return if (cached) {
            val value = sharedProperties?.getInternalProperty(key)
            // If the value is not null since key was found or if the value was null since the
            // object stored for the key was itself null, we detect the later by checking if the key
            // exists in the map.
            if (value != null || sharedProperties?.getInternalProperties()?.containsKey(key) == true) {
                value
            } else {
                // This should not happen normally unless map was modified after the
                // [loadTermuxPropertiesFromDisk] call
                // A null value can still be returned by
                // [SharedPropertiesParser.getInternalPropertyValueFromValue] for some keys
                val defaultValue = getInternalTermuxPropertyValueFromValue(context, key, null)
                Logger.logWarn(LOG_TAG, "The value for \"$key\" not found in SharedProperties cache, force returning default value: `$defaultValue`")
                defaultValue
            }
        } else {
            // We get the property value directly from file and return its internal value
            getInternalTermuxPropertyValueFromValue(context, key, sharedProperties?.getProperty(key, false))
        }
    }

    fun shouldAllowExternalApps(): Boolean = getInternalPropertyValue(TermuxConstants.PROP_ALLOW_EXTERNAL_APPS, true) as Boolean

    fun isFileShareReceiverDisabled(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_DISABLE_FILE_SHARE_RECEIVER, true) as Boolean

    fun isFileViewReceiverDisabled(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_DISABLE_FILE_VIEW_RECEIVER, true) as Boolean

    fun areHardwareKeyboardShortcutsDisabled(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS, true) as Boolean

    fun areTerminalSessionChangeToastsDisabled(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST, true) as Boolean

    fun isEnforcingCharBasedInput(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_ENFORCE_CHAR_BASED_INPUT, true) as Boolean

    fun shouldExtraKeysTextBeAllCaps(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS, true) as Boolean

    fun shouldSoftKeyboardBeHiddenOnStartup(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP, true) as Boolean

    fun shouldRunTermuxAmSocketServer(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_RUN_TERMUX_AM_SOCKET_SERVER, true) as Boolean

    fun shouldOpenTerminalTranscriptURLOnClick(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_ONCLICK_URL_OPEN, true) as Boolean

    fun isUsingCtrlSpaceWorkaround(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_USE_CTRL_SPACE_WORKAROUND, true) as Boolean

    fun isUsingFullScreen(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_USE_FULLSCREEN, true) as Boolean

    fun isUsingFullScreenWorkAround(): Boolean = getInternalPropertyValue(TermuxPropertyConstants.KEY_USE_FULLSCREEN_WORKAROUND, true) as Boolean

    fun getBellBehaviour(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_BELL_BEHAVIOUR, true) as Int

    fun getDeleteTMPDIRFilesOlderThanXDaysOnExit(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT, true) as Int

    fun getTerminalCursorBlinkRate(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE, true) as Int

    fun getTerminalCursorStyle(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE, true) as Int

    fun getTerminalMarginHorizontal(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_HORIZONTAL, true) as Int

    fun getTerminalMarginVertical(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_VERTICAL, true) as Int

    fun getTerminalTranscriptRows(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS, true) as Int

    fun getBackgroundOverlayColor(): Int = getInternalPropertyValue(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR, true) as Int

    fun getTerminalToolbarHeightScaleFactor(): Float = getInternalPropertyValue(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, true) as Float

    fun isBackKeyTheEscapeKey(): Boolean = TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE == getInternalPropertyValue(TermuxPropertyConstants.KEY_BACK_KEY_BEHAVIOUR, true)

    fun getDefaultWorkingDirectory(): String = getInternalPropertyValue(TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY, true) as String

    fun getNightMode(): String = getInternalPropertyValue(TermuxPropertyConstants.KEY_NIGHT_MODE, true) as String

    /** Get the [TermuxPropertyConstants.KEY_NIGHT_MODE] value from the properties file on disk. */
    fun getNightMode(context: Context): String = getTermuxInternalPropertyValue(context, TermuxPropertyConstants.KEY_NIGHT_MODE) as String

    fun shouldEnableDisableSoftKeyboardOnToggle(): Boolean = TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE == getInternalPropertyValue(TermuxPropertyConstants.KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, true)

    fun areVirtualVolumeKeysDisabled(): Boolean = TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME == getInternalPropertyValue(TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR, true)

    fun dumpPropertiesToLog() {
        val properties = getProperties(true)
        val propertiesDump = StringBuilder()

        propertiesDump.append(label).append(" Termux Properties:")
        if (properties != null) {
            for (key in properties.stringPropertyNames()) {
                propertiesDump.append("\n").append(key).append(": `").append(properties[key]).append("`")
            }
        } else {
            propertiesDump.append(" null")
        }

        Logger.logVerbose(LOG_TAG, propertiesDump.toString())
    }

    fun dumpInternalPropertiesToLog() {
        val internalProperties = getInternalProperties() as? HashMap<String, Any?>
        val internalPropertiesDump = StringBuilder()

        internalPropertiesDump.append(label).append(" Internal Properties:")
        if (internalProperties != null) {
            for (key in internalProperties.keys) {
                internalPropertiesDump.append("\n").append(key).append(": `").append(internalProperties[key]).append("`")
            }
        }

        Logger.logVerbose(LOG_TAG, internalPropertiesDump.toString())
    }
}
