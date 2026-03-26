package com.termux.app.terminal.io
import java.util.*

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View

import androidx.annotation.NonNull
import androidx.drawerlayout.widget.DrawerLayout

import com.termux.app.TermuxActivity
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.shared.logger.Logger
import com.termux.shared.termux.extrakeys.ExtraKeysConstants
import com.termux.shared.termux.extrakeys.ExtraKeysInfo
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.settings.properties.TermuxSharedProperties
import com.termux.shared.termux.terminal.io.TerminalExtraKeys
import com.termux.view.TerminalView

import org.json.JSONException

class TermuxTerminalExtraKeys(
    val activity: TermuxActivity,
    terminalView: TerminalView,
    val termuxTerminalViewClient: TermuxTerminalViewClient,
    val termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient
) : TerminalExtraKeys(terminalView) {

    private var extraKeysInfo: ExtraKeysInfo? = null

    private val mActivity: TermuxActivity
        get() = activity

    companion object {
        private val LOG_TAG = "TermuxTerminalExtraKeys"
    }

    init {
        setExtraKeys()
    }

    /**
     * Set the terminal extra keys and style.
     */
    private fun setExtraKeys() {
        extraKeysInfo = null

        try {
            // The map stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            val extrakeys = activity.properties.internalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true) as String
            val extraKeysStyle = activity.properties.internalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true) as String

            val extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle)
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY == extraKeyDisplayMap && TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE != extraKeysStyle) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.")
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE
            }

            extraKeysInfo = ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES)
        } catch (e: JSONException) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true)
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e)

            try {
                extraKeysInfo = ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES)
            } catch (e2: JSONException) {
                Logger.showToast(mActivity, "Can't create default extra keys", true)
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e)
                extraKeysInfo = null
            }
        }
    }

    fun getExtraKeysInfo(): ExtraKeysInfo? {
        return extraKeysInfo
    }

    @SuppressLint("RtlHardcoded")
    override fun onTerminalExtraKeyButtonClick(view: View, key: String, ctrlDown: Boolean, altDown: Boolean, shiftDown: Boolean, fnDown: Boolean) {
        when (key) {
            "KEYBOARD" -> {
                if (termuxTerminalViewClient != null)
                    termuxTerminalViewClient.onToggleSoftKeyboardRequest()
            }
            "DRAWER" -> {
                val drawerLayout = termuxTerminalViewClient.activity.drawer
                if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                    drawerLayout.closeDrawer(Gravity.LEFT)
                else
                    drawerLayout.openDrawer(Gravity.LEFT)
            }
            "PASTE" -> {
                if (termuxTerminalSessionActivityClient != null)
                    termuxTerminalSessionActivityClient.onPasteTextFromClipboard(null)
            }
            "SCROLL" -> {
                val terminalView = termuxTerminalViewClient.activity.terminalView
                if (terminalView != null && terminalView.emulator != null)
                    terminalView.emulator.toggleAutoScrollDisabled()
            }
            else -> {
                super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown)
            }
        }
    }

}
