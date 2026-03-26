package com.termux.app.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.app.models.UserAction
import com.termux.app.terminal.io.KeyboardShortcut
import com.termux.shared.android.AndroidUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.models.ReportInfo
import com.termux.shared.shell.ShellUtils
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES
import com.termux.shared.termux.TermuxUtils.AppInfoMode.TERMUX_PACKAGE
import com.termux.shared.termux.data.TermuxUrlUtils
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase
import com.termux.shared.view.KeyboardUtils
import com.termux.shared.view.ViewUtils
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.LinkedHashSet

class TermuxTerminalViewClient(
    val activity: TermuxActivity,
    val termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient
) : TermuxTerminalViewClientBase() {

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys. */
    var virtualControlKeyDown = false
    var virtualFnKeyDown = false

    private var showSoftKeyboardRunnable: Runnable? = null
    private var showSoftKeyboardIgnoreOnce = false
    private var showSoftKeyboardWithDelayOnce = false
    private var terminalCursorBlinkerStateAlreadySet = false
    private var sessionShortcuts: List<KeyboardShortcut>? = null

    companion object {
        private const val LOG_TAG = "TermuxTerminalViewClient"
    }

    /**
     * Should be called when activity.onCreate() is called
     */
    fun onCreate() {
        onReloadProperties()

        activity.getTerminalView().textSize = activity.preferences.fontSize
        activity.getTerminalView().keepScreenOn = activity.preferences.shouldKeepScreenOn()
    }

    /**
     * Should be called when activity.onStart() is called
     */
    fun onStart() {
        // Set [TerminalView.TERMINAL_VIEW_KEY_LOGGING_ENABLED] value
        // Also required if user changed the preference from [TermuxSettings] activity and returns
        val isTerminalViewKeyLoggingEnabled = activity.preferences.isTerminalViewKeyLoggingEnabled
        activity.getTerminalView().isTerminalViewKeyLoggingEnabled = isTerminalViewKeyLoggingEnabled

        // Piggyback on the terminal view key logging toggle for now, should add a separate toggle in future
        activity.getTermuxActivityRootView().isRootViewLoggingEnabled = isTerminalViewKeyLoggingEnabled
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled)
    }

    /**
     * Should be called when activity.onResume() is called
     */
    fun onResume() {
        // Show the soft keyboard if required
        setSoftKeyboardState(true, activity.isActivityRecreated)

        terminalCursorBlinkerStateAlreadySet = false

        if (activity.getTerminalView().emulator != null) {
            // Start terminal cursor blinking if enabled
            // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
            // event to start it. This is needed since onEmulatorSet() may not be called after
            // TermuxActivity is started after device display timeout with double tap and not power button.
            setTerminalCursorBlinkerState(true)
            terminalCursorBlinkerStateAlreadySet = true
        }
    }

    /**
     * Should be called when activity.onStop() is called
     */
    fun onStop() {
        // Stop terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(false)
    }

    /**
     * Should be called when activity.reloadProperties() is called
     */
    fun onReloadProperties() {
        setSessionShortcuts()
    }

    /**
     * Should be called when activity.reloadActivityStyling() is called
     */
    fun onReloadActivityStyling() {
        // Show the soft keyboard if required
        setSoftKeyboardState(false, true)

        // Start terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(true)
    }

    /**
     * Should be called when [com.termux.view.TerminalView.emulator] is set
     */
    override fun onEmulatorSet() {
        if (!terminalCursorBlinkerStateAlreadySet) {
            // Start terminal cursor blinking if enabled
            // We need to wait for the first session to be attached that's set in
            // TermuxActivity.onServiceConnected() and then the multiple calls to TerminalView.updateSize()
            // where the final one eventually sets the emulator when width/height is not 0. Otherwise
            // blinker will not start again if TermuxActivity is started again after exiting it with
            // double back press. Check TerminalView.setTerminalCursorBlinkerState().
            setTerminalCursorBlinkerState(true)
            terminalCursorBlinkerStateAlreadySet = true
        }
    }

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val increase = scale > 1f
            changeFontSize(increase)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val term = activity.getCurrentSession().emulator

        if (activity.properties.shouldOpenTerminalTranscriptURLOnClick()) {
            val columnAndRow = activity.getTerminalView().getColumnAndRow(e, true)
            val wordAtTap = term.screen.getWordAtLocation(columnAndRow[0], columnAndRow[1])
            val urlSet: LinkedHashSet<CharSequence> = TermuxUrlUtils.extractUrls(wordAtTap)

            if (urlSet.isNotEmpty()) {
                val url = urlSet.iterator().next() as String
                ShareUtils.openUrl(activity, url)
                return
            }
        }

        if (!term.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(activity))
                KeyboardUtils.showSoftKeyboard(activity, activity.getTerminalView())
            else
                Logger.logVerbose(LOG_TAG, "Not showing soft keyboard onSingleTapUp since its disabled")
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return activity.properties.isBackKeyTheEscapeKey
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return activity.properties.isEnforcingCharBasedInput
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return activity.properties.isUsingCtrlSpaceWorkaround
    }

    override fun isTerminalViewSelected(): Boolean {
        return activity.getTerminalToolbarViewPager() == null || 
            activity.isTerminalViewSelected || 
            activity.getTerminalView().hasFocus()
    }

    override fun copyModeChanged(copyMode: Boolean) {
        // Disable drawer while copying.
        activity.getDrawer().setDrawerLockMode(
            if (copyMode) DrawerLayout.LOCK_MODE_LOCKED_CLOSED 
            else DrawerLayout.LOCK_MODE_UNLOCKED
        )
    }

    @SuppressLint("RtlHardcoded")
    override fun onKeyDown(keyCode: Int, e: KeyEvent, currentSession: TerminalSession): Boolean {
        if (handleVirtualKeys(keyCode, e, true)) return true

        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning) {
            termuxTerminalSessionActivityClient.removeFinishedSession(currentSession)
            return true
        } else if (!activity.properties.areHardwareKeyboardShortcutsDisabled &&
            e.isCtrlPressed && e.isAltPressed
        ) {
            // Get the unmodified code point:
            val unicodeChar = e.getUnicodeChar(0)

            when {
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN || unicodeChar == 'n'.code -> {
                    termuxTerminalSessionActivityClient.switchToSession(true)
                }
                keyCode == KeyEvent.KEYCODE_DPAD_UP || unicodeChar == 'p'.code -> {
                    termuxTerminalSessionActivityClient.switchToSession(false)
                }
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    activity.getDrawer().openDrawer(Gravity.LEFT)
                }
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    activity.getDrawer().closeDrawers()
                }
                unicodeChar == 'k'.code -> {
                    onToggleSoftKeyboardRequest()
                }
                unicodeChar == 'm'.code -> {
                    activity.getTerminalView().showContextMenu()
                }
                unicodeChar == 'r'.code -> {
                    termuxTerminalSessionActivityClient.renameSession(currentSession)
                }
                unicodeChar == 'c'.code -> {
                    termuxTerminalSessionActivityClient.addNewSession(false, null)
                }
                unicodeChar == 'u'.code -> {
                    showUrlSelection()
                }
                unicodeChar == 'v'.code -> {
                    doPaste()
                }
                unicodeChar == '+'.code || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+'.code -> {
                    // We also check for the shifted char here since shift may be required to produce '+',
                    // see https://github.com/termux/termux-api/issues/2
                    changeFontSize(true)
                }
                unicodeChar == '-'.code -> {
                    changeFontSize(false)
                }
                unicodeChar in '1'.code..'9'.code -> {
                    val index = unicodeChar - '1'.code
                    termuxTerminalSessionActivityClient.switchToSession(index)
                }
            }
            return true
        }

        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && activity.getTerminalView().emulator == null) {
            activity.finishActivityIfNotFinishing()
            return true
        }

        return handleVirtualKeys(keyCode, e, false)
    }

    /** Handle dedicated volume buttons as virtual keys if applicable. */
    private fun handleVirtualKeys(keyCode: Int, event: KeyEvent, down: Boolean): Boolean {
        val inputDevice = event.device
        return when {
            activity.properties.areVirtualVolumeKeysDisabled() -> false
            inputDevice != null && inputDevice.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> {
                // Do not steal dedicated buttons from a full external keyboard.
                false
            }
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
                virtualControlKeyDown = down
                true
            }
            keyCode == KeyEvent.KEYCODE_VOLUME_UP -> {
                virtualFnKeyDown = down
                true
            }
            else -> false
        }
    }

    override fun readControlKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || virtualControlKeyDown
    }

    override fun readAltKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.ALT)
    }

    override fun readShiftKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT)
    }

    override fun readFnKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.FN)
    }

    fun readExtraKeysSpecialButton(specialButton: SpecialButton): Boolean {
        if (activity.getExtraKeysView() == null) return false
        val state = activity.getExtraKeysView().readSpecialButton(specialButton, true)
        if (state == null) {
            Logger.logError(LOG_TAG, "Failed to read an unregistered $specialButton special button value from extra keys.")
            return false
        }
        return state
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (virtualFnKeyDown) {
            var resultingKeyCode = -1
            var resultingCodePoint = -1
            var altDown = false
            val lowerCase = Character.toLowerCase(codePoint)
            when (lowerCase) {
                // Arrow keys.
                'w' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP
                'a' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT
                's' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
                'd' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT

                // Page up and down.
                'p' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP
                'n' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN

                // Some special keys:
                't' -> resultingKeyCode = KeyEvent.KEYCODE_TAB
                'i' -> resultingKeyCode = KeyEvent.KEYCODE_INSERT
                'h' -> resultingCodePoint = '~'.code

                // Special characters to input.
                'u' -> resultingCodePoint = '_'.code
                'l' -> resultingCodePoint = '|'.code

                // Function keys.
                in '1'..'9' -> resultingKeyCode = (codePoint - '1'.code) + KeyEvent.KEYCODE_F1
                '0' -> resultingKeyCode = KeyEvent.KEYCODE_F10

                // Other special keys.
                'e' -> resultingCodePoint = 27 // Escape
                '.' -> resultingCodePoint = 28 // ^.

                // alt+b, jumping backward in readline.
                // alf+f, jumping forward in readline.
                // alt+x, common in emacs.
                'b', 'f', 'x' -> {
                    resultingCodePoint = lowerCase.code
                    altDown = true
                }

                // Volume control.
                'v' -> {
                    resultingCodePoint = -1
                    val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_SAME,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }

                // Writing mode:
                'q', 'k' -> {
                    activity.toggleTerminalToolbar()
                    virtualFnKeyDown = false // force disable fn key down to restore keyboard input into terminal view, fixes termux/termux-app#1420
                }
            }

            if (resultingKeyCode != -1) {
                val term = session.emulator
                session.write(
                    KeyHandler.getCode(
                        resultingKeyCode,
                        0,
                        term.isCursorKeysApplicationMode,
                        term.isKeypadApplicationMode
                    )
                )
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint)
            }
            return true
        } else if (ctrlDown) {
            if (codePoint == 106 /* Ctrl+j or \n */ && !session.isRunning) {
                termuxTerminalSessionActivityClient.removeFinishedSession(session)
                return true
            }

            val shortcuts = sessionShortcuts
            if (!shortcuts.isNullOrEmpty()) {
                val codePointLowerCase = Character.toLowerCase(codePoint)
                for (i in shortcuts.indices.reversed()) {
                    val shortcut = shortcuts[i]
                    if (codePointLowerCase == shortcut.codePoint) {
                        when (shortcut.shortcutAction) {
                            TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION -> {
                                termuxTerminalSessionActivityClient.addNewSession(false, null)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION -> {
                                termuxTerminalSessionActivityClient.switchToSession(true)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION -> {
                                termuxTerminalSessionActivityClient.switchToSession(false)
                                return true
                            }
                            TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION -> {
                                termuxTerminalSessionActivityClient.renameSession(activity.getCurrentSession())
                                return true
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Set the terminal sessions shortcuts.
     */
    private fun setSessionShortcuts() {
        sessionShortcuts = ArrayList()

        // The [TermuxPropertyConstants.MAP_SESSION_SHORTCUTS] stores the session shortcut key and action pair
        for ((key, value) in TermuxPropertyConstants.MAP_SESSION_SHORTCUTS) {
            // The map stores the code points for the session shortcuts while loading properties
            val codePoint = activity.properties.getInternalPropertyValue(key, true) as? Int
            // If codePoint is null, then session shortcut did not exist in properties or was invalid
            // as parsed by [#getCodePointForSessionShortcuts(String,String)]
            // If codePoint is not null, then get the action for the MAP_SESSION_SHORTCUTS key and
            // add the code point to sessionShortcuts
            if (codePoint != null)
                sessionShortcuts?.add(KeyboardShortcut(codePoint, value))
        }
    }

    fun changeFontSize(increase: Boolean) {
        activity.preferences.changeFontSize(increase)
        activity.getTerminalView().textSize = activity.preferences.fontSize
    }

    /**
     * Called when user requests the soft keyboard to be toggled via "KEYBOARD" toggle button in
     * drawer or extra keys, or with ctrl+alt+k hardware keyboard shortcut.
     */
    fun onToggleSoftKeyboardRequest() {
        // If soft keyboard toggle behaviour is enable/disabled
        if (activity.properties.shouldEnableDisableSoftKeyboardOnToggle()) {
            // If soft keyboard is visible
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(activity)) {
                Logger.logVerbose(LOG_TAG, "Disabling soft keyboard on toggle")
                activity.preferences.isSoftKeyboardEnabled = false
                KeyboardUtils.disableSoftKeyboard(activity, activity.getTerminalView())
            } else {
                // Show with a delay, otherwise pressing keyboard toggle won't show the keyboard after
                // switching back from another app if keyboard was previously disabled by user.
                // Also request focus, since it wouldn't have been requested at startup by
                // setSoftKeyboardState if keyboard was disabled. #2112
                Logger.logVerbose(LOG_TAG, "Enabling soft keyboard on toggle")
                activity.preferences.isSoftKeyboardEnabled = true
                KeyboardUtils.clearDisableSoftKeyboardFlags(activity)
                if (showSoftKeyboardWithDelayOnce) {
                    showSoftKeyboardWithDelayOnce = false
                    activity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(), 500)
                    activity.getTerminalView().requestFocus()
                } else {
                    KeyboardUtils.showSoftKeyboard(activity, activity.getTerminalView())
                }
            }
        }
        // If soft keyboard toggle behaviour is show/hide
        else {
            // If soft keyboard is disabled by user for Termux
            if (!activity.preferences.isSoftKeyboardEnabled) {
                Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard on toggle")
                KeyboardUtils.disableSoftKeyboard(activity, activity.getTerminalView())
            } else {
                Logger.logVerbose(LOG_TAG, "Showing/Hiding soft keyboard on toggle")
                KeyboardUtils.clearDisableSoftKeyboardFlags(activity)
                KeyboardUtils.toggleSoftKeyboard(activity)
            }
        }
    }

    fun setSoftKeyboardState(isStartup: Boolean, isReloadTermuxProperties: Boolean) {
        var noShowKeyboard = false

        // Requesting terminal view focus is necessary regardless of if soft keyboard is to be
        // disabled or hidden at startup, otherwise if hardware keyboard is attached and user
        // starts typing on hardware keyboard without tapping on the terminal first, then a colour
        // tint will be added to the terminal as highlight for the focussed view. Test with a light
        // theme. For android 8.+, the "defaultFocusHighlightEnabled" attribute is also set to false
        // in TerminalView layout to fix the issue.

        // If soft keyboard is disabled by user for Termux (check function docs for Termux behaviour info)
        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(
                activity,
                activity.preferences.isSoftKeyboardEnabled,
                activity.preferences.isSoftKeyboardEnabledOnlyIfNoHardware
            )
        ) {
            Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard")
            KeyboardUtils.disableSoftKeyboard(activity, activity.getTerminalView())
            activity.getTerminalView().requestFocus()
            noShowKeyboard = true
            // Delay is only required if onCreate() is called like when Termux app is exited with
            // double back press, not when Termux app is switched back from another app and keyboard
            // toggle is pressed to enable keyboard
            if (isStartup && activity.isOnResumeAfterOnCreate)
                showSoftKeyboardWithDelayOnce = true
        } else {
            // Set flag to automatically push up TerminalView when keyboard is opened instead of showing over it
            KeyboardUtils.setSoftInputModeAdjustResize(activity)

            // Clear any previous flags to disable soft keyboard
            KeyboardUtils.clearDisableSoftKeyboardFlags(activity)

            // If soft keyboard is to be hidden on startup
            if (isStartup && activity.properties.shouldSoftKeyboardBeHiddenOnStartup()) {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup")
                // Required to keep keyboard hidden when Termux app is switched back from another app
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(activity)

                KeyboardUtils.hideSoftKeyboard(activity, activity.getTerminalView())
                activity.getTerminalView().requestFocus()
                noShowKeyboard = true
                // Required to keep keyboard hidden on app startup
                showSoftKeyboardIgnoreOnce = true
            }
        }

        activity.getTerminalView().onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            // Force show soft keyboard if TerminalView or toolbar text input view has
            // focus and close it if they don't
            var textInputViewHasFocus = false
            val textInputView: EditText? = activity.findViewById(R.id.terminal_toolbar_text_input)
            if (textInputView != null) textInputViewHasFocus = textInputView.hasFocus()

            if (hasFocus || textInputViewHasFocus) {
                if (showSoftKeyboardIgnoreOnce) {
                    showSoftKeyboardIgnoreOnce = false
                    return@OnFocusChangeListener
                }
                Logger.logVerbose(LOG_TAG, "Showing soft keyboard on focus change")
            } else {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on focus change")
            }

            KeyboardUtils.setSoftKeyboardVisibility(
                getShowSoftKeyboardRunnable(),
                activity,
                activity.getTerminalView(),
                hasFocus || textInputViewHasFocus
            )
        }

        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            Logger.logVerbose(LOG_TAG, "Requesting TerminalView focus and showing soft keyboard")
            activity.getTerminalView().requestFocus()
            activity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(), 300)
        }
    }

    private fun getShowSoftKeyboardRunnable(): Runnable {
        if (showSoftKeyboardRunnable == null) {
            showSoftKeyboardRunnable = Runnable {
                KeyboardUtils.showSoftKeyboard(activity, activity.getTerminalView())
            }
        }
        return showSoftKeyboardRunnable ?: return
    }

    fun setTerminalCursorBlinkerState(start: Boolean) {
        if (start) {
            // If set/update the cursor blinking rate is successful, then enable cursor blinker
            if (activity.getTerminalView().setTerminalCursorBlinkerRate(
                    activity.properties.terminalCursorBlinkRate
                )
            )
                activity.getTerminalView().setTerminalCursorBlinkerState(true, true)
            else
                Logger.logError(LOG_TAG, "Failed to start cursor blinker")
        } else {
            // Disable cursor blinker
            activity.getTerminalView().setTerminalCursorBlinkerState(false, true)
        }
    }

    fun shareSessionTranscript() {
        val session = activity.getCurrentSession() ?: return

        val transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true) ?: return

        // See https://github.com/termux/termux-app/issues/1166.
        val truncatedTranscript = DataUtils.getTruncatedCommandOutput(
            transcriptText,
            DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES,
            false,
            true,
            false
        ).trim()
        ShareUtils.shareText(
            activity,
            activity.getString(R.string.title_share_transcript),
            truncatedTranscript,
            activity.getString(R.string.title_share_transcript_with)
        )
    }

    fun shareSelectedText() {
        val selectedText = activity.getTerminalView().storedSelectedText
        if (DataUtils.isNullOrEmpty(selectedText)) return
        ShareUtils.shareText(
            activity,
            activity.getString(R.string.title_share_selected_text),
            selectedText,
            activity.getString(R.string.title_share_selected_text_with)
        )
    }

    fun showUrlSelection() {
        val session = activity.getCurrentSession() ?: return

        val text = ShellUtils.getTerminalSessionTranscriptText(session, true, true)

        val urlSet: LinkedHashSet<CharSequence> = TermuxUrlUtils.extractUrls(text)
        if (urlSet.isEmpty()) {
            AlertDialog.Builder(activity)
                .setMessage(R.string.title_select_url_none_found)
                .show()
            return
        }

        val urls = urlSet.toTypedArray<CharSequence>()
        Collections.reverse(Arrays.asList(*urls)) // Latest first.

        // Click to copy url to clipboard:
        val dialog = AlertDialog.Builder(activity)
            .setItems(urls) { _, which ->
                val url = urls[which] as String
                ShareUtils.copyTextToClipboard(
                    activity,
                    url,
                    activity.getString(R.string.msg_select_url_copied_to_clipboard)
                )
            }
            .setTitle(R.string.title_select_url_dialog)
            .create()

        // Long press to open URL:
        dialog.setOnShowListener {
            val lv = dialog.listView // this is a ListView with your "buds" in it
            lv.setOnItemLongClickListener { _, _, position, _ ->
                dialog.dismiss()
                val url = urls[position] as String
                ShareUtils.openUrl(activity, url)
                true
            }
        }

        dialog.show()
    }

    fun reportIssueFromTranscript() {
        val session = activity.getCurrentSession() ?: return

        val transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true) ?: return

        MessageDialogUtils.showMessage(
            activity,
            "${TermuxConstants.TERMUX_APP_NAME} Report Issue",
            activity.getString(R.string.msg_add_termux_debug_info),
            activity.getString(com.termux.shared.R.string.action_yes),
            { dialog, which -> reportIssueFromTranscript(transcriptText, true) },
            activity.getString(com.termux.shared.R.string.action_no),
            { dialog, which -> reportIssueFromTranscript(transcriptText, false) },
            null
        )
    }

    private fun reportIssueFromTranscript(transcriptText: String, addTermuxDebugInfo: Boolean) {
        Logger.showToast(activity, activity.getString(R.string.msg_generating_report), true)

        Thread {
            val reportString = StringBuilder()
            val title = "${TermuxConstants.TERMUX_APP_NAME} Report Issue"

            reportString.append("## Transcript\n")
            reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true))
            reportString.append("\n##\n")

            if (addTermuxDebugInfo) {
                reportString.append("\n\n").append(
                    TermuxUtils.getAppInfoMarkdownString(activity, TERMUX_AND_PLUGIN_PACKAGES)
                )
            } else {
                reportString.append("\n\n").append(
                    TermuxUtils.getAppInfoMarkdownString(activity, TERMUX_PACKAGE)
                )
            }

            reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(activity, true))

            if (TermuxBootstrap.isAppPackageManagerAPT) {
                val termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(activity)
                if (termuxAptInfo != null)
                    reportString.append("\n\n").append(termuxAptInfo)
            }

            if (addTermuxDebugInfo) {
                val termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(activity)
                if (termuxDebugInfo != null)
                    reportString.append("\n\n").append(termuxDebugInfo)
            }

            val userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.getName()

            val reportInfo = ReportInfo(
                userActionName,
                TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME,
                title
            )
            reportInfo.reportString = reportString.toString()
            reportInfo.reportStringSuffix = "\n\n" + TermuxUtils.getReportIssueMarkdownString(activity)
            reportInfo.setReportSaveFileLabelAndPath(
                userActionName,
                Environment.getExternalStorageDirectory().path + "/" +
                    FileUtils.sanitizeFileName(
                        "${TermuxConstants.TERMUX_APP_NAME}-$userActionName.log",
                        true,
                        true
                    )
            )

            ReportActivity.startReportActivity(activity, reportInfo)
        }.start()
    }

    fun doPaste() {
        val session = activity.getCurrentSession() ?: return
        if (!session.isRunning) return

        val text = ShareUtils.getTextStringFromClipboardIfSet(activity, true)
        if (text != null)
            session.emulator.paste(text)
    }
}
