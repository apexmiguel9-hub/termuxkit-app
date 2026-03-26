package com.termux.view
import java.util.*

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Scroller
import androidx.annotation.RequiresApi
import androidx.annotation.Nullable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.textselection.TextSelectionCursorController

/** View displaying and interacting with a [TerminalSession]. */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null
) : View(context, attributes) {

    /** Log terminal view key and IME events. */
    private var TERMINAL_VIEW_KEY_LOGGING_ENABLED = false

    /** The currently displayed terminal session, whose emulator is [emulator]. */
    var termSession: TerminalSession? = null
        private set

    /** Our terminal emulator whose session is [termSession]. */
    var emulator: TerminalEmulator? = null
        private set

    var renderer: TerminalRenderer? = null
        private set

    var client: TerminalViewClient? = null
        private set

    private var textSelectionCursorController: TextSelectionCursorController? = null

    private var terminalCursorBlinkerHandler: Handler? = null
    private var terminalCursorBlinkerRunnable: TerminalCursorBlinkerRunnable? = null
    private var terminalCursorBlinkerRate = 0
    private var cursorInvisibleIgnoreOnce = false

    /** The top row of text to display. Ranges from -getScreen().activeTranscriptRows to 0. */
    var topRow = 0
        private set

    private val defaultSelectors = intArrayOf(-1, -1, -1, -1)

    var scaleFactor = 1f
    private lateinit var gestureRecognizer: GestureAndScaleRecognizer

    /** Keep track of where mouse touch event started which we report as mouse scroll. */
    private var mouseScrollStartX = -1
    private var mouseScrollStartY = -1

    /** Keep track of the time when a touch event leading to sending mouse scroll events started. */
    private var mouseStartDownTime: Long = -1

    private lateinit var scroller: Scroller

    /** What was left in from scrolling movement. */
    private var scrollRemainder = 0f

    /** If non-zero, this is the last unicode code point received if that was a combining character. */
    private var combiningAccent = 0

    /**
     * The current AutoFill type returned for [View.getAutofillType] by [getAutofillType].
     *
     * The default is [AUTOFILL_TYPE_NONE] so that AutoFill UI, like toolbar above keyboard
     * is not shown automatically, like on Activity starts/View create. This value should be updated
     * to required value, like [AUTOFILL_TYPE_TEXT] before calling
     * [AutofillManager.requestAutofill] so that AutoFill UI shows. The updated value
     * set will automatically be restored to [AUTOFILL_TYPE_NONE] in
     * [autofill] so that AutoFill UI isn't shown anymore by calling [resetAutoFill].
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private var autoFillType = AUTOFILL_TYPE_NONE

    /**
     * The current AutoFill type returned for [View.getImportantForAutofill] by
     * [getImportantForAutofill].
     *
     * The default is [IMPORTANT_FOR_AUTOFILL_NO] so that view is not considered important
     * for AutoFill. This value should be updated to required value, like
     * [IMPORTANT_FOR_AUTOFILL_YES] before calling [AutofillManager.requestAutofill]
     * so that Android and apps consider the view as important for AutoFill to process the request.
     * The updated value set will automatically be restored to [IMPORTANT_FOR_AUTOFILL_NO] in
     * [autofill] by calling [resetAutoFill].
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private var autoFillImportance = IMPORTANT_FOR_AUTOFILL_NO

    /**
     * The current AutoFill hints returned for [View.getAutofillHints] by [getAutofillHints].
     *
     * The default is an empty `string[]`. This value should be updated to required value. The
     * updated value set will automatically be restored an empty `string[]` in
     * [autofill] by calling [resetAutoFill].
     */
    private var autoFillHints = emptyArray<String>()

    private val accessibilityEnabled: Boolean

    companion object {
        const val TERMINAL_CURSOR_BLINK_RATE_MIN = 100
        const val TERMINAL_CURSOR_BLINK_RATE_MAX = 2000

        /** The [KeyEvent] is generated from a virtual keyboard, like manually with the [KeyEvent] constructor. */
        const val KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD // -1

        /** The [KeyEvent] is generated from a non-physical device, like if 0 value is returned by [KeyEvent.deviceId]. */
        const val KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0

        private const val LOG_TAG = "TerminalView"
    }

    init {
        gestureRecognizer = GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
            private var scrolledWithFinger = false

            override fun onUp(event: MotionEvent): Boolean {
                scrollRemainder = 0f
                if (emulator != null && (emulator?.isMouseTrackingActive() ?: false) ?: false &&
                    !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText && !scrolledWithFinger) {
                    // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true)
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false)
                    return true
                }
                scrolledWithFinger = false
                return false
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                emulator ?: return true

                if (isSelectingText) {
                    stopTextSelectionMode()
                    return true
                }
                requestFocus()
                client?.onSingleTapUp(event)
                return true
            }

            override fun onScroll(e: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                emulator ?: return true
                if ((emulator?.isMouseTrackingActive() ?: false) ?: false && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    // If moving with mouse pointer while pressing button, report that instead of scroll.
                    // This means that we never report moving with button press-events for touch input,
                    // since we cannot just start sending these events without a starting press event,
                    // which we do not do for touch input, only mouse in onTouchEvent().
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                } else {
                    scrolledWithFinger = true
                    val fontLineSpacing = (renderer?.fontLineSpacing ?: 1).toFloat()
                    val deltaY: Float = distanceY + scrollRemainder
                    val deltaRows = (deltaY / fontLineSpacing).toInt()
                    scrollRemainder = deltaY - deltaRows.toFloat() * fontLineSpacing
                    doScroll(e, deltaRows)
                }
                return true
            }

            override fun onScale(focusX: Float, focusY: Float, scale: Float): Boolean {
                emulator ?: return true
                if (isSelectingText) return true
                scaleFactor *= scale
                scaleFactor = (client?.onScale(scaleFactor) ?: scaleFactor)
                return true
            }

            override fun onFling(e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                emulator ?: return true
                // Do not start scrolling until last fling has been taken care of:
                if (!scroller.isFinished) return true

                val mouseTrackingAtStartOfFling = emulator?.isMouseTrackingActive() ?: false
                val SCALE = 0.25f
                if (mouseTrackingAtStartOfFling) {
                    scroller.fling(0, 0, 0, -(velocityY * SCALE).toInt(), 0, 0, -((emulator?.rows ?: 24)) / 2, ((emulator?.rows ?: 24)) / 2)
                } else {
                    scroller.fling(0, topRow, 0, -(velocityY * SCALE).toInt(), 0, 0, -(((emulator?.getScreen()?.activeTranscriptRows ?: 0))), 0)
                }

                post(object : Runnable {
                    private var lastY = 0

                    override fun run() {
                        if (mouseTrackingAtStartOfFling != (emulator?.isMouseTrackingActive() ?: false) ?: false) {
                            scroller.abortAnimation()
                            return
                        }
                        if (scroller.isFinished) return
                        val more = scroller.computeScrollOffset()
                        val newY = scroller.currY
                        val diff = if (mouseTrackingAtStartOfFling) (newY - lastY) else (newY - topRow)
                        doScroll(e2, diff)
                        lastY = newY
                        if (more) post(this)
                    }
                })

                return true
            }

            override fun onDown(x: Float, y: Float): Boolean {
                // Why is true not returned here?
                // https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures
                // Although setting this to true still does not solve the following errors when long pressing in terminal view text area
                // ViewDragHelper: Ignoring pointerId=0 because ACTION_DOWN was not received for this pointer before ACTION_MOVE
                // Commenting out the call to mGestureDetector.onTouchEvent(event) in GestureAndScaleRecognizer#onTouchEvent() removes
                // the error logging, so issue is related to GestureDetector
                return false
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                // Do not treat is as a single confirmed tap - it may be followed by zoom.
                return false
            }

            override fun onLongPress(event: MotionEvent) {
                if (gestureRecognizer.isInProgress()) return
                if (client?.onLongPress(event) ?: false) return
                if (!isSelectingText) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    startTextSelectionMode(event)
                }
            }
        })
        scroller = Scroller(context)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityEnabled = am.isEnabled
    }

    /**
     * @param client The [TerminalViewClient] interface implementation to allow
     * for communication between [TerminalView] and its client.
     */
    fun setTerminalViewClient(client: TerminalViewClient) {
        this.client = client
    }

    /**
     * Sets whether terminal view key logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    fun setIsTerminalViewKeyLoggingEnabled(value: Boolean) {
        TERMINAL_VIEW_KEY_LOGGING_ENABLED = value
    }

    /**
     * Attach a [TerminalSession] to this view.
     *
     * @param session The [TerminalSession] this view will be displaying.
     */
    fun attachSession(session: TerminalSession?): Boolean {
        if (session == termSession) return false
        topRow = 0

        termSession = session
        emulator = null
        combiningAccent = 0

        updateSize()

        // Wait with enabling the scrollbar until we have a terminal to get scroll position from.
        isVerticalScrollBarEnabled = true

        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Ensure that inputType is only set if TerminalView is selected view with the keyboard and
        // an alternate view is not selected, like an EditText. This is necessary if an activity is
        // initially started with the alternate view or if activity is returned to from another app
        // and the alternate view was the one selected the last time.
        outAttrs.inputType = if ((client?.isTerminalViewSelected() ?: true) ?: true) {
            if ((client?.shouldEnforceCharBasedInput() ?: false) ?: false) {
                // Some keyboards seems do not reset the internal state on TYPE_NULL.
                // Affects mostly Samsung stock keyboards.
                // https://github.com/termux/termux-app/issues/686
                // However, this is not a valid value as per AOSP since `InputType.TYPE_CLASS_*` is
                // not set and it logs a warning:
                // W/InputAttributes: Unexpected input class: inputType=0x00080090 imeOptions=0x02000000
                // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/inputmethods/LatinIME/java/src/com/android/inputmethod/latin/InputAttributes.java;l=79
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                // Using InputType.NULL is the most correct input type and avoids issues with other hacks.
                //
                // Previous keyboard issues:
                // https://github.com/termux/termux-packages/issues/25
                // https://github.com/termux/termux-app/issues/87.
                // https://github.com/termux/termux-app/issues/126.
                // https://github.com/termux/termux-app/issues/137 (japanese chars and TYPE_NULL).
                InputType.TYPE_NULL
            }
        } else {
            // Corresponds to android:inputType="text"
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }

        // Note that IME_ACTION_NONE cannot be used as that makes it impossible to input newlines using the on-screen
        // keyboard on Android TV (see https://github.com/termux/termux-app/issues/221).
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {

            override fun finishComposingText(): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) client?.logInfo(LOG_TAG, "IME: finishComposingText()")
                super.finishComposingText()

                val editable = getEditable() ?: return false
                sendTextToTerminal(editable)
                editable.clear()
                return true
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    client?.logInfo(LOG_TAG, "IME: commitText(\"$text\", $newCursorPosition)")
                }
                super.commitText(text, newCursorPosition)

                emulator ?: return true

                val content = getEditable() ?: return false
                sendTextToTerminal(content)
                content.clear()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    client?.logInfo(LOG_TAG, "IME: deleteSurroundingText($leftLength, $rightLength)")
                }
                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until leftLength) sendKeyEvent(deleteKey)
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            private fun sendTextToTerminal(text: CharSequence) {
                stopTextSelectionMode()
                val textLengthInChars = text.length
                var i = 0
                while (i < textLengthInChars) {
                    val firstChar = text[i]
                    var codePoint: Int
                    if (Character.isHighSurrogate(firstChar)) {
                        if (++i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text[i])
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR
                        }
                    } else {
                        codePoint = firstChar.code
                    }

                    // Check onKeyDown() for details.
                    if ((client?.readShiftKey() ?: false))
                        codePoint = Character.toUpperCase(codePoint)

                    var ctrlHeld = false
                    var finalCodePoint = codePoint
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n'.code) {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            finalCodePoint = '\r'.code
                        }

                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true
                        when (codePoint) {
                            31 -> finalCodePoint = '_'.code
                            30 -> finalCodePoint = '^'.code
                            29 -> finalCodePoint = ']'.code
                            28 -> finalCodePoint = '\\'.code
                            else -> finalCodePoint = codePoint + 96
                        }
                    }

                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, finalCodePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    override fun computeVerticalScrollRange(): Int {
        return emulator?.getScreen()?.activeRows ?: 1
    }

    override fun computeVerticalScrollExtent(): Int {
        return ((emulator?.rows ?: 24) ?: 1)
    }

    override fun computeVerticalScrollOffset(): Int {
        return emulator?.let { it.getScreen()?.activeRows ?: 0 + topRow - it.rows } ?: 1
    }

    fun onScreenUpdated() {
        onScreenUpdated(false)
    }

    fun onScreenUpdated(skipScrollingIn: Boolean) {
        emulator ?: return
        var skipScrolling = skipScrollingIn

        val rowsInHistory = ((emulator?.getScreen()?.activeTranscriptRows ?: 0))
        if (topRow < -rowsInHistory) topRow = -rowsInHistory

        if (isSelectingText || (emulator?.isAutoScrollDisabled() ?: false)) {

            // Do not scroll when selecting text.
            val rowShift = (emulator?.getScrollCounter() ?: 0)
            if (-topRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                if (isSelectingText)
                    stopTextSelectionMode()

                if ((emulator?.isAutoScrollDisabled() ?: false)) {
                    topRow = -rowsInHistory
                    skipScrolling = true
                }
            } else {
                skipScrolling = true
                topRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }

        if (!skipScrolling && topRow != 0) {
            // Scroll down if not already there.
            if (topRow < -3) {
                // Awaken scroll bars only if scrolling a noticeable amount
                // - we do not want visible scroll bars during normal typing
                // of one row at a time.
                awakenScrollBars()
            }
            topRow = 0
        }

        emulator?.clearScrollCounter()

        invalidate()
        if (accessibilityEnabled) contentDescription = text
    }

    /** This must be called by the hosting activity in [Activity.onContextMenuClosed]
     * when context menu for the [TerminalView] is started by
     * [TextSelectionCursorController.ACTION_MORE] is closed. */
    fun onContextMenuClosed(menu: Menu) {
        // Unset the stored text since it shouldn't be used anymore and should be cleared from memory
        unsetStoredSelectedText()
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    fun setTextSize(textSize: Int) {
        renderer = TerminalRenderer(textSize, renderer?.mTypeface ?: Typeface.MONOSPACE)
        updateSize()
    }

    fun setTypeface(newTypeface: Typeface) {
        renderer = TerminalRenderer(renderer?.textSize ?: 14, newTypeface)
        updateSize()
        invalidate()
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun isOpaque(): Boolean = true

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account. E.g. if scrolled 3 lines up and the event
     * position is in the top left, column will be -3 if relativeToScroll is
     * true and 0 if relativeToScroll is false.
     * @return Array with the column and row.
     */
    fun getColumnAndRow(event: MotionEvent, relativeToScroll: Boolean): IntArray {
        val fontWidth = renderer?.fontWidth ?: 1f
        val fontLineSpacing = renderer?.fontLineSpacing ?: 1
        val column = (event.x / fontWidth).toInt()
        var row = ((event.y - fontLineSpacing.toFloat()) / fontLineSpacing.toFloat()).toInt()
        if (relativeToScroll) {
            row += topRow
        }
        return intArrayOf(column, row)
    }

    /** Send a single mouse event code to the terminal. */
    private fun sendMouseEventCode(e: MotionEvent, button: Int, pressed: Boolean) {
        val columnAndRow = getColumnAndRow(e, false)
        var x = columnAndRow[0] + 1
        var y = columnAndRow[1] + 1
        if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
            if (mouseStartDownTime == e.downTime) {
                x = mouseScrollStartX
                y = mouseScrollStartY
            } else {
                mouseStartDownTime = e.downTime
                mouseScrollStartX = x
                mouseScrollStartY = y
            }
        }
        emulator?.sendMouseEvent(button, x, y, pressed)
    }

    /** Perform a scroll, either from dragging the screen or by scrolling a mouse wheel. */
    fun doScroll(event: MotionEvent, rowsDown: Int) {
        val up = rowsDown < 0
        val amount = kotlin.math.abs(rowsDown)
        for (i in 0 until amount) {
            if (emulator?.isMouseTrackingActive() ?: false) {
                sendMouseEventCode(event, if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true)
            } else if (emulator?.isAlternateBufferActive() ?: false) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            } else {
                topRow = min(0, max(-((emulator?.getScreen()?.activeTranscriptRows ?: 0)), topRow + if (up) -1 else 1))
                if (!awakenScrollBars()) invalidate()
            }
        }
    }

    /** Overriding [View.onGenericMotionEvent]. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        emulator?.let {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.action == MotionEvent.ACTION_SCROLL) {
                // Handle mouse wheel scrolling.
                val up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f
                doScroll(event, if (up) -3 else 3)
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(23)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        emulator ?: return true
        val action = event.action

        if (isSelectingText) {
            updateFloatingToolbarVisibility(event)
            gestureRecognizer.onTouchEvent(event)
            return true
        }
        
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN) showContextMenu()
                return true
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                clipData?.getItemAt(0)?.let { clipItem ->
                    val text = clipItem.coerceToText(context)
                    if (text.isNotEmpty()) emulator?.paste(text.toString())
                }
            } else if (emulator?.isMouseTrackingActive() ?: false) { // BUTTON_PRIMARY
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP ->
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.action == MotionEvent.ACTION_DOWN)
                    MotionEvent.ACTION_MOVE ->
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                }
            }
        }

        gestureRecognizer.onTouchEvent(event)
        return true
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            client?.logInfo(LOG_TAG, "onKeyPreIme(keyCode=$keyCode, event=$event)")
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill()
            if (isSelectingText) {
                stopTextSelectionMode()
                return true
            } else if (client?.shouldBackButtonBeMappedToEscape() ?: false) {
                // Intercept back button to treat it as escape:
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> return onKeyDown(keyCode, event)
                    KeyEvent.ACTION_UP -> return onKeyUp(keyCode, event)
                }
            }
        } else if ((client?.shouldUseCtrlSpaceWorkaround() ?: false) ?: false &&
            keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed
        ) {
            /* ctrl+space does not work on some ROMs without this workaround.
               However, this breaks it on devices where it works out of the box. */
            return onKeyDown(keyCode, event)
        }
        return super.onKeyPreIme(keyCode, event)
    }

    /**
     * Key presses in software keyboards will generally NOT trigger this listener, although some
     * may elect to do so in some situations. Do not rely on this to catch software key presses.
     * Gboard calls this when shouldEnforceCharBasedInput() is disabled (InputType.TYPE_NULL) instead
     * of calling commitText(), with deviceId=-1. However, Hacker's Keyboard, OpenBoard, LG Keyboard
     * call commitText().
     *
     * This function may also be called directly without android calling it, like by
     * `TerminalExtraKeys` which generates a KeyEvent manually which uses [KeyCharacterMap.VIRTUAL_KEYBOARD]
     * as the device (deviceId=-1), as does Gboard. That would normally use mappings defined in
     * `/system/usr/keychars/Virtual.kcm`. You can run `dumpsys input` to find the `KeyCharacterMapFile`
     * used by virtual keyboard or hardware keyboard. Note that virtual keyboard device is not the
     * same as software keyboard, like Gboard, etc. Its a fake device used for generating events and
     * for testing.
     *
     * We handle shift key in `commitText()` to convert codepoint to uppercase case there with a
     * call to [Character.toUpperCase], but here we instead rely on getUnicodeChar() for
     * conversion of keyCode, for both hardware keyboard shift key (via effectiveMetaState) and
     * `client.readShiftKey()`, based on value in kcm files.
     * This may result in different behaviour depending on keyboard and android kcm files set for the
     * InputDevice for the event passed to this function. This will likely be an issue for non-english
     * languages since `Virtual.kcm` in english only by default or at least in AOSP. For both hardware
     * shift key (via effectiveMetaState) and `client.readShiftKey()`, `getUnicodeChar()` is used
     * for shift specific behaviour which usually is to uppercase.
     *
     * For fn key on hardware keyboard, android checks kcm files for hardware keyboards, which is
     * `Generic.kcm` by default, unless a vendor specific one is defined. The event passed will have
     * [KeyEvent.META_FUNCTION_ON] set. If the kcm file only defines a single character or unicode
     * code point `\\uxxxx`, then only one event is passed with that value. However, if kcm defines
     * a `fallback` key for fn or others, like `key DPAD_UP { ... fn: fallback PAGE_UP }`, then
     * android will first pass an event with original key `DPAD_UP` and [KeyEvent.META_FUNCTION_ON]
     * set. But this function will not consume it and android will pass another event with `PAGE_UP`
     * and [KeyEvent.META_FUNCTION_ON] not set, which will be consumed.
     *
     * Now there are some other issues as well, firstly ctrl and alt flags are not passed to
     * `getUnicodeChar()`, so modified key values in kcm are not used. Secondly, if the kcm file
     * for other modifiers like shift or fn define a non-alphabet, like { fn: '\u0015' } to act as
     * DPAD_LEFT, the `getUnicodeChar()` will correctly return `21` as the code point but action will
     * not happen because the `handleKeyCode()` function that transforms DPAD_LEFT to `\033[D`
     * escape sequence for the terminal to perform the left action would not be called since its
     * called before `getUnicodeChar()` and terminal will instead get `21 0x15 Negative Acknowledgement`.
     * The solution to such issues is calling `getUnicodeChar()` before the call to `handleKeyCode()`
     * if user has defined a custom kcm file, like done in POC mentioned in #2237. Note that
     * Hacker's Keyboard calls `commitText()` so don't test fn/shift with it for this function.
     * https://github.com/termux/termux-app/pull/2237
     * https://github.com/agnostic-apollo/termux-app/blob/terminal-code-point-custom-mapping/terminal-view/src/main/java/com/termux/view/TerminalView.java
     *
     * Key Character Map (kcm) and Key Layout (kl) files info:
     * https://source.android.com/devices/input/key-character-map-files
     * https://source.android.com/devices/input/key-layout-files
     * https://source.android.com/devices/input/keyboard-devices
     * AOSP kcm and kl files:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/data/keyboards
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/packages/InputDevices/res/raw
     *
     * KeyCodes:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/native/include/android/keycodes.h
     *
     * `dumpsys input`:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1917
     *
     * Loading of keymap:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1644
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/Keyboard.cpp;l=41
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/InputDevice.cpp
     * OVERLAY keymaps for hardware keyboards may be combined as well:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=165
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=831
     *
     * Parse kcm file:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=727
     * Parse key value:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=981
     *
     * `KeyEvent.getUnicodeChar()`
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java;l=2716
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/KeyCharacterMap.java;l=368
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/jni/android_view_KeyCharacterMap.cpp;l=117
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=231
     *
     * Keyboard layouts advertised by applications, like for hardware keyboards via #ACTION_QUERY_KEYBOARD_LAYOUTS
     * Config is stored in `/data/system/input-manager-state.xml`
     * https://github.com/ris58h/custom-keyboard-layout
     * Loading from apps:
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1221
     * Set:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=89
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=543
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/apps/Settings/src/com/android/settings/inputmethod/KeyboardLayoutDialogFragment.java;l=167
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1385
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/PersistentDataStore.java
     * Get overlay keyboard layout
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=2158
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/services/core/jni/com_android_server_input_InputManagerService.cpp;l=616
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            client?.logInfo(LOG_TAG, "onKeyDown(keyCode=$keyCode, isSystem()=${event.isSystem}, event=$event)")
        emulator ?: return true
        if (isSelectingText) {
            stopTextSelectionMode()
        }

        if ((termSession?.let { client?.onKeyDown(keyCode, event, it) } ?: false ?: false)) {
            invalidate()
            return true
        } else if (event.isSystem && (!(client?.shouldBackButtonBeMappedToEscape() ?: false) || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event)
        } else if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            termSession?.write(event.characters)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            return super.onKeyDown(keyCode, event)
        }

        val metaState = event.metaState
        val controlDown = event.isCtrlPressed || (client?.readControlKey() ?: false)
        val leftAltDown = (metaState and KeyEvent.META_ALT_LEFT_ON) != 0 || (client?.readAltKey() ?: false)
        val shiftDown = event.isShiftPressed || (client?.readShiftKey() ?: false)
        val rightAltDownFromEvent = (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0

        var keyMod = 0
        if (controlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) client?.logInfo(LOG_TAG, "handleKeyCode() took key event")
            return true
        }

        // Clear Ctrl since we handle that ourselves:
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (rightAltDownFromEvent) {
            // Let right Alt/Alt Gr be used to compose characters.
        } else {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear = bitsToClear or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        }
        var effectiveMetaState = event.metaState and bitsToClear.inv()

        if (shiftDown) effectiveMetaState = effectiveMetaState or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if ((client?.readFnKey() ?: false) ?: false) effectiveMetaState = effectiveMetaState or KeyEvent.META_FUNCTION_ON

        val result = event.getUnicodeChar(effectiveMetaState)
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            client?.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar($effectiveMetaState) returned: $result")
        if (result == 0) {
            return false
        }

        val oldCombiningAccent = combiningAccent
        var combiningAccentValue = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // If entered combining accent previously, write it out:
            if (combiningAccent != 0)
                inputCodePoint(event.deviceId, combiningAccent, controlDown, leftAltDown)
            combiningAccent = combiningAccentValue
        } else {
            if (combiningAccent != 0) {
                val combinedChar = KeyCharacterMap.getDeadChar(combiningAccent, result)
                if (combinedChar > 0) {
                    inputCodePoint(event.deviceId, combinedChar, controlDown, leftAltDown)
                }
                combiningAccent = 0
            } else {
                inputCodePoint(event.deviceId, result, controlDown, leftAltDown)
            }
        }

        if (combiningAccent != oldCombiningAccent) invalidate()

        return true
    }

    fun inputCodePoint(eventSource: Int, codePoint: Int, controlDownFromEvent: Boolean, leftAltDownFromEvent: Boolean) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            client?.logInfo(LOG_TAG, "inputCodePoint(eventSource=$eventSource, codePoint=$codePoint, controlDownFromEvent=$controlDownFromEvent, leftAltDownFromEvent=$leftAltDownFromEvent)")
        }

        termSession ?: return

        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        emulator?.setCursorBlinkState(true)

        val controlDown = controlDownFromEvent || (client?.readControlKey() ?: false)
        val altDown = leftAltDownFromEvent || (client?.readAltKey() ?: false)

        if (termSession?.let { client?.onCodePoint(codePoint, controlDown, it) } ?: false) return

        var finalCodePoint = codePoint
        if (controlDown) {
            when {
                codePoint in 'a'.code..'z'.code -> finalCodePoint = codePoint - 'a'.code + 1
                codePoint in 'A'.code..'Z'.code -> finalCodePoint = codePoint - 'A'.code + 1
                codePoint == ' '.code || codePoint == '2'.code -> finalCodePoint = 0
                codePoint == '['.code || codePoint == '3'.code -> finalCodePoint = 27 // ^[ (Esc)
                codePoint == '\\'.code || codePoint == '4'.code -> finalCodePoint = 28
                codePoint == ']'.code || codePoint == '5'.code -> finalCodePoint = 29
                codePoint == '^'.code || codePoint == '6'.code -> finalCodePoint = 30 // control-^
                codePoint == '_'.code || codePoint == '7'.code || codePoint == '/'.code ->
                    // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                    // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                    finalCodePoint = 31
                codePoint == '8'.code -> finalCodePoint = 127 // DEL
            }
        }

        if (finalCodePoint > -1) {
            // If not virtual or soft keyboard.
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                when (finalCodePoint) {
                    0x02DC -> finalCodePoint = 0x007E // SMALL TILDE -> TILDE (~)
                    0x02CB -> finalCodePoint = 0x0060 // MODIFIER LETTER GRAVE ACCENT -> GRAVE ACCENT (`)
                    0x02C6 -> finalCodePoint = 0x005E // MODIFIER LETTER CIRCUMFLEX ACCENT -> CIRCUMFLEX ACCENT (^)
                }
            }

            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            termSession?.writeCodePoint(altDown, finalCodePoint)
        }
    }

    fun handleKeyCodeAction(keyCode: Int, keyMod: Int): Boolean {
        val shiftDown: Boolean = (keyMod and KeyHandler.KEYMOD_SHIFT) != 0
        val isPageKey: Boolean = (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN)

        if (isPageKey && shiftDown) {
            val time: Long = SystemClock.uptimeMillis()
            val motionEvent: MotionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val rows: Int = if (keyCode == KeyEvent.KEYCODE_PAGE_UP) -(emulator?.rows ?: 24) else (emulator?.rows ?: 24)
            doScroll(motionEvent, rows)
            motionEvent.recycle()
            return true
        }

        return false
    }

    fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        emulator?.setCursorBlinkState(true)

        val handled: Boolean = handleKeyCodeAction(keyCode, keyMod)
        if (handled) {
            return true
        }

        val term = emulator ?: return false
        val code: String? = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode())
        code ?: return false
        termSession?.write(code)
        return true
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A [KeyEvent] describing the event.
     * @return Whether the event was handled.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            client?.logInfo(LOG_TAG, "onKeyUp(keyCode=$keyCode, event=$event)")

        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.
        emulator ?: return true
        if (keyCode != KeyEvent.KEYCODE_BACK) return true

        if (client?.onKeyUp(keyCode, event) ?: false) {
            invalidate()
            return true
        } else if (event.isSystem) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event)
        }

        return true
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateSize()
    }

    /** Check if the terminal size in rows and columns should be updated. */
    fun updateSize() {
        val viewWidth = width
        val viewHeight = height
        viewWidth.takeUnless { it == 0 } ?: return
        viewHeight.takeUnless { it == 0 } ?: return
        termSession ?: return

        // Set to 80 and 24 if you want to enable vttest.
        val newColumns = max(4, (viewWidth / (renderer?.fontWidth ?: 1f)).toInt())
        val fontLineSpacing = renderer?.fontLineSpacing ?: 1
        val newRows = max(4, ((viewHeight.toFloat() - fontLineSpacing.toFloat()) / fontLineSpacing.toFloat()).toInt())

        if (emulator == null || (newColumns != (emulator?.columns ?: 80) || newRows != (emulator?.rows ?: 24))) {
            termSession?.updateSize(newColumns, newRows, (renderer?.fontWidth ?: 1f).toInt(), (renderer?.fontLineSpacing ?: 1).toInt())
            emulator = termSession?.emulator
            client?.onEmulatorSet() ?: return

            // Update terminalCursorBlinkerRunnable inner class emulator on session change
            terminalCursorBlinkerRunnable?.setEmulator(emulator)

            topRow = 0
            scrollTo(0, 0)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        emulator?.let {
            // render the terminal view and highlight any selected text
            var sel = defaultSelectors
            if (textSelectionCursorController != null) {
                textSelectionCursorController?.getSelectors(sel)
            }

            renderer?.render(it, canvas, topRow, sel[0], sel[1], sel[2], sel[3])

            // render the text selection handles
            renderTextSelection()
        } ?: run {
            canvas.drawColor(0XFF000000)
        }
    }

    fun getCurrentSession(): TerminalSession? = termSession

    private val text: CharSequence
        get() = emulator?.getScreen()?.getSelectedText(0, topRow, (emulator?.columns ?: 80), topRow + (emulator?.rows ?: 24)) ?: ""

    fun getCursorX(x: Float): Int = (x / (renderer?.fontWidth ?: 1f)).toInt()

    fun getCursorY(y: Float): Int = ((y - 40f) / (renderer?.fontLineSpacing ?: 1).toFloat()).toInt() + topRow

    fun getPointX(cx: Int): Int {
        val finalCx = minOf(cx, emulator?.columns ?: 80)
        val fontWidth = renderer?.fontWidth ?: 1f
        return (finalCx.toFloat() * fontWidth).roundToInt()
    }

    fun getPointY(cy: Int): Int = ((cy - topRow).toFloat() * (renderer?.fontLineSpacing ?: 1).toFloat()).roundToInt()

    fun setTopRow(topRow: Int) {
        this.topRow = topRow
    }

    /**
     * Define functions required for AutoFill API
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun autofill(value: AutofillValue) {
        if (value.isText) {
            termSession?.write(value.textValue.toString())
        }
        resetAutoFill()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillType(): Int = autoFillType

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillHints(): Array<String> = autoFillHints

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillValue(): AutofillValue = AutofillValue.forText("")

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getImportantForAutofill(): Int = autoFillImportance

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun resetAutoFill() {
        autoFillType = AUTOFILL_TYPE_NONE
        autoFillImportance = IMPORTANT_FOR_AUTOFILL_NO
        autoFillHints = emptyArray()
    }

    fun getAutoFillManagerService(): AutofillManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            val context = context ?: return null
            context.getSystemService(AutofillManager::class.java)
        } catch (e: Exception) {
            client?.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e)
            null
        }
    }

    fun isAutoFillEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        return try {
            val autofillManager = getAutoFillManagerService()
            autofillManager != null && autofillManager.isEnabled
        } catch (e: Exception) {
            client?.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e)
            false
        }
    }

    @Synchronized fun requestAutoFillUsername() {
        requestAutoFill(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_USERNAME) else null
        )
    }

    @Synchronized fun requestAutoFillPassword() {
        requestAutoFill(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_PASSWORD) else null
        )
    }

    @Synchronized fun requestAutoFill(autoFillHints: Array<String>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (autoFillHints == null || autoFillHints.isEmpty()) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager != null && autofillManager.isEnabled) {
                // Update type that will be returned by `getAutofillType()` so that AutoFill UI is shown.
                autoFillType = AUTOFILL_TYPE_TEXT
                // Update importance that will be returned by `getImportantForAutofill()` so that
                // AutoFill considers the view as important.
                autoFillImportance = IMPORTANT_FOR_AUTOFILL_YES
                // Update hints that will be returned by `getAutofillHints()` for which to show AutoFill UI.
                this.autoFillHints = autoFillHints
                autofillManager.requestAutofill(this)
            }
        } catch (e: Exception) {
            client?.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e)
        }
    }

    @Synchronized fun cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (autoFillType == AUTOFILL_TYPE_NONE) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager != null && autofillManager.isEnabled) {
                resetAutoFill()
                autofillManager.cancel()
            }
        } catch (e: Exception) {
            client?.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e)
        }
    }

    /**
     * Set terminal cursor blinker rate. It must be between [TERMINAL_CURSOR_BLINK_RATE_MIN]
     * and [TERMINAL_CURSOR_BLINK_RATE_MAX], otherwise it will be disabled.
     *
     * The [setTerminalCursorBlinkerState] must be called after this
     * for changes to take effect if not disabling.
     *
     * @param blinkRate The value to set.
     * @return Returns `true` if setting blinker rate was successfully set, otherwise [@code false].
     */
    @Synchronized fun setTerminalCursorBlinkerRate(blinkRate: Int): Boolean {
        val result = if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            client?.logError(LOG_TAG, "The cursor blink rate must be in between $TERMINAL_CURSOR_BLINK_RATE_MIN-$TERMINAL_CURSOR_BLINK_RATE_MAX: $blinkRate")
            terminalCursorBlinkerRate = 0
            false
        } else {
            client?.logVerbose(LOG_TAG, "Setting cursor blinker rate to $blinkRate")
            terminalCursorBlinkerRate = blinkRate
            true
        }

        if (terminalCursorBlinkerRate == 0) {
            client?.logVerbose(LOG_TAG, "Cursor blinker disabled")
            stopTerminalCursorBlinker()
        }

        return result
    }

    /**
     * Sets whether cursor blinker should be started or stopped. Cursor blinker will only be
     * started if [terminalCursorBlinkerRate] does not equal 0 and is between
     * [TERMINAL_CURSOR_BLINK_RATE_MIN] and [TERMINAL_CURSOR_BLINK_RATE_MAX].
     *
     * This should be called when the view holding this activity is resumed or stopped so that
     * cursor blinker does not run when activity is not visible. If you call this on onResume()
     * to start cursor blinking, then ensure that [emulator] is set, otherwise wait for the
     * [TerminalViewClient.onEmulatorSet] event after calling [attachSession]
     * for the first session added in the activity since blinking will not start if [emulator]
     * is not set, like if activity is started again after exiting it with double back press. Do not
     * call this directly after [attachSession] since [updateSize]
     * may return without setting [emulator] since width/height may be 0. Its called again in
     * [onSizeChanged]. Calling on onResume() if emulator is already set
     * is necessary, since onEmulatorSet() may not be called after activity is started after device
     * display timeout with double tap and not power button.
     *
     * It should also be called on the
     * [com.termux.terminal.TerminalSessionClient.onTerminalCursorStateChange]
     * callback when cursor is enabled or disabled so that blinker is disabled if cursor is not
     * to be shown. It should also be checked if activity is visible if blinker is to be started
     * before calling this.
     *
     * It should also be called after terminal is reset with [TerminalSession.reset] in case
     * cursor blinker was disabled before reset due to call to
     * [com.termux.terminal.TerminalSessionClient.onTerminalCursorStateChange].
     *
     * How cursor blinker starting works is by registering a [Runnable] with the looper of
     * the main thread of the app which when run, toggles the cursor blinking state and re-registers
     * itself to be called with the delay set by [terminalCursorBlinkerRate]. When cursor
     * blinking needs to be disabled, we just cancel any callbacks registered. We don't run our own
     * "thread" and let the thread for the main looper do the work for us, whose usage is also
     * required to update the UI, since it also handles other calls to update the UI as well based
     * on a queue.
     *
     * Note that when moving cursor in text editors like nano, the cursor state is quickly
     * toggled `-> off -> on`, which would call this very quickly sequentially. So that if cursor
     * is moved 2 or more times quickly, like long hold on arrow keys, it would trigger
     * `-> off -> on -> off -> on -> ...`, and the "on" callback at index 2 is automatically
     * cancelled by next "off" callback at index 3 before getting a chance to be run. For this case
     * we log only if [TERMINAL_VIEW_KEY_LOGGING_ENABLED] is enabled, otherwise would clutter
     * the log. We don't start the blinking with a delay to immediately show cursor in case it was
     * previously not visible.
     *
     * @param start If cursor blinker should be started or stopped.
     * @param startOnlyIfCursorEnabled If set to `true`, then it will also be checked if the
     * cursor is even enabled by [TerminalEmulator] before
     * starting the cursor blinker.
     */
    @Synchronized fun setTerminalCursorBlinkerState(start: Boolean, startOnlyIfCursorEnabled: Boolean) {
        // Stop any existing cursor blinker callbacks
        stopTerminalCursorBlinker()

        emulator ?: return

        emulator?.setCursorBlinkingEnabled(false)

        if (start) {
            // If cursor blinker is not enabled or is not valid
            if (terminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || terminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
                return
            // If cursor blinder is to be started only if cursor is enabled
            else if (startOnlyIfCursorEnabled && !(emulator?.isCursorEnabled() ?: true) ?: true) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                    client?.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled")
                return
            }

            // Start cursor blinker runnable
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                client?.logVerbose(LOG_TAG, "Starting cursor blinker with the blink rate $terminalCursorBlinkerRate")
            if (terminalCursorBlinkerHandler == null)
                terminalCursorBlinkerHandler = Handler(Looper.getMainLooper())
            terminalCursorBlinkerRunnable = TerminalCursorBlinkerRunnable(emulator ?: return, terminalCursorBlinkerRate)
            emulator?.setCursorBlinkingEnabled(true)
            terminalCursorBlinkerRunnable?.run()
        }
    }

    /**
     * Cancel the terminal cursor blinker callbacks
     */
    private fun stopTerminalCursorBlinker() {
        if (terminalCursorBlinkerHandler != null && terminalCursorBlinkerRunnable != null) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                client?.logVerbose(LOG_TAG, "Stopping cursor blinker")
            terminalCursorBlinkerRunnable?.let { terminalCursorBlinkerHandler?.removeCallbacks(it) }
        }
    }

    private inner class TerminalCursorBlinkerRunnable(
        private var emulator: TerminalEmulator?,
        private val mBlinkRate: Int
    ) : Runnable {

        // Initialize with false so that initial blink state is visible after toggling
        private var cursorVisible = false

        fun setEmulator(emulator: TerminalEmulator?) {
            this.emulator = emulator
        }

        override fun run() {
            try {
                if (emulator != null) {
                    // Toggle the blink state and then invalidate() the view so
                    // that onDraw() is called, which then calls TerminalRenderer.render()
                    // which checks with TerminalEmulator.shouldCursorBeVisible() to decide whether
                    // to draw the cursor or not
                    cursorVisible = !cursorVisible
                    //client.logVerbose(LOG_TAG, "Toggling cursor blink state to $cursorVisible")
                    emulator?.setCursorBlinkState(cursorVisible)
                    invalidate()
                }
            } finally {
                // Recall the Runnable after mBlinkRate milliseconds to toggle the blink state
                terminalCursorBlinkerHandler?.postDelayed(this, mBlinkRate.toLong())
            }
        }
    }

    /**
     * Define functions required for text selection and its handles.
     */
    private fun getTextSelectionCursorController(): TextSelectionCursorController {
        if (textSelectionCursorController == null) {
            textSelectionCursorController = TextSelectionCursorController(this)

            val observer = viewTreeObserver
            if (observer != null) {
                observer.addOnTouchModeChangeListener(textSelectionCursorController)
            }
        }

        return textSelectionCursorController ?: throw IllegalStateException("Not initialized")
    }

    private fun showTextSelectionCursors(event: MotionEvent) {
        getTextSelectionCursorController().show(event)
    }

    private fun hideTextSelectionCursors(): Boolean {
        return getTextSelectionCursorController().hide()
    }

    private fun renderTextSelection() {
        textSelectionCursorController?.render()
    }

    val isSelectingText: Boolean
        get() = textSelectionCursorController?.isActive() ?: false

    /** Get the currently selected text if selecting. */
    fun getText(): String? {
        return if (isSelectingText && textSelectionCursorController != null)
            textSelectionCursorController?.selectedText
        else
            null
    }

    /** Get the selected text stored before "MORE" button was pressed on the context menu. */
    @Nullable
    fun getStoredSelectedText(): String? {
        return textSelectionCursorController?.getStoredSelectedText()
    }

    /** Unset the selected text stored before "MORE" button was pressed on the context menu. */
    fun unsetStoredSelectedText() {
        textSelectionCursorController?.unsetStoredSelectedText()
    }

    private fun getTextSelectionActionMode(): ActionMode? {
        return textSelectionCursorController?.getActionMode()
    }

    fun startTextSelectionMode(event: MotionEvent) {
        if (!requestFocus()) {
            return
        }

        showTextSelectionCursors(event)
        client?.copyModeChanged(isSelectingText)

        invalidate()
    }

    fun stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            client?.copyModeChanged(isSelectingText)
            invalidate()
        }
    }

    private fun decrementYTextSelectionCursors(decrement: Int) {
        textSelectionCursorController?.decrementYTextSelectionCursors(decrement)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        textSelectionCursorController?.let {
            viewTreeObserver.addOnTouchModeChangeListener(it)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        textSelectionCursorController?.let {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            stopTextSelectionMode()

            viewTreeObserver.removeOnTouchModeChangeListener(it)
            it.onDetached()
        }
    }

    /**
     * Define functions required for long hold toolbar.
     */
    private val mShowFloatingToolbar = Runnable {
        @RequiresApi(api = Build.VERSION_CODES.M)
        if (getTextSelectionActionMode() != null) {
            getTextSelectionActionMode()?.hide(0)  // hide off.
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun showFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            val delay = ViewConfiguration.getDoubleTapTimeout()
            postDelayed(mShowFloatingToolbar, delay.toLong())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun hideFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            removeCallbacks(mShowFloatingToolbar)
            getTextSelectionActionMode()?.hide(-1)
        }
    }

    fun updateFloatingToolbarVisibility(event: MotionEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> hideFloatingToolbar()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showFloatingToolbar()
            }
        }
    }
}
