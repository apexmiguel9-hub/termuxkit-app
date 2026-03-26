package com.termux.shared.termux.extrakeys
import java.util.*

import com.google.android.material.button.MaterialButton

/** The [Class] that maintains a state of a [SpecialButton] */
class SpecialButtonState(
    val extraKeysView: ExtraKeysView
) {

    /** If special button has been created for the [ExtraKeysView]. */
    var isCreated = false

    /** If special button is active. */
    var isActive = false
        set(value) {
            field = value
            buttons.forEach { button ->
                button.setTextColor(
                    if (value) button.currentTextColor
                    else button.textColors.defaultColor
                )
            }
        }

    /** If special button is locked due to long hold on it and should not be deactivated if its
     * state is read. */
    var isLocked = false

    val buttons = ArrayList<MaterialButton>()
}
