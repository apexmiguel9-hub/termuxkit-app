package com.termux.shared.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

object ThemeUtils {

    @JvmField
    val ATTR_TEXT_COLOR_PRIMARY = android.R.attr.textColorPrimary

    @JvmField
    val ATTR_TEXT_COLOR_SECONDARY = android.R.attr.textColorSecondary

    @JvmField
    val ATTR_TEXT_COLOR = android.R.attr.textColor

    @JvmField
    val ATTR_TEXT_COLOR_LINK = android.R.attr.textColorLink

    /**
     * Will return true if system has enabled night mode.
     * https://developer.android.com/guide/topics/resources/providing-resources#NightQualifier
     */
    fun isNightModeEnabled(context: Context?): Boolean {
        if (context == null) return false
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Will return true if mode is set to [NightMode.TRUE], otherwise will return true if
     * mode is set to [NightMode.SYSTEM] and night mode is enabled by system. */
    fun shouldEnableDarkTheme(context: Context?, name: String?): Boolean {
        return when {
            NightMode.TRUE.name == name -> true
            NightMode.FALSE.name == name -> false
            NightMode.SYSTEM.name == name -> isNightModeEnabled(context)
            else -> false
        }
    }

    /** Get [ATTR_TEXT_COLOR_PRIMARY] value being used by current theme. */
    fun getTextColorPrimary(context: Context): Int {
        return getSystemAttrColor(context, ATTR_TEXT_COLOR_PRIMARY)
    }

    /** Get [ATTR_TEXT_COLOR_SECONDARY] value being used by current theme. */
    fun getTextColorSecondary(context: Context): Int {
        return getSystemAttrColor(context, ATTR_TEXT_COLOR_SECONDARY)
    }

    /** Get [ATTR_TEXT_COLOR] value being used by current theme. */
    fun getTextColor(context: Context): Int {
        return getSystemAttrColor(context, ATTR_TEXT_COLOR)
    }

    /** Get [ATTR_TEXT_COLOR_LINK] value being used by current theme. */
    fun getTextColorLink(context: Context): Int {
        return getSystemAttrColor(context, ATTR_TEXT_COLOR_LINK)
    }

    /** Wrapper for [getSystemAttrColor] with `def` value `0`. */
    fun getSystemAttrColor(context: Context, attr: Int): Int {
        return getSystemAttrColor(context, attr, 0)
    }

    /**
     * Get a values defined by the current theme listed in attrs.
     *
     * @param context The context for operations. It must be an instance of [Activity] or
     *               [AppCompatActivity] or one with which a theme attribute can be got.
     *                Do no use application context.
     * @param attr The attr id.
     * @param def The def value to return.
     * @return Returns the `attr` value if found, otherwise `def`.
     */
    fun getSystemAttrColor(context: Context, attr: Int, def: Int): Int {
        val typedArray = context.theme.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, def)
        typedArray.recycle()
        return color
    }
}
