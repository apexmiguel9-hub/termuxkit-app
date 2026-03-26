package com.termux.shared.termux.extrakeys

import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import org.json.JSONException
import org.json.JSONObject
import java.util.Arrays
import java.util.stream.Collectors

class ExtraKeyButton {

    /** The key name for the name of the extra key if using a dict to define the extra key. {key: name, ...} */
    companion object {
        const val KEY_KEY_NAME = "key"

        /** The key name for the macro value of the extra key if using a dict to define the extra key. {macro: value, ...} */
        const val KEY_MACRO = "macro"

        /** The key name for the alternate display name of the extra key if using a dict to define the extra key. {display: name, ...} */
        const val KEY_DISPLAY_NAME = "display"

        /** The key name for the nested dict to define popup extra key info if using a dict to define the extra key. {popup: {key: name, ...}, ...} */
        const val KEY_POPUP = "popup"

        /**
         * Replace the alias with its actual key name if found in extraKeyAliasMap.
         */
        @JvmStatic
        fun replaceAlias(@NonNull extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap, key: String): String {
            return extraKeyAliasMap[key, key]
        }
    }

    /**
     * The key that will be sent to the terminal, either a control character, like defined in
     * [ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS] (LEFT, RIGHT, PGUP...) or some text.
     */
    val key: String

    /**
     * If the key is a macro, i.e. a sequence of keys separated by space.
     */
    val macro: Boolean

    /**
     * The text that will be displayed on the button.
     */
    val display: String

    /**
     * The [ExtraKeyButton] containing the information of the popup button (triggered by swipe up).
     */
    @Nullable
    val popup: ExtraKeyButton?

    /**
     * Initialize a [ExtraKeyButton].
     *
     * @param config The [JSONObject] containing the info to create the [ExtraKeyButton].
     * @param extraKeyDisplayMap The [ExtraKeysConstants.ExtraKeyDisplayMap] that defines the
     *                           display text mapping for the keys if a custom value is not defined
     *                           by [KEY_DISPLAY_NAME].
     * @param extraKeyAliasMap The [ExtraKeysConstants.ExtraKeyDisplayMap] that defines the
     *                           aliases for the actual key names.
     */
    @Throws(JSONException::class)
    constructor(
        @NonNull config: JSONObject,
        @NonNull extraKeyDisplayMap: ExtraKeysConstants.ExtraKeyDisplayMap,
        @NonNull extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
    ) : this(config, null, extraKeyDisplayMap, extraKeyAliasMap)

    /**
     * Initialize a [ExtraKeyButton].
     *
     * @param config The [JSONObject] containing the info to create the [ExtraKeyButton].
     * @param popup The [ExtraKeyButton] optional [popup] button.
     * @param extraKeyDisplayMap The [ExtraKeysConstants.ExtraKeyDisplayMap] that defines the
     *                           display text mapping for the keys if a custom value is not defined
     *                           by [KEY_DISPLAY_NAME].
     * @param extraKeyAliasMap The [ExtraKeysConstants.ExtraKeyDisplayMap] that defines the
     *                           aliases for the actual key names.
     */
    @Throws(JSONException::class)
    constructor(
        @NonNull config: JSONObject,
        @Nullable popup: ExtraKeyButton?,
        @NonNull extraKeyDisplayMap: ExtraKeysConstants.ExtraKeyDisplayMap,
        @NonNull extraKeyAliasMap: ExtraKeysConstants.ExtraKeyDisplayMap
    ) {
        val keyFromConfig = getStringFromJson(config, KEY_KEY_NAME)
        val macroFromConfig = getStringFromJson(config, KEY_MACRO)
        val keys: Array<String>
        when {
            keyFromConfig != null && macroFromConfig != null -> {
                throw JSONException("Both key and macro can't be set for the same key. key: \"$keyFromConfig\", macro: \"$macroFromConfig\"")
            }
            keyFromConfig != null -> {
                keys = arrayOf(keyFromConfig)
                macro = false
            }
            macroFromConfig != null -> {
                keys = macroFromConfig.split(" ").toTypedArray()
                macro = true
            }
            else -> {
                throw JSONException("All keys have to specify either key or macro")
            }
        }

        for (i in keys.indices) {
            keys[i] = ExtraKeyButton.replaceAlias(extraKeyAliasMap, keys[i])
        }

        this.key = TextUtils.join(" ", keys) as String

        val displayFromConfig = getStringFromJson(config, KEY_DISPLAY_NAME)
        this.display = if (displayFromConfig != null) {
            displayFromConfig
        } else {
            Arrays.stream(keys)
                .map { key: String -> extraKeyDisplayMap[key, key] }
                .collect(Collectors.joining(" "))
        }

        this.popup = popup
    }

    fun getStringFromJson(@NonNull config: JSONObject, @NonNull key: String): String? {
        return try {
            config.getString(key)
        } catch (e: JSONException) {
            null
        }
    }

    /** Get [key]. */
    fun getKey(): String = key

    /** Check whether a [macro] is defined or not. */
    fun isMacro(): Boolean = macro

    /** Get [display]. */
    fun getDisplay(): String = display

    /** Get [popup]. */
    @Nullable
    fun getPopup(): ExtraKeyButton? = popup
}
