package com.termux.shared.data

import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.google.common.base.Strings
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

object DataUtils {

    /** Max safe limit of data size to prevent TransactionTooLargeException when transferring data
     * inside or to other apps via transactions. */
    @JvmField
    val TRANSACTION_SIZE_LIMIT_IN_BYTES = 100 * 1024 // 100KB

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    /**
     * Get a truncated command output [String].
     *
     * @param text The [String] value to truncate.
     * @param maxLength The maximum length of the returned string.
     * @param fromEnd If `true`, truncate from the end, otherwise from the start.
     * @param onNewline If `true` and truncating from start, cut off at the next newline.
     * @param addPrefix If `true`, add "(truncated) " prefix to the result.
     * @return Returns the truncated [String], or the original if it's shorter than maxLength.
     */
    fun getTruncatedCommandOutput(
        text: String?,
        maxLength: Int,
        fromEnd: Boolean,
        onNewline: Boolean,
        addPrefix: Boolean
    ): String? {
        if (text == null) return null

        val prefix = "(truncated) "
        val adjustedMaxLength = if (addPrefix) maxLength - prefix.length else maxLength

        if (adjustedMaxLength < 0 || text.length < adjustedMaxLength) return text

        var result = if (fromEnd) {
            text.substring(0, adjustedMaxLength)
        } else {
            val cutOffIndex = text.length - adjustedMaxLength
            if (onNewline) {
                val nextNewlineIndex = text.indexOf('\n', cutOffIndex)
                if (nextNewlineIndex != -1 && nextNewlineIndex != text.length - 1)
                    text.substring(nextNewlineIndex + 1)
                else
                    text.substring(cutOffIndex)
            } else {
                text.substring(cutOffIndex)
            }
        }

        if (addPrefix)
            result = prefix + result

        return result
    }

    /**
     * Replace a substring in each item of a [Array].
     *
     * @param array The [Array] to replace in.
     * @param find The substring to replace.
     * @param replace The substring to replace with.
     */
    fun replaceSubStringsInStringArrayItems(array: Array<String>?, find: String, replace: String) {
        if (array == null || array.isEmpty()) return

        for (i in array.indices) {
            array[i] = array[i].replace(find, replace)
        }
    }

    /**
     * Get the `float` from a [String].
     *
     * @param value The [String] value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `float` value after parsing the [String] value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    fun getFloatFromString(value: String?, def: Float): Float {
        if (value == null) return def

        return try {
            value.toFloat()
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the `int` from a [String].
     *
     * @param value The [String] value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value after parsing the [String] value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    fun getIntFromString(value: String?, def: Int): Int {
        if (value == null) return def

        return try {
            value.toInt()
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the `String` from an `Integer`.
     *
     * @param value The `Integer` value.
     * @param def The default `String` value.
     * @return Returns `value` if it is not `null`, otherwise returns `def`.
     */
    fun getStringFromInteger(value: Int?, def: String?): String? {
        return value?.toString() ?: def
    }

    /**
     * Get a `hex string` from a `ByteArray`.
     *
     * @param bytes The `ByteArray` value.
     * @return Returns the `hex string` value.
     */
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Get an `int` from [Bundle] that is stored as a [String].
     *
     * @param bundle The [Bundle] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value after parsing the [String] value stored in
     * [Bundle], otherwise returns default if failed to read a valid value,
     * like in case of an exception.
     */
    fun getIntStoredAsStringFromBundle(bundle: Bundle?, key: String, def: Int): Int {
        if (bundle == null) return def
        return getIntFromString(bundle.getString(key, def.toString()), def)
    }

    /**
     * If value is not in the range [min, max], set it to either min or max.
     */
    fun clamp(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }

    /**
     * If value is not in the range [min, max], set it to default.
     */
    fun rangedOrDefault(value: Float, def: Float, min: Float, max: Float): Float {
        return if (value < min || value > max) def else value
    }

    /**
     * Add a space indent to a [String]. Each indent is 4 space characters long.
     *
     * @param string The [String] to add indent to.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    fun getSpaceIndentedString(string: String?, count: Int): String? {
        if (string == null || string.isEmpty()) return string
        return getIndentedString(string, "    ", count)
    }

    /**
     * Add a tab indent to a [String]. Each indent is 1 tab character long.
     *
     * @param string The [String] to add indent to.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    fun getTabIndentedString(string: String?, count: Int): String? {
        if (string == null || string.isEmpty()) return string
        return getIndentedString(string, "\t", count)
    }

    /**
     * Add an indent to a [String].
     *
     * @param string The [String] to add indent to.
     * @param indent The indent characters.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    fun getIndentedString(string: String?, @NonNull indent: String, count: Int): String? {
        if (string == null || string.isEmpty()) return string
        return string.replace(Regex("(?m)^"), Strings.repeat(indent, count.coerceAtLeast(1)))
    }

    /**
     * Get the object itself if it is not `null`, otherwise default.
     *
     * @param object The `Object` to check.
     * @param def The default `Object`.
     * @return Returns `object` if it is not `null`, otherwise returns `def`.
     */
    fun <T> getDefaultIfNull(`object`: T?, def: T?): T? {
        return `object` ?: def
    }

    /**
     * Get the [String] itself if it is not `null` or empty, otherwise default.
     *
     * @param value The [String] to check.
     * @param def The default [String].
     * @return Returns `value` if it is not `null` or empty, otherwise returns `def`.
     */
    fun getDefaultIfUnset(value: String?, def: String?): String? {
        return if (value == null || value.isEmpty()) def else value
    }

    /** Check if a string is null or empty. */
    fun isNullOrEmpty(string: String?): Boolean {
        return string == null || string.isEmpty()
    }

    /** Get size of a serializable object. */
    fun getSerializedSize(`object`: Serializable?): Long {
        if (`object` == null) return 0
        return try {
            ByteArrayOutputStream().use { byteOutputStream ->
                ObjectOutputStream(byteOutputStream).use { objectOutputStream ->
                    objectOutputStream.writeObject(`object`)
                    objectOutputStream.flush()
                    byteOutputStream.toByteArray().size.toLong()
                }
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Wrapper for [getIntColorFromString] with `setAlpha` `false`.
     */
    fun getIntColorFromString(value: String?, def: Int): Int {
        return getIntColorFromString(value, def, false)
    }

    /**
     * Get an `int` color from [String] with alpha value change. If `setAlpha`
     * is `true` and given value is missing alpha then set it using def alpha.
     *
     * @param value    The [String] value.
     * @param def      The default value if failed to read a valid value.
     * @param setAlpha The `boolean` value that decides whether to set alpha or not.
     * @return Returns the `int` color value after parsing the [String]
     * value, otherwise returns default value.
     */
    fun getIntColorFromString(value: String?, def: Int, setAlpha: Boolean): Int {
        if (value == null) return def

        return try {
            var color = Color.parseColor(value)

            if (setAlpha && value.length == 7) {
                // Use alpha value of `def` color and rgb value of given `value`.
                color = (def and 0xff000000.toInt()) or (color and 0x00ffffff.toInt())
            }

            color
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Exchanges the value of x and y in [Point].
     *
     * @param point The original source point to swap.
     * @return Returns new swapped point.
     */
    fun swap(point: Point): Point {
        return Point(point.y, point.x)
    }
}
