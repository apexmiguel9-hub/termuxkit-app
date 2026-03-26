package com.termux.shared.data

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.NonNull
import java.util.Arrays

object IntentUtils {

    private const val LOG_TAG = "IntentUtils"

    /**
     * Get a [String] extra from an [Intent] if its not `null` or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @param throwExceptionIfNotSet If set to `true`, then an exception will be thrown if extra
     *                               is not set.
     * @return Returns the [String] extra if set, otherwise `null`.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getStringExtraIfSet(
        @NonNull intent: Intent,
        key: String,
        def: String?,
        throwExceptionIfNotSet: Boolean
    ): String? {
        val value = getStringExtraIfSet(intent, key, def)
        if (value == null && throwExceptionIfNotSet)
            throw Exception("The \"$key\" key string value is null or empty")
        return value
    }

    /**
     * Get a [String] extra from an [Intent] if its not `null` or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the [String] extra if set, otherwise `null`.
     */
    fun getStringExtraIfSet(@NonNull intent: Intent, key: String, def: String?): String? {
        val value = intent.getStringExtra(key)
        return if (value == null || value.isEmpty()) {
            if (def != null && def.isNotEmpty()) def else null
        } else {
            value
        }
    }

    /**
     * Get an `Integer` from an [Intent] stored as a [String] extra if its not
     * `null` or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the `Integer` extra if set, otherwise `null`.
     */
    fun getIntegerExtraIfSet(@NonNull intent: Intent, key: String, def: Int?): Int? {
        return try {
            val value = intent.getStringExtra(key)
            if (value == null || value.isEmpty()) {
                def
            } else {
                value.toInt()
            }
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get a [String]`[]` extra from an [Intent] if its not `null` or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @param throwExceptionIfNotSet If set to `true`, then an exception will be thrown if extra
     *                               is not set.
     * @return Returns the [String]`[]` extra if set, otherwise `null`.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getStringArrayExtraIfSet(
        @NonNull intent: Intent,
        key: String,
        def: Array<String>?,
        throwExceptionIfNotSet: Boolean
    ): Array<String>? {
        val value = getStringArrayExtraIfSet(intent, key, def)
        if (value == null && throwExceptionIfNotSet)
            throw Exception("The \"$key\" key string array is null or empty")
        return value
    }

    /**
     * Get a [String]`[]` extra from an [Intent] if its not `null` or empty.
     *
     * @param intent The [Intent] to get the extra from.
     * @param key The [String] key name.
     * @param def The default value if extra is not set.
     * @return Returns the [String]`[]` extra if set, otherwise `null`.
     */
    fun getStringArrayExtraIfSet(intent: Intent, key: String, def: Array<String>?): Array<String>? {
        val value = intent.getStringArrayExtra(key)
        return if (value == null || value.isEmpty()) {
            if (def != null && def.isNotEmpty()) def else null
        } else {
            value
        }
    }

    fun getIntentString(intent: Intent?): String? {
        if (intent == null) return null

        return intent.toString() + "\n" + getBundleString(intent.extras)
    }

    fun getBundleString(bundle: Bundle?): String {
        if (bundle == null || bundle.isEmpty) return "Bundle[]"

        val bundleString = StringBuilder("Bundle[\n")
        var first = true
        for (key in bundle.keySet()) {
            if (!first)
                bundleString.append("\n")

            bundleString.append(key).append(": `")

            val value = bundle.get(key)
            when (value) {
                is IntArray -> bundleString.append(Arrays.toString(value))
                is ByteArray -> bundleString.append(Arrays.toString(value))
                is BooleanArray -> bundleString.append(Arrays.toString(value))
                is ShortArray -> bundleString.append(Arrays.toString(value))
                is LongArray -> bundleString.append(Arrays.toString(value))
                is FloatArray -> bundleString.append(Arrays.toString(value))
                is DoubleArray -> bundleString.append(Arrays.toString(value))
                is Array<*> -> bundleString.append(Arrays.toString(value))
                is Bundle -> bundleString.append(getBundleString(value))
                else -> bundleString.append(value)
            }

            bundleString.append("`")

            first = false
        }

        bundleString.append("\n]")
        return bundleString.toString()
    }
}
