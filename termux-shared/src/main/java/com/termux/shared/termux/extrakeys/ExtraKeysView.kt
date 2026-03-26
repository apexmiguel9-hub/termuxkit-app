package com.termux.shared.termux.extrakeys

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.google.android.material.button.MaterialButton
import com.termux.shared.R
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.shared.termux.terminal.io.TerminalExtraKeys
import com.termux.shared.theme.ThemeUtils
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.max

/**
 * A [View] showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * To use it, add following to a layout file and import it in your activity layout file or inflate
 * it with a [androidx.viewpager.widget.ViewPager].:
 * ```
 * <?xml version="1.0" encoding="utf-8"?>
 * <com.termux.shared.termux.extrakeys.ExtraKeysView xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:id="@+id/extra_keys"
 *     style="?android:attr/buttonBarStyle"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:layout_alignParentBottom="true"
 *     android:orientation="horizontal" />
 * ```
 *
 * Then in your activity, get its reference by a call to [android.app.Activity.findViewById]
 * or [LayoutInflater.inflate] if using [androidx.viewpager.widget.ViewPager].
 * Then call [setExtraKeysViewClient] and pass it the implementation of
 * [IExtraKeysView] so that you can receive callbacks. You can also override other values set
 * in [ExtraKeysView] by calling the respective functions.
 * If you extend [ExtraKeysView], you can also set them in the constructor, but do call super().
 *
 * After this you will have to make a call to [reload] and pass
 * it the [ExtraKeysInfo] to load and display the extra keys. Read its class javadocs for more
 * info on how to create it.
 *
 * Termux app defines the view in res/layout/view_terminal_toolbar_extra_keys and
 * inflates it in TerminalToolbarViewPager.instantiateItem() and sets the [ExtraKeysView] client
 * and calls [ExtraKeysView.reload].
 * The [ExtraKeysInfo] is created by TermuxAppSharedProperties.setExtraKeys().
 * Then its got and the view height is adjusted in TermuxActivity.setTerminalToolbarHeight().
 * The client used is TermuxTerminalExtraKeys, which extends
 * [TerminalExtraKeys] to handle Termux app specific logic and
 * leave the rest to the super class.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    /** The client for the [ExtraKeysView]. */
    interface IExtraKeysView {

        /**
         * This is called by [ExtraKeysView] when a button is clicked. This is also called
         * for [mRepetitiveKeys] and [ExtraKeyButton] that have a popup set.
         * However, this is not called for [specialButtons], whose state can instead be read
         * via a call to [readSpecialButton].
         *
         * @param view The view that was clicked.
         * @param buttonInfo The [ExtraKeyButton] for the button that was clicked.
         *                   The button may be a [ExtraKeyButton.KEY_MACRO] set which can be
         *                   checked with a call to [ExtraKeyButton.isMacro].
         * @param button The [MaterialButton] that was clicked.
         */
        fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton)

        /**
         * This is called by [ExtraKeysView] when a button is clicked so that the client
         * can perform any hepatic feedback. This is only called in the [MaterialButton.OnClickListener]
         * and not for every repeat. Its also called for [specialButtons].
         *
         * @param view The view that was clicked.
         * @param buttonInfo The [ExtraKeyButton] for the button that was clicked.
         * @param button The [MaterialButton] that was clicked.
         * @return Return `true` if the client handled the feedback, otherwise `false`
         * so that [performExtraKeyButtonHapticFeedback] can handle it depending on system settings.
         */
        fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton): Boolean
    }

    companion object {
        /** Defines the default value for [buttonTextColor] defined by current theme. */
        val ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor

        /** Defines the default value for [buttonActiveTextColor] defined by current theme. */
        val ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor

        /** Defines the default value for [buttonBackgroundColor] defined by current theme. */
        val ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor

        /** Defines the default value for [buttonActiveBackgroundColor] defined by current theme. */
        val ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor

        /** Defines the default fallback value for [buttonTextColor] if [ATTR_BUTTON_TEXT_COLOR] is undefined. */
        val DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF

        /** Defines the default fallback value for [buttonActiveTextColor] if [ATTR_BUTTON_ACTIVE_TEXT_COLOR] is undefined. */
        const val DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA

        /** Defines the default fallback value for [buttonBackgroundColor] if [ATTR_BUTTON_BACKGROUND_COLOR] is undefined. */
        const val DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000

        /** Defines the default fallback value for [buttonActiveBackgroundColor] if [ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR] is undefined. */
        const val DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F

        /** Defines the minimum allowed duration in milliseconds for [mLongPressTimeout]. */
        const val MIN_LONG_PRESS_DURATION = 200

        /** Defines the maximum allowed duration in milliseconds for [mLongPressTimeout]. */
        const val MAX_LONG_PRESS_DURATION = 3000

        /** Defines the fallback duration in milliseconds for [mLongPressTimeout]. */
        const val FALLBACK_LONG_PRESS_DURATION = 400

        /** Defines the minimum allowed duration in milliseconds for [mLongPressRepeatDelay]. */
        const val MIN_LONG_PRESS__REPEAT_DELAY = 5

        /** Defines the maximum allowed duration in milliseconds for [mLongPressRepeatDelay]. */
        const val MAX_LONG_PRESS__REPEAT_DELAY = 2000

        /** Defines the default duration in milliseconds for [mLongPressRepeatDelay]. */
        const val DEFAULT_LONG_PRESS_REPEAT_DELAY = 80

        /**
         * Get the maximum length of a row in the matrix.
         */
        @JvmStatic
        fun maximumLength(matrix: Array<Array<ExtraKeyButton>>): Int {
            var m = 0
            for (row in matrix)
                m = max(m, row.size)
            return m
        }
    }

    /** The implementation of the [IExtraKeysView] that acts as a client for the [ExtraKeysView]. */
    var extraKeysViewClient: IExtraKeysView? = null

    /** The map for the [SpecialButton] and their [SpecialButtonState]. Defaults to
     * the one returned by [getDefaultSpecialButtons]. */
    var specialButtons: Map<SpecialButton, SpecialButtonState> = emptyMap()

    /** The keys for the [SpecialButton] added to [specialButtons]. This is automatically
     * set when the call to [setSpecialButtons] is made. */
    var specialButtonsKeys: Set<SpecialButton> = emptySet()

    /**
     * The list of keys for which auto repeat of key should be triggered if its extra keys button
     * is long pressed. This is done by calling [IExtraKeysView.onExtraKeyButtonClick]
     * every [longPressRepeatDelay] seconds after [longPressTimeout] has passed.
     * The default keys are defined by [ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS].
     */
    var repetitiveKeys: List<String> = emptyList()

    /** The text color for the extra keys button. Defaults to [DEFAULT_BUTTON_TEXT_COLOR]. */
    var buttonTextColor: Int = 0

    /** The text color for the extra keys button when its active.
     * Defaults to [DEFAULT_BUTTON_ACTIVE_TEXT_COLOR]. */
    var buttonActiveTextColor: Int = 0

    /** The background color for the extra keys button. Defaults to [DEFAULT_BUTTON_BACKGROUND_COLOR]. */
    var buttonBackgroundColor: Int = 0

    /** The background color for the extra keys button when its active. Defaults to
     * [DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR]. */
    var buttonActiveBackgroundColor: Int = 0

    /** Defines whether text for the extra keys button should be all capitalized automatically. */
    var buttonTextAllCaps: Boolean = true

    /**
     * Defines the duration in milliseconds before a press turns into a long press. The default
     * duration used is the one returned by a call to [ViewConfiguration.getLongPressTimeout]
     * which will return the system defined duration which can be changed in accessibility settings.
     * The duration must be in between [MIN_LONG_PRESS_DURATION] and [MAX_LONG_PRESS_DURATION],
     * otherwise [FALLBACK_LONG_PRESS_DURATION] is used.
     */
    var longPressTimeout: Int = 0

    /**
     * Defines the duration in milliseconds for the delay between trigger of each repeat of
     * [repetitiveKeys]. The default value is defined by [DEFAULT_LONG_PRESS_REPEAT_DELAY].
     * The duration must be in between [MIN_LONG_PRESS__REPEAT_DELAY] and
     * [MAX_LONG_PRESS__REPEAT_DELAY], otherwise [DEFAULT_LONG_PRESS_REPEAT_DELAY] is used.
     */
    var longPressRepeatDelay: Int = 0

    /** The popup window shown if [ExtraKeyButton.getPopup] returns a `non-null` value
     * and a swipe up action is done on an extra key. */
    var popupWindow: PopupWindow? = null

    var scheduledExecutor: java.util.concurrent.ScheduledExecutorService? = null
    var handler: Handler? = null
    var specialButtonsLongHoldRunnable: SpecialButtonsLongHoldRunnable? = null
    var longPressCount: Int = 0

    init {
        repetitiveKeys = ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS
        specialButtons = getDefaultSpecialButtons(this)

        buttonTextColor = ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, (DEFAULT_BUTTON_TEXT_COLOR shr 32).toInt())
        buttonActiveTextColor = ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, (DEFAULT_BUTTON_ACTIVE_TEXT_COLOR shr 32).toInt())
        buttonBackgroundColor = ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, (DEFAULT_BUTTON_BACKGROUND_COLOR shr 32).toInt())
        buttonActiveBackgroundColor = ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, (DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR shr 32).toInt())

        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        longPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
    }

    /** Get the default map that can be used for [specialButtons]. */
    @NonNull
    fun getDefaultSpecialButtons(extraKeysView: ExtraKeysView): Map<SpecialButton, SpecialButtonState> {
        return hashMapOf(
            SpecialButton.CTRL to SpecialButtonState(extraKeysView),
            SpecialButton.ALT to SpecialButtonState(extraKeysView),
            SpecialButton.SHIFT to SpecialButtonState(extraKeysView),
            SpecialButton.FN to SpecialButtonState(extraKeysView)
        )
    }

    /**
     * Reload this instance of [ExtraKeysView] with the info passed in `extraKeysInfo`.
     *
     * @param extraKeysInfo The [ExtraKeysInfo] that defines the necessary info for the extra keys.
     * @param heightPx The height in pixels of the parent surrounding the [ExtraKeysView]. It must
     *                 be a single child.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun reload(extraKeysInfo: ExtraKeysInfo?, heightPx: Float) {
        extraKeysInfo ?: return

        for (state in specialButtons.values)
            state.apply { }

        removeAllViews()

        val buttons = extraKeysInfo.getMatrix()

        setRowCount(buttons.size)
        setColumnCount(maximumLength(buttons))

        for (row in buttons.indices) {
            for (col in buttons[row].indices) {
                val buttonInfo = buttons[row][col]

                val button: MaterialButton?
                if (isSpecialButton(buttonInfo)) {
                    button = createSpecialButton(buttonInfo.key, true)
                    button ?: return
                } else {
                    button = MaterialButton(context, null, android.R.attr.buttonBarButtonStyle)
                }

                button?.text = buttonInfo.display
                button?.setTextColor(buttonTextColor)
                button?.isAllCaps = buttonTextAllCaps
                button?.setPadding(0, 0, 0, 0)

                button?.setOnClickListener { view ->
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button)
                    onAnyExtraKeyButtonClick(view, buttonInfo, button)
                }

                button?.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.setBackgroundColor(buttonActiveBackgroundColor)
                            // Start long press scheduled executors which will be stopped in next MotionEvent
                            startScheduledExecutors(view, buttonInfo, button)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (buttonInfo.popup != null) {
                                // Show popup on swipe up
                                if (popupWindow == null && event.y < 0) {
                                    stopScheduledExecutors()
                                    view.setBackgroundColor(buttonBackgroundColor)
                                    showPopup(view, buttonInfo.popup)
                                }
                                if (popupWindow != null && event.y > 0) {
                                    view.setBackgroundColor(buttonActiveBackgroundColor)
                                    dismissPopup()
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            // If ACTION_UP up was not from a repetitive key or was with a key with a popup button
                            if (longPressCount == 0 || popupWindow != null) {
                                // Trigger popup button click if swipe up complete
                                if (popupWindow != null) {
                                    dismissPopup()
                                    if (buttonInfo.popup != null) {
                                        onAnyExtraKeyButtonClick(view, buttonInfo.popup, button)
                                    }
                                } else {
                                    view.performClick()
                                }
                            }
                            true
                        }
                        else -> true
                    }
                }

                val param = LayoutParams()
                param.width = 0
                param.height = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                    (heightPx + 0.5).toInt()
                } else {
                    0
                }
                param.setMargins(0, 0, 0, 0)
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.0f)
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1.0f)
                button.layoutParams = param

                addView(button)
            }
        }
    }

    fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        extraKeysViewClient?.onExtraKeyButtonClick(view, buttonInfo, button)
    }

    fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        extraKeysViewClient?.let { client ->
            // If client handled the feedback, then just return
            if (client.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return
        }

        if (Settings.System.getInt(context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {

            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                // Perform haptic feedback only if no total silence mode enabled.
                if (Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    fun onAnyExtraKeyButtonClick(view: View, @NonNull buttonInfo: ExtraKeyButton, button: MaterialButton) {
        if (isSpecialButton(buttonInfo)) {
            if (longPressCount > 0) return
            val state = specialButtons[SpecialButton.valueOf(buttonInfo.key)]
            state ?: return

            // Toggle active state and disable lock state if new state is not active
            state.isActive = !state.isActive
            if (!state.isActive)
                state.isLocked = false
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button)
        }
    }

    fun startScheduledExecutors(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        stopScheduledExecutors()
        longPressCount = 0
        if (repetitiveKeys.contains(buttonInfo.key)) {
            // Auto repeat key if long pressed until ACTION_UP stops it by calling stopScheduledExecutors.
            // Currently, only one (last) repeat key can run at a time. Old ones are stopped.
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            scheduledExecutor?.scheduleWithFixedDelay({
                onExtraKeyButtonClick(view, buttonInfo, button)
            }, longPressTimeout.toLong(), longPressRepeatDelay.toLong(), TimeUnit.MILLISECONDS)
        } else if (isSpecialButton(buttonInfo)) {
            // Lock the key if long pressed by running specialButtonsLongHoldRunnable after
            // waiting for longPressTimeout milliseconds. If user does not long press, then the
            // ACTION_UP triggered will cancel the runnable by calling stopScheduledExecutors before
            // it has a chance to run.
            val state = specialButtons[SpecialButton.valueOf(buttonInfo.key)]
            state ?: return
            if (handler == null)
                handler = Handler(Looper.getMainLooper())
            val runnable = SpecialButtonsLongHoldRunnable(state)
            specialButtonsLongHoldRunnable = runnable
            handler?.postDelayed(runnable, longPressTimeout.toLong())
        }
    }

    fun stopScheduledExecutors() {
        scheduledExecutor?.let {
            it.shutdownNow()
            scheduledExecutor = null
        }

        specialButtonsLongHoldRunnable?.let { longHoldRunnable ->
            handler?.removeCallbacks(longHoldRunnable)
            specialButtonsLongHoldRunnable = null
        }
    }

    inner class SpecialButtonsLongHoldRunnable(private val state: SpecialButtonState) : Runnable {
        override fun run() {
            // Toggle active and lock state
            state.isLocked = !state.isActive
            state.isActive = !state.isActive
            longPressCount++
        }
    }

    private fun showPopup(view: View, extraButton: ExtraKeyButton) {
        val width = view.measuredWidth
        val height = view.measuredHeight
        val button: MaterialButton
        if (isSpecialButton(extraButton)) {
            val specialButton = createSpecialButton(extraButton.key, false)
            specialButton ?: return
            button = specialButton
        } else {
            button = MaterialButton(context, null, android.R.attr.buttonBarButtonStyle)
            button.setTextColor(buttonTextColor)
        }
        button.text = extraButton.display
        button.isAllCaps = buttonTextAllCaps
        button.setPadding(0, 0, 0, 0)
        button.minHeight = 0
        button.minWidth = 0
        button.setMinimumWidth(0)
        button.setMinimumHeight(0)
        button.width = width
        button.height = height
        button.setBackgroundColor(buttonActiveBackgroundColor)
        popupWindow = PopupWindow(this)
        popupWindow?.width = LayoutParams.WRAP_CONTENT
        popupWindow?.height = LayoutParams.WRAP_CONTENT
        popupWindow?.setContentView(button)
        popupWindow?.isOutsideTouchable = true
        popupWindow?.isFocusable = false
        popupWindow?.showAsDropDown(view, 0, -2 * height)
    }

    fun dismissPopup() {
        popupWindow?.setContentView(null)
        popupWindow?.dismiss()
        popupWindow = null
    }

    /** Check whether a [ExtraKeyButton] is a [SpecialButton]. */
    fun isSpecialButton(button: ExtraKeyButton): Boolean {
        val keyString: String = button.key?.toString() ?: ""
        return specialButtonsKeys.any { it.key == keyString }
    }

    /**
     * Read whether [SpecialButton] registered in [specialButtons] is active or not.
     *
     * @param specialButton The [SpecialButton] to read.
     * @param autoSetInActive Set to `true` if [SpecialButtonState.isActive] should be
     *                        set `false` if button is not locked.
     * @return Returns `null` if button does not exist in [specialButtons]. If button
     *         exists, then returns `true` if the button is created in [ExtraKeysView]
     *         and is active, otherwise `false`.
     */
    @Nullable
    fun readSpecialButton(specialButton: SpecialButton, autoSetInActive: Boolean): Boolean? {
        val state = specialButtons[specialButton]
        state ?: return null

        if (!state.isCreated || !state.isActive)
            return false

        // Disable active state only if not locked
        if (autoSetInActive && !state.isLocked)
            state.isActive = false

        return true
    }

    @Nullable
    fun createSpecialButton(buttonKey: String, needUpdate: Boolean): MaterialButton? {
        val state = specialButtons[SpecialButton.valueOf(buttonKey)]
        state ?: return null
        state.isCreated = true
        val button = MaterialButton(context, null, android.R.attr.buttonBarButtonStyle)
        button.setTextColor(if (state.isActive) buttonActiveTextColor else buttonTextColor)
        if (needUpdate) {
            state.buttons.add(button)
        }
        return button
    }
}
